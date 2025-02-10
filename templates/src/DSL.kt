import kotlin.reflect.KProperty

@DslMarker
annotation class TemplateDsl

@TemplateDsl
object Template {

    operator fun <T> getValue(thisRef: Any?, property: KProperty<*>): T {
        TODO("Not implemented")
    }

    operator fun <T> get(key: String): T {
        TODO("Not implemented")
    }

    operator fun invoke(name: String): Slot =
        TODO("Not implemented")

    fun <T> Property(key: String): T? =
        TODO("Not implemented")

    fun Slot(key: String): Slot? =
        TODO("Not implemented")

    fun Slots(key: String): Sequence<Slot> =
        TODO("Not implemented")
}

@TemplateDsl
interface Slot {
    fun <T> get(): T
}
