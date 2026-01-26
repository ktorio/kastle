package kastle

val booleanProperty: Boolean by _properties
val booleanValue: Boolean by _properties
val integerProperty: Boolean by _properties
val integerValue: Int by _properties
val stringProperty: String by _properties
val stringValue: String by _properties
val listProperty: List<String> by _properties
val listValue: List<String> by _properties

fun main() {
    println("Boolean value: !$booleanProperty -> $booleanValue")
    println("Int value: $integerProperty + 2 = $integerValue")
    println("String value: $stringProperty.uppercase() -> $stringValue")
    println("List value: $listProperty?.map { it.uppercase() }.joinToString(\", \") -> $listValue")
}