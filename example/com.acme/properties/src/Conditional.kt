val numberProperty: Int by _properties
val booleanProperty: Boolean by _properties
val nullProperty: String? by _properties
val missingProperty: String? by _properties

fun main() {
    if (numberProperty > 0) {
        println("numberProperty is positive")
    }
    if (booleanProperty) {
        println("booleanProperty is true")
    }
    if (nullProperty != null) {
        println("nullProperty is true?")
    } else {
        println("nullProperty else")
    }
    if (missingProperty != null) {
        println("missingProperty is true?")
    } else {
        println("missingProperty else")
    }
}