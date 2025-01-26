# ♖ KASTLE DSL ♖

The template DSL provided for the KASTLE framework is designed for easy development of 
KASTLE modules.

## Glossary

Below is a list of terms used to describe features in the KASTLE framework.

| Term            | Description                                                                                           |
|-----------------|-------------------------------------------------------------------------------------------------------|
| module          | A selectable unit for project generation logic.  It includes build dependencies and source templates. |
| truthy / falsey | Lenient evaluation of a property value, similar to Javascript boolean casting.                        |

## Blocks

There are several constructs for customizing source templates from properties and interactions with 
other KASTLE modules.

You can find the details of all block types in the table below:

| Block   | Description                                                                                                                                                                             |
|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| __slot  | Another module may target this block for injecting its own customization.  This accepts a single injection, and will fail when more than one module targeting the same slot is selected |
| __slots | Same as the `__slot` block, but it accepts multiple injections, inserting each on a new line.                                                                                           |
| __value | Include the literal value of the property in your template.  This may not work for complex types                                                                                        |
| __if    | This block is included only when the property referenced is truthy                                                                                                                      |
| __else  | The fallback block for a preceding `__if`                                                                                                                                               |
| __each  | This block is included for every element in a referenced list property                                                                                                                  |
| __when  | Compares the provided property using different comparator blocks, where the first match is included and all others are ignored. See [When Comparisons](#when-comparisons)               |

## When Comparisons

| Block  | Description                                              |
|--------|----------------------------------------------------------|
| __each | Matches when the property is equal to the provided value |