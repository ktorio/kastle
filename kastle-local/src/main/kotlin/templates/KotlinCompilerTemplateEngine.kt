package org.jetbrains.kastle.templates

import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.elementType
import kotlinx.io.files.Path
import org.jetbrains.kastle.*
import org.jetbrains.kastle.BlockPosition
import org.jetbrains.kastle.SkipBlock
import org.jetbrains.kastle.SourceTemplate
import org.jetbrains.kastle.io.readText
import org.jetbrains.kastle.io.resolve
import org.jetbrains.kastle.logging.ConsoleLogger
import org.jetbrains.kastle.logging.Logger
import org.jetbrains.kastle.utils.*
import org.jetbrains.kastle.utils.protocol
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import java.io.File

/**
 * Provides analysis capabilities for Kotlin source files within a specified path.
 * This class uses Kotlin compiler's source analysis with a predefined environment setup.
 *
 * @constructor Initializes the source analyzer with the specified path and repository.
 * The environment is configured for JVM production with basic compiler settings.
 *
 * @property path The path to the Kotlin source files to be analyzed.
 */
internal class KotlinCompilerTemplateEngine(
    private val path: Path? = null,
    private val log: Logger = ConsoleLogger(),
    private val onProperty: (PropertyDescriptor) -> Unit = {},
) {
    val environment: KotlinCoreEnvironment
    val psiFileFactory: PsiFileFactory
    val expressionParser: KotlinExpressionParser
    // TODO verify compilation, etc.
    //private val analyzer = TopDownAnalyzerFacadeForJVM

    init {
        val verbose = false
        val stderrMessages = PrintingMessageCollector(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, verbose)
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, stderrMessages)
            path?.parent?.name?.let { put(CommonConfigurationKeys.MODULE_NAME, it) }
            put(CommonConfigurationKeys.ALLOW_ANY_SCRIPTS_IN_SOURCE_ROOTS, true)
            put(JVMConfigurationKeys.JDK_HOME, File(System.getenv("JAVA_HOME")))

            path?.let { addKotlinSourceRoot(path.toString()) }
        }
        @OptIn(K1Deprecation::class)
        environment = KotlinCoreEnvironment.createForProduction(
            Disposer.newDisposable(),
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
        psiFileFactory = PsiFileFactory.getInstance(environment.project)
        expressionParser = KotlinExpressionParser(psiFileFactory)
    }

    val ktFiles: List<KtFile> by lazy {
        environment.getSourceFiles()
    }

    fun read(
        path: Path? = null,
        text: String? = null,
    ): SourceTemplate {
        val psiFile = psiFileFactory.createFileFromText(
            path.toString(),
            KotlinFileType.INSTANCE,
            text ?: path?.readText() ?: throw IllegalArgumentException("Missing path or text in source definition"),
        )
        return read(path ?: Path("source.kt"), psiFile as KtFile)
    }

    /**
     * Parses a source template reference and retrieves the corresponding source template.
     *
     * @param reference The reference to the source template file which includes path and target details.
     * @returns A `SourceTemplate` object that encapsulates the read source text and its metadata.
     *
     * @throws IllegalArgumentException if the source file specified in the reference cannot be found.
     */
    fun read(
        sourcePath: Path,
        ktFile: KtFile,
    ): SourceTemplate {
        // TODO drop header
        // TODO full file path
        val targetFromHeader = ktFile
            .descendantsOfType<PsiComment>()
            .firstOrNull()
            ?.text?.lines()?.map {
                it.trimStart('/', '*', ' ', '\t').trimEnd()
            }?.firstOrNull {
                it.startsWith("slot:")
            }
        val targetString = targetFromHeader ?: "file:${sourcePath.resolve(ktFile.name)}"
        val target = expressionParser.parseTemplate(targetString)
        log.debug { "Compiling $targetString..." }

        return when (target.protocol) {
            "file" -> SourceTemplate(
                target = target,
                text = ktFile.text,
                imports = ktFile.readImports(),
                blocks = ktFile.findBlocks(),
            )
            "slot" -> {
                // TODO should be able to specify which function
                val nestedBlocks = when (val functionBody = ktFile.firstFunctionBody()) {
                    null -> ktFile.findBlocks()
                    // skip everything outside the function body
                    else -> {
                        // skip braces and whitespace
                        val functionBodyRange = functionBody.bodyRange()
                        functionBody.findBlocks() +
                                SkipBlock(position = BlockPosition(0, 0..functionBodyRange.first)) +
                                SkipBlock(position = BlockPosition(0, functionBodyRange.last..ktFile.textLength))
                    }
                }

                SourceTemplate(
                    target = target,
                    text = ktFile.text,
                    imports = ktFile.readImports(),
                    blocks = nestedBlocks,
                )
            }
            else -> throw IllegalArgumentException("Unsupported target protocol: ${target.protocol}")
        }
    }

    private fun KtFile.firstFunctionBody(): KtExpression? {
        val fileBody = if (name.extension == "kts") {
            declarations
                .filterIsInstance<KtScript>()
                .singleOrNull() ?: return null
        } else this

        val namedFunction = fileBody.declarations
            .filterIsInstance<KtNamedFunction>()
            .singleOrNull() ?: return null

        return namedFunction.bodyExpression
    }

    private fun KtExpression.bodyRange(): IntRange {
        val fileText = containingFile.text
        val startOffset = children.first {
            it !is PsiWhiteSpace && it.elementType !is KtSingleValueToken
        }.startOffset.let {
            fileText.startOfLine(it) ?: it
        }
        val endOffset = children.last {
            it !is PsiWhiteSpace && it.elementType !is KtSingleValueToken
        }.endOffset

        return startOffset .. endOffset
    }

    private fun KtExpression.bodyText(): String =
        text.trimBraces().trim('\n').trimIndent().trim()

    private fun KtElement.findBlocks(): List<Block> {
        // references to project or module
        val templateReferences = findReferencesTo(
            PROPERTIES,
            SLOT,
            SLOTS,
            MODULE,
            PROJECT,
            UNSAFE
        ).map(TemplateParentReference.Companion::classify).toList()

        // declarations of properties
        val propertyDeclarations = templateReferences
            .filterIsInstance<TemplateParentReference.PropertyDelegate>()

        // strip declarations using skip blocks
        val declarationBlocks = propertyDeclarations.map {
            SkipBlock(position = it.declaration.blockPosition())
        }

        // inline blocks with references to properties
        val propertyBlocks = propertyDeclarations.flatMap {
            it.declaration.findReferences().flatMap { reference ->
                reference.readReferenceBlocks()
            }
        }

        // inline reference chains
        val chainedReferences = templateReferences
            .filterIsInstance<TemplateParentReference.PropertyReference>()
            .flatMap { it.expression.readReferenceBlocks() }

        // slot references
        val slots = templateReferences
            .filterIsInstance<TemplateParentReference.Slot>()
            .map { it.expression.readSlotBlock() }

        // unsafe blocks
        val unsafeBlocks = templateReferences
            .filterIsInstance<TemplateParentReference.Unsafe>()
            .map { it.expression.readUnsafeBlock() }

        // include discovered properties in the list of properties
        for (property in propertyDeclarations.map { it.asProperty() }) {
            onProperty(property)
        }

        // sort, indent logic
        val allBlocks = collect(
            declarationBlocks,
            propertyBlocks,
            chainedReferences,
            slots,
            unsafeBlocks,
        )

        return allBlocks
    }

}

/**
 * Sort all blocks and correct indentations based on inlining.
 */
private fun collect(vararg lists: Collection<out Block>): List<Block> {
    val blocks = lists.toList()
        .flatten()
        .sortedBy { it.rangeStart }
        .distinct()

    // inherit indentation for nested blocks
    for (i in blocks.indices) {
        if (i == 0) continue
        val current = blocks[i]

        var nesting = if (current is StructuralBlock) 1 else 0
        for (j in i - 1 downTo 0) {
            val previous = blocks[j] as? StructuralBlock ?: continue
            if (current in previous)
                nesting++
        }
        if (nesting > 0) {
            current.position = current.position.copy(level = nesting)
        }
    }
    return blocks
}
