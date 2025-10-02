/**
 * @target slot://com.acme/parent/install
 */
import java.nio.file.Paths

fun Parent.install() {
    // child source here
    println("working dir: " + Paths.get("").toString())
}