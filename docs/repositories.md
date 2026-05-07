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

### Connecting Packs

### Modules

### Sources

### Properties


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
