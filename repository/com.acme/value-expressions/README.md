This module demonstrates how to use value expressions in your manifest.

You can provide values for other modules, or you can use them to populate your own variables.

## Example manifest

```yaml
propertyValues:
  - key: modulePathUpper
    value: _module.path.uppercase()
```