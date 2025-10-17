package kastle

import kotlin.reflect.KProperty

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

@Suppress("Unused", "ClassName")
object _project {
    val name: String = ""
    val group: String = ""
    val modules: List<TemplateSourceModule> = emptyList()
    val gradle = TemplateGradleProjectSettings()
}

/**
 * References the current module in the templated project.
 */
val _module: TemplateSourceModule = object: TemplateSourceModule {
    override val path: String = ""
    override val type: String = "lib"
    override val platform: String? = null
    override val platforms: List<String> = emptyList()
    override val dependencies: Map<String, List<TemplateBuildDependency>> = emptyMap()
    override val testDependencies: Map<String, List<TemplateBuildDependency>> = emptyMap()
    override val gradle = TemplateGradleModuleSettings()
}

/**
 * Injects the slot with the given name.
 */
val _slot: (String) -> TemplateSlot? = { null }

/**
 * Injects all slots targeting the given slot name.
 */
fun _slots(key: String): Sequence<TemplateSlot> = emptySequence()

/**
 * Returns true when the project config includes a source targeting this slot.
 *
 * Useful for when there is some wrapping content around some slots.
 */
fun _hasSlot(key: String): Boolean = false

/**
 * Inlines the string as raw code.
 */
fun <E> _unsafe(code: String): E = TODO()

interface TemplateProperties {
    operator fun <T> getValue(thisRef: Any?, property: KProperty<*>): T = TODO()
    operator fun <T> get(key: String): T = TODO()
}

interface TemplateSourceModule {
    val path: String
    val type: String
    val platform: String?
    val platforms: List<String>
    val gradle: TemplateGradleModuleSettings
    val dependencies: Map<String, List<TemplateBuildDependency>>
    val testDependencies: Map<String, List<TemplateBuildDependency>>
}

data class TemplateGradleProjectSettings(
    val repositories: List<TemplateMavenRepository> = emptyList(),
    val plugins: List<TemplateGradlePlugin> = emptyList(),
)

data class TemplateGradleModuleSettings(
    val plugins: List<String> = emptyList(),
)

data class TemplateMavenRepository(
    val name: String,
    val url: String,
    val gradleFunction: String?
)

@TemplateDsl
interface TemplateSlot {
    fun <T> get(): T
}

data class TemplateBuildDependency(
    val type: String, // maven, project, catalog
    val group: String? = null,
    val artifact: String? = null,
    val version: String? = null,
    val path: String? = null,
    val key: String? = null,
    val exported: Boolean = false,
)

data class TemplateGradlePlugin(
    val id: String,
    val name: String,
    val version: String
)