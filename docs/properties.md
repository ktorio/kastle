# Properties and Expressions Reference

This document contains important reference materials for working with properties in your templates.

## Supported types

| Type      | Encoding                      | Example Value                           |
|-----------|-------------------------------|-----------------------------------------|
| String    | `string`                      | `"Hello, world"`                        |
| Boolean   | `boolean`                     | `true`                                  |
| Int       | `int`                         | `42`                                    |
| Long      | `long`                        | `9223372036854775807`                   |
| Float     | `float`                       | `3.14`                                  |
| Double    | `double`                      | `3.141592653589793`                     |
| Enum      | `enum{value1, value2, ...}`   | `"value1"`                              |
| List      | `list<E>`                     | `["item1", "item2"]` for `list<string>` |
| Object    | `object{K1: E1, K2: E2, ...}` | `{"name": "John", "age": 30}`           |
| Nullables | `E?`                          | `null` or a value of the specified type |

## Configuring properties from a manifest

You can configure properties under `pack.ksl.yaml`. For example:

```yaml
properties:
  - key: serverModules
    type: list<string>?
    hidden: true
  - key: configFormat
    type: enum { HOCON, YAML, none }
    default: HOCON
```

The back deserialized object can be found in [PropertyTypes.kt](/kastle-core/src/commonMain/kotlin/PropertyTypes.kt).

This style of declaration MUST be used for handlebar templates, because there is no way to declare properties in the template itself.

When `hidden` is used, it's best to include a default.  These properties will not be shown in the UI, and can only be populated from other packs included in the templated project.

## Automatic declaration from Kotlin code

You can declare properties in your Kotlin code using the `_properties` property. For example:

```kotlin
// Human-readable label
val variableName: String by _properties
```

Declaring this way will automatically include it in the resulting pack metadata, and it will appear in the UI for configuration.

Note, this does not allow for default values, but you can use nullable types.

## Assigning values from packs

You can assign values from other packs. For example:

```yaml
propertyValues:
  - key: io.ktor/server-core/serverModules
    value: SerializationKt.configureSerialization
```

In this example, the value is parsed based on the declaration of the target _pack_.  This can be useful for hidden properties.

Dynamic expressions can be applied for properties as well.  This is useful for handlebars templates because inline expressions are not possible.

Here is an example:
```yaml
propertyValues:
  - key: isSinglePlatform
    expression: _module.platforms.size == 1
```

The expressions are parsed with the Kotlin compiler, though only a subset of Kotlin is supported in the script engine.
