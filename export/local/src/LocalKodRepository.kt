package org.jetbrains.kastle

import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.descendantsOfType
import kotlinx.coroutines.flow.*
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.kastle.io.*
import org.jetbrains.kastle.utils.*
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.io.File
import kotlin.collections.*
import kotlin.io.path.exists
import kotlin.io.path.relativeTo
import kotlin.text.substring
import kotlin.text.trim
import kotlin.text.trimIndent

private const val MANIFEST_YAML = "manifest.yaml"
private const val GROUP_YAML = "group.yaml"

class LocalKodRepository(
    private val root: Path,
    private val fs: FileSystem = SystemFileSystem,
    remoteRepository: KodRepository = KodRepository.EMPTY,
): KodRepository {
    constructor(root: String): this(Path(root))

    private val repository: KodRepository = object : KodRepository {
        private val cache = mutableMapOf<KodId, KodDescriptor>()

        override fun kodIds(): Flow<KodId> =
            remoteRepository.kodIds()

        override suspend fun get(id: KodId): KodDescriptor? =
            cache[id] ?: fromLocalOrRemote(id)?.also {
                cache[id] = it
            }

        override suspend fun slot(slotId: SlotId): Slot? =
            this.get(slotId.kod)?.sources?.flatMap { it.blocks.orEmpty() }?.filterIsInstance<Slot>()?.find { slot ->
                slot.name == slotId.name
            }

        private suspend fun fromLocalOrRemote(id: KodId): KodDescriptor? =
            this@LocalKodRepository.get(id) ?: remoteRepository.get(id)

    }

    override fun kodIds(): Flow<KodId> =
        fs.list(root).flatMap { groupPath ->
            fs.list(groupPath)
        }.asFlow().mapNotNull { path ->
            if (fs.metadataOrNull(path)?.isDirectory == true)
                KodId.parse("${path.parent!!.name}/${path.name}")
            else null
        }

    override suspend fun get(kodId: KodId): KodDescriptor? {
        val moduleRoot = root.resolve(kodId.toString())
        val manifest: KodManifest = moduleRoot.resolve(MANIFEST_YAML).readYaml()
            ?: throw MissingManifestFileException("Cannot find $MANIFEST_YAML in $moduleRoot")
        val group = manifest.group ?: moduleRoot.resolve("../$GROUP_YAML").readYaml()
        val properties = manifest.properties.toMutableList()
        val modules = moduleRoot.moduleFolders().asFlow().map { path ->
            // TODO discover + iterate through all modules
            val sourceFolder = path.resolve("src")
            if (!fs.exists(sourceFolder))
                return@map SourceModule(sources = emptyList())

            // Properties are supplied both from the manifest and from declarations in the source files
            val analyzer = KotlinCompilerSourceAnalyzer(sourceFolder, repository)
            val sources = analyzer.ktFiles.asFlow()
                .map { sourceFile ->
                    analyzer.read(sourceFile, properties)
                }.toList()

            SourceModule(
                path = path.name,
                sources = sources,
            )
        }.toList()

        return KodDescriptor(
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

/**
 * Provides analysis capabilities for Kotlin source files within a specified path.
 * This class utilizes Kotlin compiler's source analysis with a predefined environment setup.
 *
 * @constructor Initializes the source analyzer with the specified path and repository.
 * The environment is configured for JVM production with basic compiler settings.
 *
 * @property path The path to the Kotlin source files to be analyzed.
 * @property repository An optional kod repository for additional data or functionality.
 */
private class KotlinCompilerSourceAnalyzer(
    private val path: Path,
    private val repository: KodRepository = KodRepository.EMPTY,
) {
    companion object {
        private val targetRegex = Regex("""@target\s+(\S+)""", RegexOption.IGNORE_CASE)
    }

    private var environment: KotlinCoreEnvironment
    private var psiFileFactory: PsiFileFactory
    // TODO verify compilation, etc.
    private val analyzer = TopDownAnalyzerFacadeForJVM

    init {
        val verbose = false
        val stderrMessages = PrintingMessageCollector(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, verbose)
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, stderrMessages)
            put(CommonConfigurationKeys.MODULE_NAME, path.parent!!.name)
            put(CommonConfigurationKeys.ALLOW_ANY_SCRIPTS_IN_SOURCE_ROOTS, true)
            put(JVMConfigurationKeys.JDK_HOME, File(System.getenv("JAVA_HOME")))

            addKotlinSourceRoot(path.toString())
        }
        environment = KotlinCoreEnvironment.createForProduction(
            Disposer.newDisposable(),
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
        psiFileFactory = PsiFileFactory.getInstance(environment.project)
    }

    val ktFiles: List<KtFile> by lazy {
        environment.getSourceFiles()
    }

    /**
     * Parses a source template reference and retrieves the corresponding source template.
     *
     * @param reference The reference to the source template file which includes path and target details.
     * @returns A `SourceTemplate` object that encapsulates the read source text and its metadata.
     *
     * @throws IllegalArgumentException if the source file specified in the reference cannot be found.
     */
    suspend fun read(
        ktFile: KtFile,
        properties: MutableList<Property>
    ): SourceTemplate {
        // TODO drop header
        // TODO full file path
        val targetFromHeader = ktFile
            .descendantsOfType<PsiComment>()
            .firstOrNull()
            ?.let {
                targetRegex.find(it.text)?.groupValues?.getOrNull(1)
            }
        val target = targetFromHeader ?: "file:${ktFile.virtualFile.name}"

        return when (target.protocol) {
            "file" -> SourceTemplate(
                text = ktFile.text,
                target = target,
                blocks = ktFile.findBlocks(properties),
            )
            "slot" -> {
                val slot = repository.slot(target.slotId)
                    ?: throw IllegalArgumentException("Slot not found: ${target.afterProtocol}")
                val imports = ktFile.importList?.imports?.map { it.text.substring("import ".length) } ?: emptyList()
                val text = when(slot.position) {
                    is SourcePosition.TopLevel -> ktFile.endOfImports()?.let { endOfImports ->
                        ktFile.text.substring(endOfImports)
                    } ?: ktFile.text
                    // TODO other positions, annotations, etc.
                    is SourcePosition.Inline -> ktFile.firstFunctionBody()
                        ?: throw IllegalArgumentException("Expected single function body for targeting slot")
                }
                SourceTemplate(
                    text = text,
                    target = target,
                    imports = imports,
                    blocks = ktFile.findBlocks(properties),
                )
            }
            else -> throw IllegalArgumentException("Unsupported target protocol: ${target.protocol}")
        }
    }

    private fun KtFile.firstFunctionBody() =
        declarations.filterIsInstance<KtNamedFunction>()
            .singleOrNull()
            ?.bodyExpression?.text?.trimBraces()?.trimIndent()?.trim()

    private fun KtFile.findBlocks(properties: MutableList<Property>): List<Block> {
        val templateReferences = findReferencesTo("Template").map(TemplateReference::classify).toList()
        val propertyDeclarations = templateReferences.filterIsInstance<TemplateReference.PropertyDelegate>()
            .map { it.declaration }
        val declarationBlocks = propertyDeclarations.map { declaration ->
            SkipBlock(position = declaration.sourcePosition(includeTrailingNewline = true))
        }
        val propertyBlocks = propertyDeclarations.asSequence().flatMap { declaration ->
            declaration.findReferences().flatMap { reference ->
                reference.readPropertyBlocks(declaration.name!!)
            }
        }
        val slots = templateReferences.filterIsInstance<TemplateReference.SlotExpression>()
            .map { it.expression.readSlotBlock() }
        // TODO merge properties to kod manifest
        properties.addAll(propertyDeclarations.map { it.asProperty() })

        return declarationBlocks + propertyBlocks + slots
    }

}