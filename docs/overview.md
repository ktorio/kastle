# ♖ Kastle System Overview ♖

In this document, you'll find a high-level overview of the KASTLE system and how it works.

## About

The goal of the KASTLE project is to provide a general means for algorithmic project generation for Kotlin programs. 
This is achieved with a custom file templating engine and an extensible module system.

## Architecture

There are three main elements of the Kastle system:

1. The pack repository
2. The project generator
3. The server

## The Pack Repository

The pack repository exposes the means for reading the modules that make up a new project.

The interface allows for ID listing and lookup:

```kotlin
interface PackRepository {
    fun ids(): Flow<PackId>
    suspend fun get(packId: PackId): PackDescriptor?
}
```

Note that we use asynchronous functions and types for seamless integration with local and remote sources.

We currently have a few implementations depending on your requirements:

1. [**Local:**](/local/src/LocalPackRepository.kt)
   This implementation resolves manually authored files, including unprocessed metadata and templates.
   Directly reading this type of repository requires access to the Kotlin compiler, so it is only available for the JVM.
   You can, however, use compiled templates across all platforms for project generation.
2. [**Remote:**](/client/src/RemoteRepository.kt)
   The client implementation for generating projects from a remote server.
   This assumes a schema consistent with the endpoints defined from the [server](#the-server) implementation.
3. [**Json:**](/core/src/io/JsonFilePackRepository.kt)
   A pre-compiled local repository implementation.
   You can generate these files using the `exportToJson()` extension function on any other repository implementation.

## The Project Generator

You can create a `ProjectGenerator` from any repository implementation listed above:

```kotlin
val generator = ProjectGenerator.fromRepository(repository)
```

The `ProjectGenerator` interface has a single function for creating a new project:

```kotlin
interface ProjectGenerator {
    fun generate(projectDescriptor: ProjectDescriptor): Flow<SourceFileEntry>
}
```

The `ProjectDescriptor` includes a list of desired modules, and all relevant settings for generation.

The resulting project is returned as a flow of files.
The `SourceFileEntry` is a simple class with a relative `path` and a `content` function that returns the file contents as a `kotlinx.io.Buffer`.
The platform provides easy conversion between the `Flow<SourceFileEntry>` and ZIP archives.  Currently, these are blocking operations. 

## The Server

The KASTLE server exposes endpoints to provide a remote repository when using the `RemotePackRepository`.
It also includes functions for generating projects on the server itself, and a convenient web interface for testing.

In this section, we'll describe the different server functions and endpoints in more detail.

### Repository Endpoints



### Generation Endpoints

### Web Interface

### Agent API