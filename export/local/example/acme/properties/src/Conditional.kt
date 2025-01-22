fun main() {
    __if("true-condition") {
        println(__value<String>("true-condition"))
    }
    __if("positive-condition") {
        println(__value<String>("positive-condition"))
    }
    __if("null-condition") {
        println(__value<String>("null-condition"))
    }
    __if("false-condition") {
        println(__value<String>("false-condition"))
    }
}