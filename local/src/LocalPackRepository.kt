package org.jetbrains.kastle

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar
import kotlinx.coroutines.flow.*
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.kastle.io.*
import org.jetbrains.kastle.templates.HandlebarsTemplateEngine
import org.jetbrains.kastle.templates.KotlinDSLCompilerTemplateEngine
import org.jetbrains.kastle.templates.TemplateFormat
import org.jetbrains.kastle.templates.extensionFormat
import org.jetbrains.kastle.utils.protocol
import org.jetbrains.kastle.utils.slotId
import org.jetbrains.kastle.utils.takeIfSlot
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
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

    private val repository: PackRepository = object : PackRepository {
        private val cache = mutableMapOf<PackId, PackDescriptor>()

        override fun packIds(): Flow<PackId> =
            remoteRepository.packIds()

        override suspend fun get(id: PackId): PackDescriptor? =
            cache[id] ?: fromLocalOrRemote(id)?.also {
                cache[id] = it
            }

        override suspend fun slot(slotId: SlotId): SlotDescriptor? =
            this.get(slotId.pack)?.sources
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
            fs.list(groupPath)
        }.asFlow().mapNotNull { path ->
            if (fs.metadataOrNull(path)?.isDirectory == true)
                PackId.parse("${path.parent!!.name}/${path.name}")
            else null
        }

    override suspend fun get(packId: PackId): PackDescriptor? {
        val projectPath = root.resolve(packId.toString())
        val manifest: PackManifest = projectPath.resolve(MANIFEST_YAML).readYaml() ?: return null
        val group = manifest.group ?: projectPath.resolve("../$GROUP_YAML").readYaml()
        val properties = manifest.properties.toMutableList()
        val documentation = projectPath.resolve("README.md").readText()

        val projectSources = projectPath.moduleFolders().asFlow().map { modulePath ->
            val relativeModulePath = modulePath.relativeTo(projectPath).toString()
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
                    ?.items?.map { it.yamlScalar.content }
                    ?.filter { !it.startsWith("..") }
                    ?.map(Dependency::parse).orEmpty()
            val dependencies = amperYaml.readDependencies("dependencies")
            val testDependencies = amperYaml.readDependencies("testDependencies")

            val sources = mutableListOf<SourceTemplate>()
            val resources = mutableListOf<SourceTemplate>()
            val sourceFolders = when {
                platforms.size == 1 -> listOf(modulePath.resolve("src"))
                else -> buildList { add("src"); platforms.forEach { add("src@$it")} }.map(modulePath::resolve)
            }

            for (sourceFolder in sourceFolders) {
                if (!fs.exists(sourceFolder))
                    return@map SourceModule(sources = emptyList())

                // Properties are supplied both from the manifest and from declarations in the source files
                val kotlinAnalyzer = KotlinDSLCompilerTemplateEngine(sourceFolder, repository)
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

            SourceModule(
                type = moduleType,
                path = relativeModulePath,
                platforms = platforms,
                dependencies = dependencies,
                testDependencies = testDependencies,
                sources = sources + resources,
            )
        }.toList().let(ProjectModules::fromList)

        // project-level sources are included in all modules,
        // except for slot targets, these are inserted into the first module
        val kotlinAnalyzer = KotlinDSLCompilerTemplateEngine(projectPath, repository)
        val commonSources = manifest.sources.map { (path, text, target, condition) ->
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
                            condition = condition
                        )
                }
                TemplateFormat.OTHER ->
                    textFileTemplateEngine.read(target, templateContents)
                        .copy(
                            packId = packId,
                            condition = condition
                        )
            }
        }

        return PackDescriptor(
            manifest.copy(
                group = group,
                properties = properties.distinctBy { it.key },
                documentation = documentation,
            ),
            commonSources = commonSources,
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

    private fun Path.moduleFolders(): Sequence<Path> =
        if (fs.exists(resolve("src")))
            sequenceOf(this)
        else fs.list(this).asSequence().filter { file ->
            fs.metadataOrNull(file)?.isDirectory == true &&
                fs.exists(file.resolve("src"))
        }
}

