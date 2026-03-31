# ♖ KASTLE ♖

_**K**otlin **A**pplication **S**ourcecode **T**emplating and **L**ayout **E**ngine_

## Project structure

| Module                               | Description                                                    |
|--------------------------------------|----------------------------------------------------------------|
| [kastle-core](kastle-core)           | Domain types for the pack repository and the templating engine |
| [kastle-templates](kastle-templates) | Interfaces for compiling Kotlin source templates               |
| [kastle-local](kastle-local)         | Human-readable repository, designed for export                 |
| [kastle-server](kastle-server)       | The HTTP server for building projects from various clients     |
| [kastle-client](kastle-client)       | For making calls to the server from IDE's, websites, etc.      |
| [repository](repository)             | Contains all sample PACKs for creating new projects.           |

## Documentation

About:
- [How it works](docs/overview.md) <br />
  A general overview of how it all fits together.

Usage:
  - [Building Repositories](docs/repositories.md) <br />
    How you can make your own templates and combine them into a repository.
  - [Template DSL Reference](docs/dsl.md) <br />
    How to use the Kotlin DSL for creating templates

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
