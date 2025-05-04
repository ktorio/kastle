This is an example of a parent module.

It contains a source file with slots that can be targeted by child modules.

Here is what the source looks like:

```kotlin
class Parent {
    fun install() {
        _slots("install")
    }
}
```

Note the use of the `_slots` invocation, which will swap in content from other sources.