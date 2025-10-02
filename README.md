# ♖ KASTLE ♖

_**K**otlin **A**pplication **S**ourcecode **T**emplating and **L**ayout **E**ngine_

## Project structure

| Module                          | Description                                                    |
|---------------------------------|----------------------------------------------------------------|
| [core](kastle-core)             | Domain types for the pack repository and the templating engine |
| [templates](kastle-templates)   | Interfaces for compiling Kotlin source templates               |
| [local](kastle-local)           | Human-readable repository, designed for export                 |
| [server](kastle-server)         | The HTTP server for building projects from various clients     |
| [client](kastle-client)         | For making calls to the server from IDE's, websites, etc.      |
| [repository](kastle-repository) | Contains all sample PACKs for creating new projects.           |

## Documentation

- [How it works](docs/overview.md)
- [Publishing guide](docs/publishing.md)
- [Template DSL Reference](docs/dsl.md)

## Building & Running

This project uses Gradle as a build system.

You can start the server using:

```
./gradlew :kastle-server:run
```

You should see the following in the console:
```
 INFO  Application - Responding at http://0.0.0.0:2626
```

Visit the URL in a browser to start creating your projects!