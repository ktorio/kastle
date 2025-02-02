val whenProperty: String by Template

fun main() {
    when (whenProperty) {
        "yes" -> println("property is yes")
        "no" -> println("property is no")
    }
}