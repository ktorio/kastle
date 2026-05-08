# ♖ KASTLE ♖

_**K**otlin **A**pplication **S**ourcecode **T**emplating and **L**ayout **E**ngine_

## About

This project is an experimental tool for generating Kotlin source code from Kotlin source code.  The core concept is 
to leverage the Kotlin compiler and language features to create a powerful templating engine.  Since the templates are 
Kotlin sources themselves, we can provide a safe developer experience that is otherwise impossible with more traditional 
code generation tools.

You can find KASTLE applications in the following places:

- [KASTLE Test Server](https://ksl.labs.jb.gg) <br />
  Our HTMX test front-end is deployed from the `main` branch to a server for ad-hoc manual testing.
- [Ktor Project Generator](https://start.ktor.io) <br />
  This generator includes a custom web front-end and API used in IntelliJ IDEA. <br />
  The KASTLE repository is supplied from the [Ktor plugin registry](https://github.com/ktorio/ktor-plugin-registry).
- [Intellij Platform Plugin Generator](https://plugins.jetbrains.com/generator)
  This generator provides an easy way for developers to create new plugins for IntelliJ IDEA. <br />
  The back-end is deployed from a [branch of this repository](https://github.com/ktorio/kastle/tree/ide-plugin-wizard).

## Documentation

About:
- [Project overview](docs/overview.md) <br />
  A general overview of how it all fits together.

Usage:
  - [Building repositories](docs/repositories.md) <br />
    How you can make your own templates and combine them into a repository.
  - [Kotlin template DSL](docs/dsl.md) <br />
    How to use the Kotlin DSL for creating templates.
  - [Handlebars templating](docs/handlebars.md) <br />
    Using our custom handlebars template engine for non-Kotlin files.
  - [Properties](docs/properties.md) <br />
    For working with properties, declared in the manifests and referenced in templates.

## Project structure

| Module                               | Description                                                    |
|--------------------------------------|----------------------------------------------------------------|
| [kastle-core](kastle-core)           | Domain types for the pack repository and the templating engine |
| [kastle-templates](kastle-templates) | Interfaces for compiling Kotlin source templates               |
| [kastle-local](kastle-local)         | Human-readable repository, designed for export                 |
| [kastle-server](kastle-server)       | The HTTP server for building projects from various clients     |
| [kastle-client](kastle-client)       | For making calls to the server from IDE's, websites, etc.      |
| [testTemplates](testTemplates)       | Contains all sample PACKs for creating new projects.           |

## Using as a Library

To import KASTLE as a library, add the following to your `build.gradle.kts` file:

```kotlin
// build.gradle.kts
repositories {
    maven("https://packages.jetbrains.team/maven/p/kastle/maven")
}
dependencies {
    implementation("org.jetbrains:kastle-core:0.1.0")
}
```

Or, if you're creating a custom template repository, use the Gradle plugin:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven("https://packages.jetbrains.team/maven/p/kastle/maven")
    }
}

plugins {
    id("org.jetbrains.kastle") version "0.1.0"
}
```


## Building & Running

This project uses Gradle as a build system.

You can start the server using:

```
./gradlew :kastle-server:jvmRun
```

You should see the following in the console:
```
 INFO  Application - Responding at http://0.0.0.0:2626
```

Visit the URL in a browser to start creating your projects!
