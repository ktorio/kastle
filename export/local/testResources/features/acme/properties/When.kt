fun main() {
    __when("when-property") {
        __equals("yes") {
            println("property is yes")
        }
        __equals("no") {
            println("property is no")
        }
    }
}