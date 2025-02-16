import kotlin.reflect.KProperty

@DslMarker
annotation class TemplateDsl

interface TemplateParent {
    operator fun <T> getValue(thisRef: Any?, property: KProperty<*>): T = TODO()
    operator fun <T> get(key: String): T = TODO()

    fun slot(key: String): Slot? = null
    fun slots(key: String): Sequence<Slot> = emptySequence()
}

interface DependencyHolder {
    val dependencies: Collection<BuildDependency>
    val testDependencies: Collection<BuildDependency>
}

@TemplateDsl
object Project: TemplateParent {
    val modules: List<Module> = emptyList()

    override operator fun <T> getValue(thisRef: Any?, property: KProperty<*>): T = TODO()
    override operator fun <T> get(key: String): T = TODO()
    override fun slot(key: String): Slot? = null
    override fun slots(key: String): Sequence<Slot> = emptySequence()
}

object Module: TemplateParent, DependencyHolder {
    val path: String = ""
    val defaultTarget: Target? = null
    val targets: List<Target> = emptyList()

    override operator fun <T> getValue(thisRef: Any?, property: KProperty<*>): T = TODO()
    override operator fun <T> get(key: String): T = TODO()
    override fun slot(key: String): Slot? = null
    override fun slots(key: String): Sequence<Slot> = emptySequence()

    override val dependencies: Collection<BuildDependency> = emptyList()
    override val testDependencies: Collection<BuildDependency> = emptyList()
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