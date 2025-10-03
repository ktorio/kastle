package kastle

// Number example
val numberProperty: Int by _properties
// Boolean example
val booleanProperty: Boolean by _properties
// Nullable property
val nullProperty: String? by _properties
// Also nullable
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