package kastle

val whenProperty: String by _properties

fun main() {
    when (whenProperty) {
        "yes" -> {
            println("property is yes")
        }
        "no" -> {
            println("property is no")
        }
    }
}