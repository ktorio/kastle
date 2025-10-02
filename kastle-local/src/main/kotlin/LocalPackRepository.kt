package org.jetbrains.kastle

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.yamlMap
import kotlinx.coroutines.flow.*
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.kastle.StaticSource.Companion.sourceFile
import org.jetbrains.kastle.amper.readDependencies
import org.jetbrains.kastle.amper.readPlatforms
import org.jetbrains.kastle.io.*
import org.jetbrains.kastle.io.resolve
import org.jetbrains.kastle.templates.*
import org.jetbrains.kastle.utils.extension
import org.jetbrains.kastle.utils.protocol
import org.jetbrains.kastle.utils.slotId
import org.jetbrains.kastle.utils.takeIfSlot
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile

private const val PACK_YAML = "pack.ksl.yaml"
private const val GROUP_YAML = "group.ksl.yaml"
private const val MODULE_YAML = "module.ksl.yaml"

class LocalPackRepository(
    private val root: Path,
    private val fs: FileSystem = SystemFileSystem,
    private val versionsCatalogFile: String = "../gradle/libs.versions.toml",
    remoteRepository: PackRepository = PackRepository.EMPTY,
): PackRepository {
    private val handlebarsTemplateEngine = HandlebarsTemplateEngine()
    private val serializersModule = SerializersModule {
        // TODO this doesn't work for some reason
        polymorphic(SourceFile::class) {
            defaultDeserializer { SourceTemplate.serializer() }
            subclass(StaticSource::class)
            subclass(SourceTemplate::class)
        }
    }
    private val yaml = Yaml(serializersModule)

    constructor(root: String): this(Path(root))
    constructor(root: String, catalogFile: String): this(Path(root), versionsCatalogFile = catalogFile)

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
                ?.filterIsInstance<SourceTemplate>()
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
            if (path.isDir() && fs.exists(path.resolve(PACK_YAML)))
                PackId.parse("${path.parent!!.name}/${path.name}")
            else null
        }

    private fun Path.isDir(): Boolean =
        fs.metadataOrNull(this)?.isDirectory == true

    override suspend fun get(packId: PackId): PackDescriptor? {
        val projectPath = root.resolve(packId.toString())
        val manifest: PackManifest = projectPath.resolve(PACK_YAML).readYaml() ?: return null
        val group = manifest.group
            ?: projectPath.resolve("../$GROUP_YAML").readYaml()
            ?: Group(packId.group)
        val properties = manifest.properties.toMutableList()
        val documentation = projectPath.resolve("README.md").readText()

        val kotlinAnalyzer = KotlinCompilerTemplateEngine(projectPath, repository)
        val expressionParser = KotlinExpressionParser(kotlinAnalyzer.psiFileFactory)

        val projectSources = projectPath.moduleFolders().asFlow().mapNotNull { modulePath ->
            val relativeModulePath = modulePath.relativeTo(projectPath).toString()
            val moduleYaml = modulePath.resolve(MODULE_YAML)
                .readYamlNode(fs, yaml)?.yamlMap
                ?: return@mapNotNull null

            val platforms = moduleYaml.readPlatforms()

            val amperSettings = moduleYaml.get<YamlMap>("amper")?.let { node ->
                yaml.decodeFromYamlNode<AmperSettings>(node)
            }
            val gradleSettings = moduleYaml.get<YamlMap>("gradle")?.let { node ->
                yaml.decodeFromYamlNode<GradleSettings>(node)
            }

            // TODO verify this is correct
            fun readDependencies(dependencies: String): DependenciesMap =
                platforms.singleOrNull()?.let {
                    mapOf(it to moduleYaml.readDependencies(dependencies))
                } ?: (platforms.associateWith { platform ->
                    moduleYaml.readDependencies("$dependencies@$platform")
                } + (Platform.COMMON to moduleYaml.readDependencies(dependencies)))

            fun readModuleSource(file: Path, target: String? = null) =
                when (file.name.extension.lowercase()) {
                    "hbs" -> handlebarsTemplateEngine.read(modulePath, file).let { template ->
                        template.copy(
                            target = target ?: template.target,
                            packId = packId,
                        )
                    }
                    else -> fs.sourceFile(file, modulePath).let { source ->
                        source.copy(
                            target = target ?: source.target,
                        )
                    }
                }

            val dependencies = readDependencies("dependencies")
            val testDependencies = readDependencies("testDependencies")

            val sources = mutableListOf<SourceFile>()
            val resources = mutableListOf<SourceFile>()
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

                // include non-kotlin files
                sources += fs.list(sourceFolder).filter {
                    !it.name.endsWith(".kt")
                }.map(::readModuleSource)

                // assume non-kotlin files in resources
                val resourcesFolder = modulePath.resolve("resources")
                if (fs.exists(resourcesFolder)) {
                    resources += fs.list(resourcesFolder)
                        .map(::readModuleSource)
                }
            }

            // additional sources defined for module; also assume no kotlin sources
            // TODO remove duplicates from files in source folders
            for (manifestSource in moduleYaml.get<YamlList>("sources")?.items.orEmpty()) {
                val (path, text, target, condition) = yaml.decodeFromYamlNode<SourceDefinition>(manifestSource)
                val conditionExpression = condition?.let(expressionParser::parse)
                sources += when(text) {
                    null -> readModuleSource(modulePath.resolve(path!!), target = target)
                    else -> handlebarsTemplateEngine.read(target!!, text).copy(packId = packId)
                }.withCondition(conditionExpression)
            }

            SourceModule(
                path = relativeModulePath,
                platforms = platforms,
                dependencies = dependencies,
                testDependencies = testDependencies,
                sources = sources + resources,
                amper = amperSettings ?: AmperSettings(),
                gradle = gradleSettings ?: GradleSettings(),
            )
        }.toList().let(ProjectModules::fromList)

        val readSource: suspend (SourceDefinition) -> SourceFile = { (path, text, target, condition) ->
            require(target != null) { "Missing target for project-level source: ${path ?: text}" }
            val file = projectPath.resolve(path ?: "source.kt")
            if (!fs.exists(file))
                throw IllegalArgumentException("Missing source file: $file")

            val format = path?.extensionFormat
                ?: target.takeIfSlot()?.getExtensionFromSlot()
                ?: target.extensionFormat
            val conditionExpression = condition?.let(expressionParser::parse)

            when(format) {
                TemplateFormat.KOTLIN -> {
                    val psiFile = kotlinAnalyzer.psiFileFactory.createFileFromText(
                        path ?: "source.kt",
                        KotlinFileType.INSTANCE,
                        file.readText() ?: text ?: throw IllegalArgumentException("Missing path or text in source definition"),
                    )
                    kotlinAnalyzer.read(Path(""), psiFile as KtFile, properties)
                        .copy(
                            packId = packId,
                            target = target,
                            condition = conditionExpression
                        )
                }
                TemplateFormat.OTHER ->
                    when (file.name.extension.lowercase()) {
                        "hbs" -> handlebarsTemplateEngine.read(
                            target,
                            file.readText() ?: text ?: throw IllegalArgumentException("Missing path or text in source definition")
                        ).copy(packId = packId, condition = conditionExpression)
                        else -> StaticSource(
                            contents = fs.source(file).buffered().use { it.readByteString() },
                            target = target,
                            condition = conditionExpression,
                        )
                    }
            }
        }

        return PackDescriptor(
            info = manifest.copy(
                id = packId,
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
            }.reduceOrNull { left, right -> left + right } ?: return VersionsCatalog.Empty

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

        val gradleCatalog = root.resolve(versionsCatalogFile)
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
        else if (fs.exists(resolve(MODULE_YAML)))
            sequenceOf(this)
        else fs.list(this).asSequence()
            .flatMap { it.moduleFolders() }


    @Serializable
    data class BuiltInToml(
        val libraries: Map<String, ArtifactDependency>,
    )
}

