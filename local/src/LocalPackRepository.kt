package org.jetbrains.kastle

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar
import kotlinx.coroutines.flow.*
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.kastle.io.*
import org.jetbrains.kastle.templates.DoubleBraceTemplateEngine
import org.jetbrains.kastle.templates.KotlinDSLCompilerTemplateEngine
import kotlin.collections.*

private const val MANIFEST_YAML = "manifest.yaml"
private const val GROUP_YAML = "group.yaml"

class LocalPackRepository(
    private val root: Path,
    private val fs: FileSystem = SystemFileSystem,
    remoteRepository: PackRepository = PackRepository.EMPTY,
): PackRepository {
    private val nonKtsTemplateEngine = DoubleBraceTemplateEngine()

    constructor(root: String): this(Path(root))

    private val repository: PackRepository = object : PackRepository {
        private val cache = mutableMapOf<PackId, PackDescriptor>()

        override fun packIds(): Flow<PackId> =
            remoteRepository.packIds()

        override suspend fun get(id: PackId): PackDescriptor? =
            cache[id] ?: fromLocalOrRemote(id)?.also {
                cache[id] = it
            }

        override suspend fun slot(slotId: SlotId): Slot? =
            this.get(slotId.pack)?.sources?.flatMap { it.blocks.orEmpty() }?.filterIsInstance<Slot>()?.find { slot ->
                slot.name == slotId.name
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
        val packRoot = root.resolve(packId.toString())
        val manifest: PackManifest = packRoot.resolve(MANIFEST_YAML).readYaml()
            ?: throw MissingManifestFileException("Cannot find $MANIFEST_YAML in $packRoot")
        val group = manifest.group ?: packRoot.resolve("../$GROUP_YAML").readYaml()
        val properties = manifest.properties.toMutableList()
        val modules = packRoot.moduleFolders().asFlow().map { path ->
            val relativeModulePath = path.relativeTo(packRoot).toString()
            val amperYaml = path.resolve("amper.yaml").readYamlNode()?.yamlMap
            val productType = amperYaml?.get<YamlMap>("product")?.get<YamlScalar>("type")?.content ?: "lib"
            val platforms = amperYaml?.get<YamlMap>("product")?.get<YamlList>("platforms")?.items?.mapNotNull {
                it.yamlScalar.content
            } ?: listOf("jvm")
            val dependencies = amperYaml?.get<YamlList>("dependencies")?.items.orEmpty().map {
                it.yamlScalar.content
            }.filter {
                !it.startsWith("..") // TODO filter better
            }.map(Dependency::parse)

            // TODO discover + iterate through all modules
            val sourceFolder = path.resolve("src")
            if (!fs.exists(sourceFolder))
                return@map SourceModule(sources = emptyList())

            // Properties are supplied both from the manifest and from declarations in the source files
            val kotlinAnalyzer = KotlinDSLCompilerTemplateEngine(sourceFolder, repository)
            val sources = kotlinAnalyzer.ktFiles.map { sourceFile ->
                kotlinAnalyzer.read(sourceFile, properties)
            }
            val resourcesFolder = path.resolve("resources")
            val resources = if (fs.exists(resourcesFolder)) {
                fs.list(resourcesFolder).map { file ->
                    nonKtsTemplateEngine.read(file)
                }
            } else emptyList()

            SourceModule(
                type = productType,
                path = relativeModulePath,
                platforms = platforms,
                dependencies = dependencies,
                sources = sources + resources,
            )
        }.toList()

        return PackDescriptor(
            manifest.copy(
                group = group,
                properties = properties.distinctBy { it.key },
            ),
            structure = ProjectStructure.fromList(modules)
        )
    }

    private fun Path.moduleFolders(): Sequence<Path> =
        if (fs.exists(resolve("src")))
            sequenceOf(this)
        else fs.list(this).asSequence().filter { file ->
            fs.metadataOrNull(file)?.isDirectory == true &&
                fs.exists(file.resolve("src"))
        }
}

