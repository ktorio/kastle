# ♖ Kastle-flavoured Handlebars ♖

For templating non-Kotlin files, we use [Handlebars](https://handlebarsjs.com/).

But since we're compiling to a common template data model, we've implemented our own version of Handlebars in Kotlin.  There are a few differences.  In this document we'll cover them.

## Comments

You can inject comments using `{{! comment }}`.  These are ignored in the resulting template.

## Property literals

Embed any value in your template using ``{{ property }}``.

For example:
```handlebars
{{! compiles to "Hello, World!" when name = "World" }}
Hello, {{ name }}!
```

## Conditionals

We've implemented the `if` and `else` built-in helpers for conditional rendering.

For example:
```handlebars
{{! compiles to either "Hello!" or "Goodby" depending on whether optionalProperty is truthy or not. }}
{{#if optionalProperty}}Hello!{{else}}Goodbye!{{/if}}
```
#### Unless

For the negation of an `if` statement, use `unless`.

## When

If you have a property that can be one of a few values, you can use `when` to render different content depending on the value.

For example:
```handlebars
{{! polite salutations are used for "Bob" and "Joe", but others are insulted }}
{{#when name}}{{"Bob"}}Hi{{"Joe"}}Hello{{else}}Up yours{{/when}}, {{name}}!
```

## Each

For iterating over a collection, use `each`.

For example:
```handlebars
{{!compiles to:
- Alice
- Bob
- Charlie}}
{{#each names}}
- {{this}}
{{/each}}
```

When inside an `each` block, the fields of the element are included in the variable scope.  To reference the element itself, use `this`.

## Slots

You can inject content into templates using slots as you would in the Kotlin engine.

For example:
```handlebars
{{! the content of mySlot will be injected here. }}
Content: {{slot mySlot}}
```

If you have more than one copy of a slot, just use `slots` and they'll be concatenated into the template.

## Escaping

If you need the literal value of `{{` in your template, you can escape it with `\{{`.
