/**
 * @target slot://acme/parent/install
 */
fun Parent.install() {
    (0..10).forEach {
        println(it)
    }
}