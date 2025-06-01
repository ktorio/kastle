package org.jetbrains.kastle

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.yamlMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.jetbrains.kastle.amper.readDependencies
import org.jetbrains.kastle.amper.readHeader
import org.jetbrains.kastle.io.*
import org.jetbrains.kastle.templates.*
import org.jetbrains.kastle.utils.protocol
import org.jetbrains.kastle.utils.slotId
import org.jetbrains.kastle.utils.takeIfSlot
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile

private const val MANIFEST_YAML = "manifest.yaml"
private const val GROUP_YAML = "group.yaml"

class LocalPackRepository(
    private val root: Path,
    private val fs: FileSystem = SystemFileSystem,
    remoteRepository: PackRepository = PackRepository.EMPTY,
): PackRepository {
    private val textFileTemplateEngine = HandlebarsTemplateEngine()

    constructor(root: String): this(Path(root))

    private val repository: PackRepository = object : PackRepository {
        private val cache = mutableMapOf<PackId, PackDescriptor>()

        override fun ids(): Flow<PackId> =
            remoteRepository.ids()

        override suspend fun get(packId: PackId): PackDescriptor? =
            cache[packId] ?: fromLocalOrRemote(packId)?.also {
                cache[packId] = it
            }

        override suspend fun slot(slotId: SlotId): SlotDescriptor? =
            this.get(slotId.pack)?.allSources
                ?.firstNotNullOfOrNull { source ->
                    source.blocks
                        ?.filterIsInstance<Slot>()
                        ?.find { slot -> slot.name == slotId.name }
                        ?.let { SlotDescriptor(it, source.target) }
                }

        private suspend fun fromLocalOrRemote(id: PackId): PackDescriptor? =
            this@LocalPackRepository.get(id) ?: remoteRepository.get(id)

        override suspend fun versions(): VersionsCatalog =
            remoteRepository.versions() + this@LocalPackRepository.versions()
    }

    override fun ids(): Flow<PackId> =
        fs.list(root).flatMap { groupPath ->
            if (!groupPath.isDir())
                return@flatMap emptyList()
            fs.list(groupPath)
        }.asFlow().mapNotNull { path ->
            if (path.isDir())
                PackId.parse("${path.parent!!.name}/${path.name}")
            else null
        }

    private fun Path.isDir(): Boolean =
        fs.metadataOrNull(this)?.isDirectory == true

    override suspend fun get(packId: PackId): PackDescriptor? {
        val projectPath = root.resolve(packId.toString())
        val manifest: PackManifest = projectPath.resolve(MANIFEST_YAML).readYaml() ?: return null
        val group = manifest.group ?: projectPath.resolve("../$GROUP_YAML").readYaml()
        val properties = manifest.properties.toMutableList()
        val documentation = projectPath.resolve("README.md").readText()

        val projectSources = projectPath.moduleFolders().asFlow().map { modulePath ->
            val relativeModulePath = modulePath.relativeTo(projectPath).toString()
            val manifestYaml = modulePath.resolve("module-manifest.yaml").readYaml<ModuleManifest>()
            val amperYaml = modulePath.resolve("module.yaml").readYamlNode()?.yamlMap
            val amperSettings = amperYaml?.get<YamlMap>("settings")?.let { node ->
                Yaml.default.decodeFromYamlNode<AmperSettings>(node)
            }

            // TODO cache versions
            val catalog = versions()
            val (moduleType, platforms) = amperYaml.readHeader()

            // TODO verify this is correct
            fun readDependencies(dependencies: String): DependenciesMap = platforms.singleOrNull()?.let {
                mapOf(it to amperYaml.readDependencies(dependencies, catalog))
            } ?: (platforms.associateWith { platform ->
                amperYaml.readDependencies("$dependencies@$platform", catalog)
            } + (Platform.COMMON to amperYaml.readDependencies(dependencies, catalog)))

            val dependencies = readDependencies("dependencies")
            val testDependencies = readDependencies("testDependencies")

            val sources = mutableListOf<SourceTemplate>()
            val resources = mutableListOf<SourceTemplate>()
            val sourceFolders = when {
                platforms.size == 1 -> listOf(modulePath.resolve("src"))
                else -> buildList { add("src"); platforms.forEach { add("src@$it")} }.map(modulePath::resolve)
            }

            for (sourceFolder in sourceFolders) {
                if (!fs.exists(sourceFolder))
                    continue

                // properties are supplied both from the manifest and from declarations in the source files
                val kotlinAnalyzer = KotlinCompilerTemplateEngine(sourceFolder, repository)
                sources += kotlinAnalyzer.ktFiles.map { sourceFile ->
                    kotlinAnalyzer.read(sourceFolder.relativeTo(modulePath), sourceFile, properties)
                        .copy(packId = packId)
                }

                // include non-kotlin files as hbs templates
                sources += fs.list(sourceFolder).filter {
                    !it.name.endsWith(".kt")
                }.map { file ->
                    textFileTemplateEngine.read(modulePath, file).copy(packId = packId)
                }

                val resourcesFolder = modulePath.resolve("resources")
                if (fs.exists(resourcesFolder)) {
                    resources += fs.list(resourcesFolder).map { file ->
                        textFileTemplateEngine.read(modulePath, file).copy(packId = packId)
                    }
                }
            }

            // additional sources defined for module; use only the text file templating engine
            for (manifestSource in manifestYaml?.sources.orEmpty()) {
                sources += when(val text = manifestSource.text) {
                    null -> textFileTemplateEngine.read(modulePath, modulePath.resolve(manifestSource.path!!))
                    else -> textFileTemplateEngine.read(manifestSource.target!!, text)
                }.copy(packId = packId)
            }

            SourceModule(
                type = moduleType,
                path = relativeModulePath,
                platforms = platforms,
                dependencies = dependencies,
                testDependencies = testDependencies,
                sources = sources + resources,
                amper = amperSettings ?: AmperSettings(),
                // TODO gradle settings
            )
        }.toList().let(ProjectModules::fromList)

        val kotlinAnalyzer = KotlinCompilerTemplateEngine(projectPath, repository)
        val expressionParser = KotlinExpressionParser(kotlinAnalyzer.psiFileFactory)
        val readSource: suspend (SourceDefinition) -> SourceTemplate = { (path, text, target, condition) ->
            require(target != null) { "Missing target for project-level source: ${path ?: text}" }
            val file = projectPath.resolve(path ?: "source.kt")
            if (!fs.exists(file))
                throw IllegalArgumentException("Missing source file: $file")

            val format = path?.extensionFormat
                ?: target.takeIfSlot()?.getExtensionFromSlot()
                ?: target.extensionFormat
            val templateContents: String = when {
                path != null -> file.readText() ?: ""
                text != null -> text
                else -> throw IllegalArgumentException("Missing path or text in source definition")
            }
            val conditionExpression = condition?.let(expressionParser::parse)

            when(format) {
                TemplateFormat.KOTLIN -> {
                    val psiFile = kotlinAnalyzer.psiFileFactory.createFileFromText(
                        path ?: "source.kt",
                        KotlinFileType.INSTANCE,
                        templateContents
                    )
                    kotlinAnalyzer.read(Path(""), psiFile as KtFile, properties)
                        .copy(
                            packId = packId,
                            target = target,
                            condition = conditionExpression
                        )
                }
                TemplateFormat.OTHER ->
                    textFileTemplateEngine.read(target, templateContents)
                        .copy(
                            packId = packId,
                            condition = conditionExpression
                        )
            }
        }

        return PackDescriptor(
            info = manifest.copy(
                group = group,
                properties = properties.distinctBy { it.key },
                documentation = documentation,
            ),
            sources = PackSources(
                common = manifest.commonSources.map { readSource(it) },
                root = manifest.rootSources.map { readSource(it) },
                modules = manifest.modules?.let { modules ->
                    ProjectModules.fromList(modules) + projectSources
                } ?: projectSources,
            )
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun versions(): VersionsCatalog {
        val builtInArtifacts =
            fs.list(root).filter {
                it.name.endsWith(".versions.toml")
            }.mapNotNull { file ->
                file.readToml<BuiltInToml>(fs)?.libraries
            }.reduce { left, right -> left + right }

        val builtInCatalog = VersionsCatalog(
            libraries = builtInArtifacts.mapValues { (_, artifact) ->
                val (group, artifact, version) = artifact
                CatalogArtifact(
                    "$group:$artifact",
                    CatalogVersion.Number(version),
                    builtIn = true
                )
            }
        )

        val gradleCatalog = root.resolve("../gradle/libs.versions.toml")
            .readToml<VersionsCatalog>(fs) ?: return builtInCatalog

        return builtInCatalog + gradleCatalog
    }

    private suspend fun Url.getExtensionFromSlot(): TemplateFormat {
        if (protocol != "slot") return TemplateFormat.OTHER
        val parentUrl = repository.slot(slotId)?.parent
            ?: throw IllegalArgumentException("Slot missing: $this")
        return when(parentUrl.protocol) {
            "file" -> parentUrl.extensionFormat
            "slot" -> parentUrl.getExtensionFromSlot()
            else -> error("Unknown source target protocol: $parentUrl")
        }
    }

    /**
     * Recursively find all modules, based on Amper conventions.
     */
    private fun Path.moduleFolders(): Sequence<Path> =
        if (fs.metadataOrNull(this)?.isDirectory != true)
            emptySequence()
        else if (isModuleFolder())
            sequenceOf(this)
        else fs.list(this).asSequence()
            .flatMap { it.moduleFolders() }

    private fun Path.isModuleFolder(): Boolean =
        fs.exists(resolve("src")) ||
            fs.exists(resolve("module.yaml")) ||
            fs.exists(resolve("module-manifest.yaml"))


    @Serializable
    data class BuiltInToml(
        val libraries: Map<String, ArtifactDependency>,
    )
}

