package org.jetbrains.kastle

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.kastle.io.readText
import org.jetbrains.kastle.io.readYaml
import org.jetbrains.kastle.io.resolve
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.parents
import java.io.File
import kotlin.collections.map

private const val MANIFEST_YAML = "manifest.yaml"
private const val GROUP_YAML = "group.yaml"

class LocalFeatureRepository(
    private val root: Path,
    private val fs: FileSystem = SystemFileSystem,
    remoteRepository: FeatureRepository = FeatureRepository.EMPTY,
): FeatureRepository {
    constructor(root: String): this(Path(root))

    private val repository: FeatureRepository = object : FeatureRepository {
        private val cache = mutableMapOf<FeatureId, FeatureDescriptor>()

        override fun featureIds(): Flow<FeatureId> =
            remoteRepository.featureIds()

        override suspend fun get(id: FeatureId): FeatureDescriptor? =
            cache[id] ?: fromLocalOrRemote(id)?.also {
                cache[id] = it
            }

        override suspend fun slot(slotId: SlotId): Slot? =
            this.get(slotId.feature)?.sources?.flatMap { it.slots.orEmpty() }?.find { slot ->
                slot.name == slotId.name
            }

        private suspend fun fromLocalOrRemote(id: FeatureId): FeatureDescriptor? =
            this@LocalFeatureRepository.get(id) ?: remoteRepository.get(id)

    }

    override fun featureIds(): Flow<FeatureId> =
        fs.list(root).flatMap { groupPath ->
            fs.list(groupPath)
        }.asFlow().mapNotNull { path ->
            if (fs.metadataOrNull(path)?.isDirectory == true)
                FeatureId.parse("${path.parent!!.name}/${path.name}")
            else null
        }

    override suspend fun get(featureId: FeatureId): FeatureDescriptor? {
        val path = root.resolve(featureId.toString())
        val manifest: FeatureManifest = path.resolve(MANIFEST_YAML).readYaml()
            ?: throw MissingManifestFileException("Cannot find $MANIFEST_YAML in $path")
        val group = manifest.group ?: path.resolve("../$GROUP_YAML").readYaml()
        val analyzer = KotlinCompilerSourceAnalyzer(path, repository)
        val sources = manifest.sources.asFlow().map(analyzer::read)

        return FeatureDescriptor(manifest.copy(group = group), sources.toList())
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
 * @property repository An optional feature repository for additional data or functionality.
 */
class KotlinCompilerSourceAnalyzer(
    private val path: Path,
    private val repository: FeatureRepository = FeatureRepository.EMPTY,
) {
    private var environment: KotlinCoreEnvironment
    private var psiFileFactory: PsiFileFactory
    private val analyzer = TopDownAnalyzerFacadeForJVM

    init {
        val verbose = false
        val stderrMessages = PrintingMessageCollector(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, verbose)
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, stderrMessages)
            put(CommonConfigurationKeys.MODULE_NAME, "PluginRegistry")
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

    /**
     * Parses a source template reference and retrieves the corresponding source template.
     *
     * @param reference The reference to the source template file which includes path and target details.
     * @returns A `SourceTemplate` object that encapsulates the read source text and its metadata.
     *
     * @throws IllegalArgumentException if the source file specified in the reference cannot be found.
     */
    suspend fun read(reference: SourceTemplateReference): SourceTemplate {
        val contents = path.resolve(reference.path).readText() ?: throw IllegalArgumentException("Source file not found: ${reference.path}")
        val ktFile by lazy { psiFileFactory.createFileFromText(reference.path, KotlinFileType.INSTANCE, contents) as KtFile }

        return when (reference.target.protocol) {
            "file" -> SourceTemplate(
                text = contents,
                target = reference.target,
                slots = ktFile.findSlots(),
            )
            "slot" -> {
                val slot = repository.slot(reference.target.slotId)
                    ?: throw IllegalArgumentException("Slot not found: ${reference.target.afterProtocol}")
                val imports = ktFile.importList?.imports?.map { it.text.substring("import ".length) } ?: emptyList()
                val text = when(slot.position) {
                    is SlotPosition.TopLevel -> ktFile.endOfImports()?.let { endOfImports ->
                        contents.substring(endOfImports)
                    } ?: contents
                    // TODO other positions, annotations, etc.
                    is SlotPosition.Inline -> ktFile.functionBody()
                        ?: throw IllegalArgumentException("Expected single function body for targeting slot")
                }
                SourceTemplate(
                    text = text,
                    target = reference.target,
                    imports = imports,
                    slots = ktFile.findSlots(),
                )
            }
            else -> throw IllegalArgumentException("Unsupported target protocol: ${reference.target.protocol}")
        }
    }

    private fun KtFile.endOfImports(): Int? =
        importDirectives.maxOfOrNull { it.textRange.endOffset }

    private fun KtFile.functionBody() =
        declarations.filterIsInstance<KtNamedFunction>()
            .singleOrNull()
            ?.bodyExpression?.text?.trimBraces()?.trimIndent()?.trim()

    private fun KtFile.findSlots(): List<Slot> =
        findFunctionCalls(
            "__slot__",
            "__each__",
            "__when__",
        ).map { expression ->
            val arguments = expression.valueArguments.map { it.text }

            when(expression.calleeExpression?.text) {
                "__slot__" -> NamedSlot(
                    name = arguments[0].unwrapQuotes(),
                    position = expression.slotPosition()
                )
                "__each__" -> RepeatingSlot(
                    name = arguments[0].unwrapQuotes(),
                    position = expression.slotPosition()
                )
                "__when__" -> TODO("Complicated, do later")
                else -> throw IllegalArgumentException("Unexpected function: ${expression.calleeExpression?.text}")
            }
        }

    private fun PsiElement.slotPosition(): SlotPosition =
        parents.firstNotNullOfOrNull { parent ->
            when(parent) {
                is KtClass -> parent.name
                is KtNamedFunction -> parent.receiverTypeReference?.name
                else -> null
            }
        }?.let { context ->
            SlotPosition.Inline(textRange.toIntRange(), context)
        } ?: SlotPosition.TopLevel(textRange.toIntRange())

    private fun KtFile.findFunctionCalls(vararg functionNames: String): List<KtCallExpression> =
        buildList {
            val visitor = object : KtTreeVisitorVoid() {

                override fun visitCallExpression(expression: KtCallExpression) {
                    super.visitCallExpression(expression)
                    if (expression.calleeExpression?.text in functionNames)
                        add(expression)
                }
            }
            accept(visitor)
        }

    private fun TextRange.toIntRange(): IntRange =
        startOffset..<endOffset

    // Function contents usually will include braces
    private fun String.trimBraces() =
        if (startsWith('{') && endsWith('}'))
            substring(1, length - 1)
        else this

    private fun String.unwrapQuotes() =
        if (startsWith('"') && endsWith('"'))
            substring(1, length - 1)
        else this
}