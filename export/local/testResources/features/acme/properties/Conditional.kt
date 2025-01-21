fun main() {
    __if("true-condition") {
        println(__property("true-condition"))
    }
    __if("positive-condition") {
        println(__property("positive-condition"))
    }
    __if("null-condition") {
        println(__property("null-condition"))
    }
    __if("false-condition") {
        println(__property("false-condition"))
    }
}