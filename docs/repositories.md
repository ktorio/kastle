# ♖ Building Repositories ♖

This document should help you get started with crafting your own template repositories.

Each repository can be exported and combined with others for backing different custom project generators.

## 1. File Structure

A repository is any directory with the following layout:

```
repository
├─ settings.gradle.kts?
├─ <groupId>
│  ├─ <packId>
│  │   ├─ pack.ksl.yaml
│  │   ├─ <module>?
│  │   │  ├─ module.ksl.yaml
│  │   │  ├─ src
│  │   │  ├─ src@<target>
│  │   │  ├─ resources
│  │   │  └─ ...
│  │   └─ ...
├  ├─ ...
└─ ...
```

The best way to familiarize yourself with this is to look at the [test repository](/testTemplates) in this project.

You'll notice above that the packs are laid out similar to an Amper project, using `src` and `src@jvm`, etc. to keep things easier to navigate.  

Important notes:
 - All Kotlin sources in the root of the project MUST declare `package kastle`.  This both puts the sources in the same package as the template DSL, and it provides an easy token for Kastle to replace with the configured package in the resulting project.
 - Only `.kt`, `.kts`, and `.hbs` files will be templated.  Kotlin files will use the [Kotlin templating DSL](dsl.md) and HBS files will use Handlebars.

There are two manifests that provide all the metadata for every pack:
 - `pack.ksl.yaml` <br />
   This contains top-level details of the pack.  It includes properties, description, and much more.  You can find the serializable class is here: [PackManfiest](/kastle-core/src/commonMain/kotlin/PackTypes.kt).
 - `module.ksl.yaml` <br />
   This contains details for a particular source module.  This contains source dependencies, module-specific files, etc.

You can find the relevant JSON schema files in the [schema](/schema) directory.

We'll go into greater details for each of these manifests and how to work with them.

## 2. Pack Details and Manifests

### General Information

The `pack.ksl.yaml` manifest describes a single pack. Most fields are optional, but `name` is required. The pack's `id` is derived from the directory layout (`<groupId>/<packId>`), so it usually shouldn't be set in the manifest.

```yaml
name: Ktor Server                  # required, human-readable name
version: 1.0.0                     # semantic version, defaults to 1.0.0
description: Adds a Ktor HTTP server
license: Apache-2.0
icon: icon.svg                     # path relative to the pack directory
tags:
  - core
  - server
links:
  vcs: https://github.com/example/repo
  home: https://example.com
  docs: https://example.com/docs
```

A long-form description can be provided by placing a `README.md` file next to `pack.ksl.yaml`. It will be picked up automatically and assigned to the `documentation` field.

The group icon, name, and other common fields can be shared across all packs in a group by adding a `group.ksl.yaml` (or `group.yaml`) file in the group directory.

### Connecting Packs

A pack can declare dependencies on other packs through the `requires` list. Each entry is either a plain `group/id` string, or a single-key object that maps modules of the required pack to modules of the current pack.

```yaml
requires:
  # simple form: depend on the entire pack
  - com.acme/parent

  # maps its root module to "server"
  - io.ktor/server-core: server

  # bind multiple modules explicitly (path-to-path map)
  - io.ktor/server-core:
      server: backend
      client: frontend
```

When a pack requires another pack, its slots become available, its properties are inherited, and any sources it contributes are added to the generated project.

### Modules

Modules live in subdirectories of the pack and are detected automatically by the presence of a `module.ksl.yaml` file. The directory layout follows Amper conventions (`src`, `src@<target>`, `resources`, `test`, `testResources`, `res` for Android).

A minimal `module.ksl.yaml` simply declares the target platform:

```yaml
platform: jvm
```

For multiplatform modules, use `platforms` instead:

```yaml
platforms:
  - jvm
  - js
  - wasmJs
```

Other supported sections:

```yaml
dependencies:
  - $ktorLibs.server.core           # version catalog reference
  - $libs.logback.classic
testDependencies:
  - $ktorLibs.server.testHost

# Build-system specific configuration
gradle:
  plugins:
    - ktorLibs.plugins.ktor
amper:
  ktor: enabled
  application:
    mainClass: com.example.MainKt
```

Dependencies prefixed with `$` are resolved from a version catalog. The default catalog is `gradle/libs.versions.toml`, and an additional `repository.versions.toml` may be defined alongside the manifests for repository-wide entries. Other `*.versions.toml` files in the repository root are exposed as external catalogs (e.g. `ktorLibs.versions.toml` becomes `$ktorLibs`).

When a module declares multiple platforms, the `dependencies` and `testDependencies` keys can be suffixed with `@<platform>` to scope them. For example, `dependencies@jvm`.

### Sources

Files placed under `src`, `src@<target>`, `resources`, `test`, `testResources` (and `res` for Android) are included automatically. Files with `.kt`, `.kts`, or `.hbs` extensions are templated; everything else is copied verbatim.

Sources that aren't part of the standard module layout - or that need explicit targeting and conditions - are declared in the `sources` list of `module.ksl.yaml`:

```yaml
sources:
  # copy a templated file, choosing the target by a property
  - path: application.conf.hbs
    target: file:resources/application.conf
    if: configFormat == "HOCON"

  # contribute to a slot defined by another pack
  - target: slot://org.gradle/gradle/buildRoot
    path: build.gradle.kts.hbs

  # inline content - useful for short snippets
  - target: slot://org.jetbrains/amper/settings
    text: "ktor: enabled"

  # include every file under a directory using a wildcard
  - path: extra/*

  # control merge order when several sources target the same file/slot
  - path: src/Routing.kt
    priority: 10
```

Each source supports the following keys:

| Key        | Description                                                                                                              |
|------------|--------------------------------------------------------------------------------------------------------------------------|
| `path`     | Path to the source file (relative to the module). Use `dir/*` to include all files in a directory.                       |
| `text`     | Inline file content (use instead of `path`). Requires `target`.                                                          |
| `target`   | Destination URL: `file:<relative path>` for a file, `slot://<group>/<pack>/<slot>` for contributing to another pack's slot. |
| `if`       | Kotlin expression - the source is included only when it evaluates to `true`.                                              |
| `priority` | Integer used to break ties when several sources target the same destination. Higher wins.                                 |

The pack manifest itself supports two additional source lists:

- `commonSources` - templates that are emitted into every module of the generated project.
- `rootSources` - templates that are emitted at the project root (e.g. `settings.gradle.kts` snippets, version catalog entries).

```yaml
rootSources:
  - target: slot://org.gradle/gradle/versionCatalogs
    text: create("ktorLibs").from("io.ktor:ktor-version-catalog:3.4.0")
  - target: file://README.md
    path: README.md.hbs
```

### Properties

Properties are typed values that drive template logic. They are declared in the pack manifest under `properties`:

```yaml
properties:
  - key: configFormat
    type: enum { HOCON, YAML, none }
    default: HOCON

  - key: serverModules
    type: list<string>?
    hidden: true

  - key: hasHttp
    type: boolean
    hidden: true
```

Each property descriptor accepts:

| Key       | Description                                                                                          |
|-----------|------------------------------------------------------------------------------------------------------|
| `key`     | Identifier used to reference the property in templates and expressions.                              |
| `type`    | Property type - `string`, `boolean`, `int`, `enum { A, B, C }`, `list<...>`. Append `?` to allow null. |
| `default` | Default value when nothing is provided by the user.                                                  |
| `hidden`  | When `true`, the property is computed internally and not surfaced in the UI.                         |

Values can be assigned (or overridden) through `propertyValues`, either at the pack level (`pack.ksl.yaml`) or per-module (`module.ksl.yaml`). Each entry must specify `key` together with either a literal `value` or a Kotlin `expression`:

```yaml
propertyValues:
  # static value
  - key: configFormat
    value: YAML

  # expression evaluated against the project context
  - key: hasHttp
    expression: _slots.contains("http")

  - key: features
    expression: _project.packs.filter { !it.tags.contains("core") }

  - key: serverTarget
    expression: if (_project.modules.any { it.path == "server" }) ":server:" else ""
```

Inside expressions you can reference:

- Other properties of the same pack by their `key`.
- Properties of another pack with the qualified form `group/id/key`.
- Built-in objects such as `_project`, `_slots`, and the current module context.

Expressions are evaluated lazily, so a property may depend on other dynamic properties as long as there are no cycles.



## 3. As a Kotlin Project

Each repository can be compiled as a Kotlin project.  This allows you to use the compiler to validate your Kotlin template files.

To implement this, add a `settings.gradle.kts` with the Kastle plugin:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        // Include the Kastle maven repository for referencing the plugin
        maven("https://packages.jetbrains.team/maven/p/kastle/maven")
    }
}

// Kastle plugin goes here
plugins {
    id("org.jetbrains.kastle") version "1.0.0-SNAPSHOT"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Also include it here to reference the template DSL
        maven("https://packages.jetbrains.team/maven/p/kastle/maven")
    }
    
    versionCatalogs {
        // Include version catalogs here
    }
}
```

This will use the Kastle API to read all metadata from your manifests in the expected directory structure and register 
the relevant Gradle modules with Kotlin multiplatform tooling.

With Gradle support, you can:
 - Validate that your templates compile
 - Export the repository using the `kslExportToJson` or `kslExportToCbor` targets
 - Build a test project using `kslRunProject` task. The options for this will be read from a project descriptor file (default, `test.yaml`)
 - Start up a test server with an interactive UI, using `kslRunServer` task.

## 4. Calling it from Kotlin

In the Kastle API, your repository is called a [LocalPackRepository](/kastle-local/src/main/kotlin/LocalPackRepository.kt).

You can get an instance by calling the constructor:

```kotlin
val myRepository = LocalPackRepository(Path("../my-repository/"))
```

This has the same interface as any other `PackRepository` implementation, but it will compile your templates as they're referenced.  If performance is a concern, it's best to export it to a compressed file with the `export` extension function and load it as a `CborFilePackRepository`.

## 4. Publishing to a central repository

This feature has not yet been implemented.  In the future, there will be a Gradle target like `kslPublish` that will send your repository to a remote server so that we can distribute development among trusted parties.
