import java.nio.file.Paths

fun Source.install() {
    // child source here
    println("working dir: " + Paths.get("").toString())
}