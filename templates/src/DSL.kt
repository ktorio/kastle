import kotlin.reflect.KProperty

@DslMarker
annotation class TemplateDsl

/**
 * For reading properties supplied from the project configuration.
 */
val _properties: TemplateProperties = object : TemplateProperties {}

/**
 * References the current module in the templated project.
 */
val _module: SourceModule = object: SourceModule {
    override val path: String = ""
    override val defaultTarget: Target? = null
    override val targets: List<Target> = emptyList()

    override val dependencies: Collection<BuildDependency> = emptyList()
    override val testDependencies: Collection<BuildDependency> = emptyList()
}

/**
 * Injects the slot with the given name.
 */
val _slot: (String) -> Slot? = { null }

/**
 * Injects all slots targetting the given slot name.
 */
fun _slots(key: String): Sequence<Slot> = emptySequence()

interface TemplateProperties {
    operator fun <T> getValue(thisRef: Any?, property: KProperty<*>): T = TODO()
    operator fun <T> get(key: String): T = TODO()
}

interface DependencyHolder {
    val dependencies: Collection<BuildDependency>
    val testDependencies: Collection<BuildDependency>
}

interface SourceModule: DependencyHolder {
    val path: String
    val defaultTarget: Target?
    val targets: List<Target>
}

@TemplateDsl
interface Slot {
    fun <T> get(): T
}

interface Target: DependencyHolder {
    val name: String
}

data class BuildDependency(
    val group: String,
    val artifact: String,
    val version: String
) {
    companion object {
        private val tlds = setOf("org", "io", "com")
    }

    val catalogReference: String get() {
        var prev: String? = null
        return sequenceOf(group, artifact).flatMap {
            it.split(Regex("\\W"))
        }.filterIndexed { i, token ->
            try {
                token.isNotBlank() && !(i == 0 && token in tlds) && prev != token
            } finally {
                prev = token
            }
        }.joinToString(".")
    }

}