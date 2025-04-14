package org.jetbrains.kastle

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar
import kotlinx.coroutines.flow.*
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import org.jetbrains.kastle.io.*
import org.jetbrains.kastle.io.resolve
import org.jetbrains.kastle.templates.HandlebarsTemplateEngine
import org.jetbrains.kastle.templates.KotlinCompilerTemplateEngine
import org.jetbrains.kastle.templates.KotlinExpressionParser
import org.jetbrains.kastle.templates.TemplateFormat
import org.jetbrains.kastle.templates.extensionFormat
import org.jetbrains.kastle.utils.protocol
import org.jetbrains.kastle.utils.slotId
import org.jetbrains.kastle.utils.takeIfSlot
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.tomlj.Toml
import kotlin.collections.*

private const val MANIFEST_YAML = "manifest.yaml"
private const val GROUP_YAML = "group.yaml"

class LocalPackRepository(
    private val root: Path,
    private val fs: FileSystem = SystemFileSystem,
    remoteRepository: PackRepository = PackRepository.EMPTY,
): PackRepository {
    private val textFileTemplateEngine = HandlebarsTemplateEngine()

    constructor(root: String): this(Path(root))

    private val versionsLookup: Map<String, Dependency> by lazy { readVersionCatalogs() }

    private val repository: PackRepository = object : PackRepository {
        private val cache = mutableMapOf<PackId, PackDescriptor>()

        override fun packIds(): Flow<PackId> =
            remoteRepository.packIds()

        override suspend fun get(id: PackId): PackDescriptor? =
            cache[id] ?: fromLocalOrRemote(id)?.also {
                cache[id] = it
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

    }

    override fun packIds(): Flow<PackId> =
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

            // TODO not entirely correct
            val (moduleType, platforms) = when(val productNode = amperYaml?.get<YamlNode>("product")) {
                is YamlScalar -> SourceModuleType.parse(productNode.content) to listOf("jvm")
                is YamlMap -> {
                    val productType = SourceModuleType.parse(productNode.get<YamlScalar>("type")?.content ?: "lib")
                    val platforms = productNode.get<YamlList>("platforms")?.items?.map { it.yamlScalar.content }.orEmpty()
                    productType to platforms
                }
                else -> SourceModuleType.LIB to listOf("jvm")
            }

            fun YamlMap?.readDependencies(key: String) =
                this?.get<YamlList>(key)
                    ?.items?.asSequence()?.map { it.yamlScalar.content }.orEmpty()
                    .filter { !it.startsWith("..") }
                    .map(Dependency::parse)
                    .map { dependency ->
                        when(dependency) {
                            is ArtifactDependency, is ModuleDependency -> dependency
                            is CatalogReference -> versionsLookup[dependency.key]
                                ?: throw IllegalArgumentException("Missing version for dependency: ${dependency.key}")
                        }
                    }
                    .toList()

            val dependencies = amperYaml.readDependencies("dependencies")
            val testDependencies = amperYaml.readDependencies("testDependencies")

            val sources = mutableListOf<SourceTemplate>()
            val resources = mutableListOf<SourceTemplate>()
            val sourceFolders = when {
                platforms.size == 1 -> listOf(modulePath.resolve("src"))
                else -> buildList { add("src"); platforms.forEach { add("src@$it")} }.map(modulePath::resolve)
            }

            for (sourceFolder in sourceFolders) {
                if (!fs.exists(sourceFolder)) {
                    return@map SourceModule(
                        type = moduleType,
                        path = relativeModulePath,
                        platforms = platforms,
                        dependencies = dependencies,
                        testDependencies = testDependencies,
                        sources = emptyList(),
                    )
                }

                // Properties are supplied both from the manifest and from declarations in the source files
                val kotlinAnalyzer = KotlinCompilerTemplateEngine(sourceFolder, repository)
                sources += kotlinAnalyzer.ktFiles.map { sourceFile ->
                    kotlinAnalyzer.read(sourceFolder.relativeTo(modulePath), sourceFile, properties)
                        .copy(packId = packId)
                }
                val resourcesFolder = modulePath.resolve("resources")
                if (fs.exists(resourcesFolder)) {
                    resources += fs.list(resourcesFolder).map { file ->
                        textFileTemplateEngine.read(file)
                            .copy(packId = packId)
                    }
                }
            }

            // additional sources defined for module use only the text file templating engine
            for (manifestSource in manifestYaml?.sources.orEmpty()) {
                sources += when(val text = manifestSource.text) {
                    null -> textFileTemplateEngine.read(modulePath.resolve(manifestSource.path!!))
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
            )
        }.toList().let(ProjectModules::fromList)

        val kotlinAnalyzer = KotlinCompilerTemplateEngine(projectPath, repository)
        val expressionParser = KotlinExpressionParser(kotlinAnalyzer.psiFileFactory)
        val readSource: suspend (SourceDefinition) -> SourceTemplate = { (path, text, target, condition) ->
            require(target != null) { "Missing target for project-level source: ${path ?: text}" }
            val file = projectPath.resolve(path ?: "source.kt")
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

        // project-level sources are included in all modules,
        // except for slot targets, these are inserted into the first module
        val commonSources = manifest.commonSources.map { readSource(it) }

        // root sources are included at the repository-level root, like .gitignore
        val rootSources = manifest.rootSources.map { readSource(it) }

        return PackDescriptor(
            manifest.copy(
                group = group,
                properties = properties.distinctBy { it.key },
                documentation = documentation,
            ),
            commonSources = commonSources,
            rootSources = rootSources,
            projectSources = projectSources,
        )
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

    private fun readVersionCatalogs(): Map<String, Dependency> = fs.list(root).filter {
        it.name.endsWith(".versions.toml")
    }.mapNotNull { file ->
        try {
            val contents = fs.source(file).buffered().readString()
            val catalog = Toml.parse(contents)
            val libraries = catalog.getTable("libraries") ?: return@mapNotNull null
            val dependencies = libraries.dottedKeySet().map { key ->
                key to Dependency.parse(libraries[key] as String)
            }
            dependencies
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }.flatten().toMap()
}

