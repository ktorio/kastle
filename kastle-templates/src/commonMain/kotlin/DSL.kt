package kastle

import kotlinx.serialization.Serializable
import kotlin.reflect.KProperty

private const val RUNTIME_ERROR = "This function is a placeholder for the compiler and cannot be invoked"

@DslMarker
annotation class TemplateDsl

/**
 * For reading properties supplied from the project configuration.
 */
val _properties: TemplateProperties = object : TemplateProperties {}

/**
 * For reading properties supplied from child modules.
 */
val _attributes: TemplateProperties = object : TemplateProperties {}

/**
 * References the current project.
 */
val _project = TemplateProject()

/**
 * References the current module in the templated project.
 */
val _module: TemplateSourceModule = TemplateSourceModule()

/**
 * Injects the slot with the given name.
 */
val _slot: (String) -> TemplateSlot? = { null }

/**
 * Injects all slots targeting the given slot name.
 */
val _slots: TemplateSlots = TODO(RUNTIME_ERROR)

/**
 * Inlines the string as raw code.
 */
fun <E> _unsafe(code: String): E = TODO(RUNTIME_ERROR)

interface TemplateSlots: Map<String, List<TemplateSlot>> {
    operator fun invoke(key: String)
}

interface TemplateProperties {
    operator fun <T> getValue(thisRef: Any?, property: KProperty<*>): T = TODO(RUNTIME_ERROR)
    operator fun <T> get(key: String): T = TODO(RUNTIME_ERROR)
}

@Serializable
data class TemplateProject(
    val name: String = "",
    val group: String = "",
    val namespace: String = "",
    val modules: List<TemplateSourceModule> = emptyList(),
    val packs: List<TemplatePack> = emptyList(),
    val versions: Map<String, String> = emptyMap(),
    val libraries: Map<String, TemplateCatalogArtifact> = emptyMap(),
    val buildSystem: String? = null,
    val gradle: TemplateGradleProjectSettings = TemplateGradleProjectSettings()
) {
    val module: TemplateSourceModule? get() = modules.singleOrNull()
}

@Serializable
data class TemplatePack(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val group: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class TemplateSourceModule(
    val path: String = "",
    val type: String = "lib", // amper-style module type (lib, app/jvm, etc.)
    val platforms: List<String> = emptyList(), // jvm, android, wasm, etc.
    val gradle: TemplateGradleModuleSettings = TemplateGradleModuleSettings(),
    val amper: TemplateAmperModuleSettings = TemplateAmperModuleSettings(),
    val dependencies: Map<String, List<TemplateBuildDependency>> = emptyMap(),
    val testDependencies: Map<String, List<TemplateBuildDependency>> = emptyMap(),
) {
    val parent: String? get() = path.substringBeforeLast('/').takeIf { it.isNotEmpty() }
    val platform: String? = platforms.singleOrNull()
}

@Serializable
data class TemplateGradleProjectSettings(
    val repositories: List<TemplateMavenRepository> = emptyList(),
    val pluginRepositories: List<TemplateMavenRepository> = emptyList(),
    val plugins: List<TemplateGradlePlugin> = emptyList(),
)

@Serializable
data class TemplateGradleModuleSettings(
    val plugins: List<String> = emptyList(),
)

@Serializable
data class TemplateAmperModuleSettings(
    val compose: String? = null,
    val ktor: String? = null,
    val application: TemplateAmperApplicationSettings? = null,
    val kotlin: TemplateAmperKotlinSettings? = null,
)

@Serializable
data class TemplateAmperApplicationSettings(
    val mainClass: String? = null,
)

@Serializable
data class TemplateAmperKotlinSettings(
    val serialization: String? = null,
)

@Serializable
data class TemplateMavenRepository(
    val id: String,
    val url: String,
    val gradleFunction: String?
)

@TemplateDsl
interface TemplateSlot {
    fun <T> get(): T
}

@Serializable
data class TemplateBuildDependency(
    val type: String, // maven, project, catalog
    val group: String? = null,
    val artifact: String? = null,
    val version: String? = null,
    val versionRef: String? = null,
    val path: String? = null,
    val gradlePath: String? = null,
    val key: String? = null,
    val functionName: String? = null,
    val args: List<String>? = null,
    @Deprecated("Use scope == \"api\" instead.", ReplaceWith("scope == \"api\""))
    val exported: Boolean = false,
    val reference: String? = null,
    val typesafeProjectAccessor: String? = null,
    val scope: String = if (exported) "api" else "implementation",
) {
    constructor(
        type: String,
        group: String? = null,
        artifact: String? = null,
        version: String? = null,
        versionRef: String? = null,
        path: String? = null,
        gradlePath: String? = null,
        key: String? = null,
        functionName: String? = null,
        args: List<String>? = null,
        exported: Boolean = false,
    ): this(
        type = type,
        group = group,
        artifact = artifact,
        version = version,
        versionRef = versionRef,
        path = path,
        gradlePath = gradlePath,
        key = key,
        functionName = functionName,
        args = args,
        exported = exported,
        scope = if (exported) "api" else "implementation",
    )

    @Suppress("DEPRECATION")
    fun copy(
        type: String = this.type,
        group: String? = this.group,
        artifact: String? = this.artifact,
        version: String? = this.version,
        versionRef: String? = this.versionRef,
        path: String? = this.path,
        gradlePath: String? = this.gradlePath,
        key: String? = this.key,
        functionName: String? = this.functionName,
        args: List<String>? = this.args,
        exported: Boolean = this.exported,
    ): TemplateBuildDependency = TemplateBuildDependency(
        type = type,
        group = group,
        artifact = artifact,
        version = version,
        versionRef = versionRef,
        path = path,
        gradlePath = gradlePath,
        key = key,
        functionName = functionName,
        args = args,
        exported = exported,
    )
}

@Serializable
data class TemplateGradlePlugin(
    val id: String,
    val name: String,
    val catalogKey: String,
    val version: String? = null,
    val versionRef: String? = null,
)

@Serializable
data class TemplateCatalogArtifact(
    val module: String? = null,
    val group: String? = null,
    val artifact: String? = null,
    val version: String? = null,
    val versionRef: String? = null,
    val kmp: Boolean = false,
)
