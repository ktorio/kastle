package org.jetbrains.kastle.templates

import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.descendantsOfType
import kotlinx.io.files.Path
import org.jetbrains.kastle.Block
import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.Property
import org.jetbrains.kastle.SkipBlock
import org.jetbrains.kastle.SourcePosition
import org.jetbrains.kastle.SourceTemplate
import org.jetbrains.kastle.io.resolve
import org.jetbrains.kastle.utils.afterProtocol
import org.jetbrains.kastle.utils.protocol
import org.jetbrains.kastle.utils.slotId
import org.jetbrains.kastle.utils.trimBraces
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.io.File

/**
 * Provides analysis capabilities for Kotlin source files within a specified path.
 * This class utilizes Kotlin compiler's source analysis with a predefined environment setup.
 *
 * @constructor Initializes the source analyzer with the specified path and repository.
 * The environment is configured for JVM production with basic compiler settings.
 *
 * @property path The path to the Kotlin source files to be analyzed.
 * @property repository An optional pack repository for additional data or functionality.
 */
internal class KotlinDSLCompilerTemplateEngine(
    private val path: Path,
    private val repository: PackRepository = PackRepository.Companion.EMPTY,
    private val log: (() -> String) -> Unit = { println(it()) }

) {
    companion object {
        private val targetRegex = Regex("""@target\s+(\S+)""", RegexOption.IGNORE_CASE)
    }

    val environment: KotlinCoreEnvironment
    val psiFileFactory: PsiFileFactory
    // TODO verify compilation, etc.
    //private val analyzer = TopDownAnalyzerFacadeForJVM

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
        environment = KotlinCoreEnvironment.Companion.createForProduction(
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
        sourcePath: Path,
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
        val target = targetFromHeader ?: "file:${sourcePath.resolve(ktFile.name)}"
        log { "Target: $target" }

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
                // TODO
                val text = ktFile.firstFunctionBody()
                        ?: throw IllegalArgumentException("Expected single function body for targeting slot")
                when(slot.position) {
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
        // references to project or module
        val templateReferences = findReferencesTo(PROPERTIES, SLOT, SLOTS, MODULE, PROJECT)
            .map(TemplateParentReference.Companion::classify)
            .toList()

        // declarations of properties
        val propertyDeclarations = templateReferences
            .filterIsInstance<TemplateParentReference.PropertyDelegate>()
            .map { it.declaration }

        // remove all declarations
        val declarationBlocks = propertyDeclarations.map { declaration ->
            SkipBlock(position = declaration.sourcePosition(includeTrailingNewline = true))
        }

        // inline blocks with references to properties
        val propertyBlocks = propertyDeclarations.asSequence().flatMap { declaration ->
            declaration.findReferences().flatMap { reference ->
                reference.readPropertyBlocks()
            }
        }

        // inline reference chains
        val chainedReferences = templateReferences
            .filterIsInstance<TemplateParentReference.PropertyReferenceChain>()
            .flatMap { it.expression.readPropertyBlocks() }

        // slot references
        val slots = templateReferences
            .filterIsInstance<TemplateParentReference.Slot>()
            .map { it.expression.readSlotBlock() }

        // include discovered properties in the list of properties
        // TODO bad abstraction
        properties.addAll(propertyDeclarations.map { it.asProperty() })

        return declarationBlocks + propertyBlocks + chainedReferences + slots
    }

}