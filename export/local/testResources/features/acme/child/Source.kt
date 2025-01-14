import java.nio.file.Paths

fun Literal.install() {
    // child source here
    println("working dir: " + Paths.get("").toString())
}