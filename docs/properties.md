# Properties Reference

This document contains important reference materials for working with properties in your templates.

## Supported types

| Type     | Encoding                      | Example Value                           |
|----------|-------------------------------|-----------------------------------------|
| String   | `string`                      | `"Hello, world"`                        |
| Boolean  | `boolean`                     | `true`                                  |
| Int      | `int`                         | `42`                                    |
| Long     | `long`                        | `9223372036854775807`                   |
| Float    | `float`                       | `3.14`                                  |
| Double   | `double`                      | `3.141592653589793`                     |
| Enum     | `enum{value1, value2, ...}`   | `"value1"`                              |
| List     | `list<E>`                     | `["item1", "item2"]` for `list<string>` |
| Object   | `object{K1: E1, K2: E2, ...}` | `{"name": "John", "age": 30}`           |
| Nullable | `E?`                          | `null` or a value of the specified type |

