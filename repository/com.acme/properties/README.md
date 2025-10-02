This module demonstrates various methods for introducing templating logic to your Kastle pack.  It is used in testing and as documentation.

## Properties

You can introduce and import properties to your Kotlin source templates using property delegation:

```kotlin
// you can import anything as a boolean - it will be true if not null and a non-empty, non-zero, non-false string
val booleanProperty: Boolean by _properties
// you can import simple types from the provided configuration
val integerProperty: Int by _properties
// you can also import lists for later use in loops
val listProperty: List<String> by _properties
```

This will allow you to inline various parts of your template using the value provided in the project configuration.

## Conditionals

Using the imported property above, we can create branches in our template.

```kotlin
if (booleanProperty) {
    // Content here only appears if the imported property is true
    // When no inlining logic applies, the literal value of the property is used, like in string templates
    println("booleanProperty is $booleanProperty")
}
```

## Loops

We can use list properties in for-each loops:

```kotlin
for (val element in listProperty) {
    // This content will be repeated for every element
    println("Element $element")
}
```

## When

When blocks will also match imported properties:

```kotlin
when(integerProperty) {
    123 -> println("The magic number has been chosen for this template")
    else -> println("Something else has been chosen")
}
```