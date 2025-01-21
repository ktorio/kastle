fun main() {
    __if("true-condition") {
        println(__value("true-condition"))
    }
    __if("positive-condition") {
        println(__value("positive-condition"))
    }
    __if("null-condition") {
        println(__value("null-condition"))
    }
    __if("false-condition") {
        println(__value("false-condition"))
    }
}