# ♖ KASTLE ♖

_**K**otlin **A**ll-Purpose **S**ourcecode **T**emplating and **L**ayout **E**ngine_

### Project structure

| Module                        | Description                                                       |
|-------------------------------|-------------------------------------------------------------------|
| [core](core)                  | Domain types for the feature repository and the templating engine |
| [templates](export/templates) | Interfaces for compiling Kotlin source templates                  |
| [local](export/local)         | Human-readable repository, designed for export                    |
| [server](server)              | The HTTP server for building projects from various clients        |
| [client](client)              | For making calls to the server from IDE's, websites, etc.         |

### Documentation

- [DSL Reference](docs/DSL.md)