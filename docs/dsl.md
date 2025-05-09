# ♖ Kastle DSL ♖

The template DSL provided for the KASTLE framework is designed for easy development of 
KASTLE modules.

## Glossary

Below is a list of terms used to describe features in the KASTLE framework.

| Term            | Description                                                                                           |
|-----------------|-------------------------------------------------------------------------------------------------------|
| pack            | A selectable unit for project generation logic.  It includes build dependencies and source templates. |

## Properties

Properties can be retrieved from the project settings with the following delegate:

```kotlin
// An optional description in the preceding comment
val apiEndpoint: String by _properties
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

You can also use simple expressions in your template property references.  They will be evaluated during the templating
to the correct value.

For example, when providing a property "p1" as `listOf("world", "mars")`:

```kotlin
val p1: List<String> by _properties

for (subject in p1.map { it.uppercase() }) {
    println("Hello, $subject")
}
```

This will generate the following source:

```kotlin
println("Hello, WORLD")
println("Hello, MARS")
```

More information can be found in [properties.md](properties.md) regarding the supported types and operations.

## Unsafe

You can use Kotlin's string templating to inject code from your variables.

For example:

```kotlin
val variableName: String by _properties

_unsafe("$variableName.bark()")
```

Will be rendered to the following when `variableName` is supplied as `fido`:

```kotlin
fido.bark()
```


## Slots

Template sources can be nested by exposing _slots_.

This can be done like so:

```kotlin
class Parent {
    fun install() {
        _slots("install")
    }
}
```

This allows for other modules to target this slot with their sources.

Then the contents can be provided from a child pack using comments:

```kotlin
/**
 * @target slot:/org.parent/parent/install
 */
fun Parent.install() {
    // child source here
    println("working dir: " + Paths.get("").toString())
}
```

This will automatically extract the function body and transfer any unique imports to the generated source files.