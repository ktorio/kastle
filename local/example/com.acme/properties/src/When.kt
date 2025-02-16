val whenProperty: String by Project

fun main() {
    when (whenProperty) {
        "yes" -> println("property is yes")
        "no" -> println("property is no")
    }
}