# ♖ Kastle DSL ♖

The template DSL provided for the KASTLE framework is designed for easy development of 
KASTLE modules.

## Glossary

Below is a list of terms used to describe features in the KASTLE framework.

| Term            | Description                                                                                           |
|-----------------|-------------------------------------------------------------------------------------------------------|
| module          | A selectable unit for project generation logic.  It includes build dependencies and source templates. |
| truthy / falsey | Lenient evaluation of a property value, similar to Javascript boolean casting.                        |

## Properties

Properties can be retrieved from the project settings with the following property delegate:

```kotlin
// An optional description in the preceding comment
val apiEndpoint: String by Template
```

You can then use the property inside your template source as if you would normally introduce control flows.

For example, with this template:

```kotlin
when(apiEndpoint) {
    "api.myapp.com" -> {
        // Contents are inlined from this scope when the value matches
    }
    else -> {
        // When preceding values do not match, this content is shown
    }
}
```

The contents of the switch entry will be used to populate the resulting source file.

This style of inline can also be performed with the `if`, `else`, and `for` control structures.  If the property
is referenced from a location that cannot be inlined, it will be treated as a literal.

## Slots

Template sources can be nested by exposing _slots_.

This can be done like so:

```kotlin
class Parent {
    fun install() {
        Template.Slots("install")
    }
}
```

This allows for other modules to target this slot with their sources.

For example, in the child module's `manifest.yaml`:

```yaml
sources:
  - path: Source.kt
    target: "slot://acme/parent/install"
```

Then the contents can be provided by an extension function in `Source.kt`:

```kotlin
import java.nio.file.Paths

fun Parent.install() {
    // child source here
    println("working dir: " + Paths.get("").toString())
}
```

This will automatically extract the function body, and transfer any unique imports to the generated source files.