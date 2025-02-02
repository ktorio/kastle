package org.jetbrains.kastle

import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFileFactory
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
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.io.File
import kotlin.collections.*
import kotlin.text.substring
import kotlin.text.trim
import kotlin.text.trimIndent

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
            this.get(slotId.feature)?.sources?.flatMap { it.blocks.orEmpty() }?.filterIsInstance<Slot>()?.find { slot ->
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
        val sourceFolder = path.resolve("src")
        if (!fs.exists(sourceFolder)) {
            return FeatureDescriptor(
                manifest.copy(group = group),
                sources = emptyList()
            )
        }

        // Properties are supplied both from the manifest and from declarations in the source files
        val analyzer = KotlinCompilerSourceAnalyzer(sourceFolder, repository)
        val properties = manifest.properties.toMutableList()
        val sources = manifest.sources.asFlow()
            .map { sourceFile ->
                analyzer.read(sourceFile, properties)
            }.toList()

        return FeatureDescriptor(
            manifest.copy(
                group = group,
                properties = properties.distinctBy { it.key },
            ),
            sources
        )
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
private class KotlinCompilerSourceAnalyzer(
    private val path: Path,
    private val repository: FeatureRepository = FeatureRepository.EMPTY,
) {

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

    /**
     * Parses a source template reference and retrieves the corresponding source template.
     *
     * @param reference The reference to the source template file which includes path and target details.
     * @returns A `SourceTemplate` object that encapsulates the read source text and its metadata.
     *
     * @throws IllegalArgumentException if the source file specified in the reference cannot be found.
     */
    suspend fun read(
        reference: SourceTemplateReference,
        properties: MutableList<Property>
    ): SourceTemplate {
        val contents = path.resolve(reference.path).readText() ?: throw IllegalArgumentException("Source file not found: ${reference.path}")
        val ktFile by lazy { psiFileFactory.createFileFromText(reference.path, KotlinFileType.INSTANCE, contents) as KtFile }

        return when (reference.target.protocol) {
            "file" -> SourceTemplate(
                text = contents,
                target = reference.target,
                blocks = ktFile.findBlocks(properties),
            )
            "slot" -> {
                val slot = repository.slot(reference.target.slotId)
                    ?: throw IllegalArgumentException("Slot not found: ${reference.target.afterProtocol}")
                val imports = ktFile.importList?.imports?.map { it.text.substring("import ".length) } ?: emptyList()
                val text = when(slot.position) {
                    is SourcePosition.TopLevel -> ktFile.endOfImports()?.let { endOfImports ->
                        contents.substring(endOfImports)
                    } ?: contents
                    // TODO other positions, annotations, etc.
                    is SourcePosition.Inline -> ktFile.firstFunctionBody()
                        ?: throw IllegalArgumentException("Expected single function body for targeting slot")
                }
                SourceTemplate(
                    text = text,
                    target = reference.target,
                    imports = imports,
                    blocks = ktFile.findBlocks(properties),
                )
            }
            else -> throw IllegalArgumentException("Unsupported target protocol: ${reference.target.protocol}")
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
        // TODO merge properties to feature manifest
        properties.addAll(propertyDeclarations.map { it.asProperty() })

        return declarationBlocks + propertyBlocks + slots
    }

}