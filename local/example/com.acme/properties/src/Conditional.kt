val trueCondition: Boolean by _properties
val positiveCondition: Boolean by _properties
val nullCondition: Boolean by _properties
val falseCondition: Boolean by _properties

fun main() {
    if (trueCondition) {
        println("trueCondition is true")
    }
    if (positiveCondition) {
        println("positiveCondition is true")
    }
    if (nullCondition) {
        println("nullCondition is true")
    } else {
        println("nullCondition else")
    }
    if (falseCondition) {
        println("falseCondition is true")
    } else {
        println("false condition else")
    }
}