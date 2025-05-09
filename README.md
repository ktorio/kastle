# ♖ KASTLE ♖

_**K**otlin **A**ll-Purpose **S**ourcecode **T**emplating and **L**ayout **E**ngine_

## Project structure

| Module                   | Description                                                    |
|--------------------------|----------------------------------------------------------------|
| [core](core)             | Domain types for the pack repository and the templating engine |
| [templates](templates)   | Interfaces for compiling Kotlin source templates               |
| [local](local)           | Human-readable repository, designed for export                 |
| [server](server)         | The HTTP server for building projects from various clients     |
| [client](client)         | For making calls to the server from IDE's, websites, etc.      |
| [repository](repository) | Contains all PACKs for creating new projects.                  |

## Documentation

- [How it works](docs/overview.md)
- [Publishing guide](docs/publishing.md)
- [Template DSL Reference](docs/dsl.md)

## Building

This project uses [Amper](https://github.com/JetBrains/amper) for building and as part of its inner workings.

## Running

This project is both a library and a standalone server.

To run the server:
 - Run `./amper task :server:import` to compile the current repository.
 - Run `./amper run --module server` to start the server

You should see the following in the console:
```
 INFO  Application - Responding at http://0.0.0.0:8080
```

Visit the URL in a browser to start creating your projects!