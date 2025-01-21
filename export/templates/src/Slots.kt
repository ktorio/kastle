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