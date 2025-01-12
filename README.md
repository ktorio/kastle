# ♜♖ Kotlin All-Purpose Sourcecode Templating and Layout Engine ♖♜

Project structure:

| Module                               | Description                                                       |
|--------------------------------------|-------------------------------------------------------------------|
| [core](core)                         | Domain types for the feature repository and the templating engine |
| [export/templates](export/templates) | Interfaces for compiling Kotlin source templates                  |
| [export/api](export/api)             | Domain types for extracting feature descriptors from repositories |
| [export/impl](export/impl)           | Implementation of the export API using the Kotlin compiler        |
| [server](server)                     | The HTTP server for building projects from various clients        |
| [client](client)                     | For making calls to the server from IDE's, websites, etc.         |
