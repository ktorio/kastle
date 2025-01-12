@DslMarker
annotation class TemplateDsl

/**
 * Given the slot name, this template will be injected from a child during project generation.
 */
@TemplateDsl
inline fun <reified T> __slot__(name: String, noinline default: (() -> T)? = null): T =
    default?.invoke() ?: TODO("No default provided")

/**
 * This will repeat the contents for all instances of the slot.
 */
@TemplateDsl
fun __each__(name: String, block: () -> Unit = {}): Unit = Unit

/**
 * Contents of the block are included only when the property is assigned.
 */
@TemplateDsl
fun __when__(property: String, block: WhenContext.() -> Unit): Unit = Unit

@TemplateDsl
interface WhenContext {
    fun __equals__(value: Any?, block: () -> Unit)
}