/**
 * Replaces the invocation with a literal of the property's value.
 */
@TemplateDsl
inline fun <reified T> __value(name: String, noinline default: (() -> T)? = null): T =
    default?.invoke() ?: TODO("No default provided")

/**
 * Include the contents only when the provided property is truthy.
 */
@TemplateDsl
fun __if(property: String, block: () -> Unit): Unit = Unit

@TemplateDsl
fun __else(block: () -> Unit): Unit = Unit

/**
 * Include the contents only when the provided property is truthy.
 */
@TemplateDsl
fun __each(property: String, block: (String) -> Unit): Unit = Unit

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