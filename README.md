# ♖ KASTLE ♖

_**K**otlin **A**ll-Purpose **S**ourcecode **T**emplating and **L**ayout **E**ngine_

### Project structure

| Module                 | Description                                                    |
|------------------------|----------------------------------------------------------------|
| [core](core)           | Domain types for the pack repository and the templating engine |
| [templates](templates) | Interfaces for compiling Kotlin source templates               |
| [local](local)         | Human-readable repository, designed for export                 |
| [server](server)       | The HTTP server for building projects from various clients     |
| [client](client)       | For making calls to the server from IDE's, websites, etc.      |

### Documentation

- [How it works](docs/overview.md)
- [Publishing guide](docs/publishing.md)
- [DSL Reference](docs/dsl.md)