val trueCondition: Boolean by Project
val positiveCondition: Boolean by Project
val nullCondition: Boolean by Project
val falseCondition: Boolean by Project

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