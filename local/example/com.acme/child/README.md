This is an example of a child module.

It contains a source file which targets a slot in the parent.

Here is what the source looks like:

```kotlin
/**
 * @target slot://com.acme/parent/install
 */
import java.nio.file.Paths

fun Parent.install() {
    // child source here
    println("working dir: " + Paths.get("").toString())
}
```