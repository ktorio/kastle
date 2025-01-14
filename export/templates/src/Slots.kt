@DslMarker
annotation class TemplateDsl

/**
 * Given the slot name, this template will be injected from a child during project generation.
 */
@TemplateDsl
inline fun <reified T> __slot(name: String, noinline default: (() -> T)? = null): T = default?.invoke() ?: TODO("No default provided")

/**
 * This will repeat the contents for all instances of the slot.
 */
@TemplateDsl
fun __slots(name: String, block: () -> Unit = {}): Unit = Unit

/**
 * Replaces the invocation with a literal of the property's value.
 */
@TemplateDsl
inline fun <reified T> __property(name: String, noinline default: (() -> T)? = null): T =
    default?.invoke() ?: TODO("No default provided")

/**
 * Include the contents only when the provided property is truthy.
 */
@TemplateDsl
fun __if(property: String, block: () -> Unit): Unit = Unit

/**
 * Include the contents only when the provided property is truthy.
 */
@TemplateDsl
fun __each(property: String, block: () -> Unit): Unit = Unit

/**
 * Contents of the block are included only when the property is assigned.
 */
@TemplateDsl
fun __when(property: String, block: WhenContext.() -> Unit): Unit = Unit

/**
 * DSL context for conditional templating from project properties.
 */
@TemplateDsl
interface WhenContext {
    fun __equals(value: Any?, block: () -> Unit)
}