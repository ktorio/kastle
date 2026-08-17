# AGENTS.md

This file provides guidance to AI coding assistants when working with code in this repository.

## What this project is

KASTLE is a **Kotlin Application Sourcecode Templating and Layout Engine**. It generates Kotlin source code from Kotlin source code — templates are valid Kotlin files that the engine parses using the Kotlin compiler itself. The key insight is that templates look like real Kotlin code with control flow (`if`, `when`, `for`) used to drive code inclusion/exclusion, rather than a string-interpolation language.

## Commands

```bash
# Run the test server (http://0.0.0.0:2626)
./gradlew :kastle-server:jvmRun

# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :kastle-local:test
./gradlew :kastle-core:jvmTest

# Run a single test class (testBalloon uses test suite names)
./gradlew :kastle-local:test --tests "LocalProjectGeneratorTest"

# Update test snapshots when generated output intentionally changes
UPDATE_GENERATOR_SNAPSHOTS=true ./gradlew :kastle-local:test
# Or with system property:
./gradlew :kastle-local:test -DUPDATE_GENERATOR_SNAPSHOTS

# Build everything
./gradlew build

# Export the test template repository to JSON or CBOR
./gradlew :testTemplates:kslExportToJson
./gradlew :testTemplates:kslExportToCbor
```

## Module architecture

| Module | Platforms | Role |
|--------|-----------|------|
| `kastle-templates` | KMP (all) | The template DSL stub — `_properties`, `_slots`, `_module`, `_project`, `_unsafe`. These are placeholders the compiler accepts but the engine replaces. |
| `kastle-core` | KMP (JVM, JS, WasmJS, iOS) | Domain types (`PackDescriptor`, `ProjectGenerator`, `PackRepository`), serialization, and `TemplateEvaluator`. |
| `kastle-local` | JVM only | Reads on-disk repositories; uses the Kotlin compiler (`KotlinCompilerTemplateEngine`) to parse and analyze `.kt` template files. Also has `HandlebarsTemplateEngine` for `.hbs` files. |
| `kastle-server` | KMP (JVM + JS) | Ktor HTTP server (JVM) + HTMX frontend (JS). The JS bundle is compiled and then copied into the JVM server's resources. |
| `kastle-client` | KMP (all) | Ktor HTTP client for calling the server REST API remotely. |
| `kastle-test` | KMP | Shared test suites (`testProjectGenerator`, `testPackRepository`) and snapshot assertion utilities. |
| `kastle-gradle-plugin` | JVM (Gradle) | The `org.jetbrains.kastle` settings plugin; registers pack directories as KMP modules and provides `kslExportToJson`, `kslExportToCbor`, `kslRunProject`, `kslRunServer` tasks. |
| `kastle-server-jib` | JVM | JIB-based Docker packaging for the server. |

## Core data flow

1. A **`PackRepository`** provides `PackDescriptor` objects. Implementations: `LocalPackRepository` (reads YAML manifests + Kotlin templates on disk), `RemotePackRepository` (HTTP client, in `kastle-client`), `JsonFilePackRepository` / `CborFilePackRepository` (pre-compiled exports, cross-platform).
2. **`ProjectGenerator`** takes a `ProjectDescriptor` (desired pack list + user properties) and resolves it through a chain of `ProjectResolver` steps (dependency flattening, module remapping, source mapping for Gradle/Toolchain/Maven). The result is a `Flow<SourceFileEntry>`.
3. **`TemplateEvaluator`** is called per source file to run each template through the engine and write the output bytes.

## Test infrastructure

Tests use the **testBalloon** framework (`de.infix.testBalloon`), not JUnit or standard Kotest. Test suites are declared with `testSuite { }` and test fixtures with `testFixture { }`. The shared suites in `kastle-test` are called from each module's own tests (e.g., `LocalProjectGeneratorTest` wires in a `LocalPackRepository` and delegates to `testProjectGenerator { }`).

**Snapshot tests**: `assertFilesAreEqualWithSnapshot` compares generated project output against checked-in snapshots in `testSnapshots/`. Versions, timestamps, and UUIDs are normalized before comparison. Set `UPDATE_GENERATOR_SNAPSHOTS=true` to regenerate them when output intentionally changes.

## Repository / template structure

The test template repository lives at `testTemplates/repository/`. Pack directories follow this layout:

```
<groupId>/<packId>/
  pack.ksl.yaml          # Pack manifest (name, requires, properties, sources, etc.)
  <module>/
    module.ksl.yaml      # Module manifest (platform, dependencies, gradle/amper config)
    src/                 # Sources for common target
    src@jvm/             # Sources for JVM target
    src@js/              # Sources for JS target (etc.)
```

Key manifest conventions:
- All Kotlin template files **must** declare `package kastle`. The engine replaces this with the user's configured package during generation.
- Only `.kt`, `.kts`, and `.hbs` files are templated. Everything else is copied verbatim.
- `pack.ksl.yaml` sources use `target: file:<path>` for output files or `target: slot://<group>/<pack>/<slot>` to contribute to another pack's slot.
- The `requires` field in a pack manifest links packs together and supports module remapping via the `key: src-module: dest-module` syntax.

## Server REST endpoints

- `GET /api/packIds` — list of all pack IDs
- `GET /api/packs` — list of packs without file contents
- `GET /api/packs/{group}/{id}` — full pack descriptor
- `POST /generate/preview` — generate project, return file listing as JSON
- `POST /generate/download` — generate project, return ZIP archive
- `GET /docs` — Swagger UI

## ABI validation

`kastle-core` and `kastle-client` have `abiValidation {}` enabled. After changing public API in those modules, run `./gradlew apiDump` to update the `.api` files before committing.

## Versioning and publishing

The version is `1.0.0-SNAPSHOT` unless built from a tag named `release-<version>`, in which case it strips the prefix. Published to `https://packages.jetbrains.team/maven/p/kastle/maven` via `SPACE_USERNAME` / `SPACE_PASSWORD` environment variables.
