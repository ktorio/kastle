val trueCondition: Boolean by Template
val positiveCondition: Boolean by Template
val nullCondition: Boolean by Template
val falseCondition: Boolean by Template

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