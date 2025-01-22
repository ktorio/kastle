fun main() {
    __each("collection") { item ->
        println(__value<String>(item))
    }
}