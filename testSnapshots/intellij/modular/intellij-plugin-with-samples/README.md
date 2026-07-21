# Intellij-plugin-with-samples

[![Twitter Follow](https://img.shields.io/badge/follow-%40JBPlatform-1DA1F2?logo=twitter)](https://twitter.com/JBPlatform)
[![Developers Forum](https://img.shields.io/badge/JetBrains%20Platform-Join-blue)][jb:forum]

## Overview

This repository implements a modular IntelliJ Platform plugin.
It uses content modules as a unit of functionality that the plugin consists of.
Content modules are split into:
- `frontend` - UI code
- `backend` - stateful business logic
- `shared`

Frontend communicates with the backend through RPC.

This structure allows to:
- separate UI code from business logic
- implement features in a way they work natively in **[split mode][docs:remote-dev]** just like in the ordinary monolithic IDE
- keep the plugin code cleaner

## Demo Functionality

The sample plugin adds a `ModularPlugin` tool window with a chat-style UI implemented with the Swing framework.

## Plugin structure

A generated project contains the following content structure:

```
.
├── .run/                   Predefined Run/Debug Configurations
├── backend/                Backend module – business logic
│   ├── build.gradle.kts    Backend dependencies
│   └── src/main/
│       ├── kotlin/         Kotlin production sources
│       └── resources/      intellij.plugin.with.samples.backend.xml
├── frontend/               Frontend module – UI and presentation
│   ├── build.gradle.kts    Frontend dependencies
│   └── src/main/
│       ├── kotlin/         Kotlin production sources
│       └── resources/      intellij.plugin.with.samples.frontend.xml
├── shared/                 Shared module – cross-boundary contracts
│   ├── build.gradle.kts    Shared dependencies
│   └── src/main/
│       ├── kotlin/         Kotlin production sources
│       └── resources/      intellij.plugin.with.samples.shared.xml
├── gradle/
│   ├── wrapper/            Gradle Wrapper
│   └── libs.versions.toml  Version catalog
├── src
│   └── main
│       └── resources/
│           └── META-INF/   Plugin configuration file and logo
├── .gitignore              Git ignoring rules
├── build.gradle.kts        Root build – assembles the final plugin
├── gradle.properties       Gradle configuration properties
├── gradlew                 *nix Gradle Wrapper script
├── gradlew.bat             Windows Gradle Wrapper script
└── settings.gradle.kts     Gradle project settings
```

> [!NOTE]
> To use Java in your plugin, create the appropriate `/src/main/java` directory within the desired module.

The plugin logo is placed in `src/main/resources/META-INF/pluginIcon.svg`.
See [Plugin Logo][docs:logo] for more information and logo requirements.

### Module Layout

- `root project` assembles the final plugin, declares the main IntelliJ Platform dependency, enables split mode, and includes the `shared`, `frontend`, and `backend` plugin modules in the final distribution.
- `shared` contains contracts that both sides must understand: RPC interfaces, DTOs, serializers, and shared model types. Put a cross-boundary API here.
- `frontend` contains UI-only code and presentation logic: the tool window registration, Swing UI, view models, and the frontend adapter that talks to the backend via RPC.
- `backend` contains project-level services and business logic: access to project, file system, and external processes, message creation, response generation, and the RPC implementation exposed to the frontend.

## Build script

The root [build.gradle.kts][file:build.gradle.kts] assembles the final plugin and applies the following Gradle plugins:

| Plugin                             | Description                                                                      |
|------------------------------------|----------------------------------------------------------------------------------|
| `org.jetbrains.kotlin.jvm`         | Adds Kotlin support                                                              |
| `org.jetbrains.changelog`          | Simplifies patching the [CHANGELOG.md][file:CHANGELOG.md] file                   |
| `org.jetbrains.intellij.platform`  | The [IntelliJ Platform Gradle Plugin][docs:intellij-platform-gradle-plugin-docs] |

The `intellijPlatform` dependencies block selects the IDE to compile against:

```kotlin
intellijIdea("2025.3.5")
```

See [Target Versions][docs:target-version] for more information.

## Plugin configuration files

The root [plugin.xml][file:plugin.xml] file located in `src/main/resources/META-INF` provides general information about the plugin, its dependencies, and references the per-module plugin descriptors.

Each module ships its own plugin descriptor in its `src/main/resources/` directory:
- `intellij.plugin.with.samples.backend.xml` – registers backend extensions and services
- `intellij.plugin.with.samples.frontend.xml` – registers frontend extensions and tool windows
- `intellij.plugin.with.samples.shared.xml` – registers shared extensions and interfaces

You can read more about plugin configuration files in the [Plugin Configuration File][docs:plugin.xml] section of our documentation.

## Remote Development Ready Architecture

The demo is intentionally split so that the UI stays frontend-only and the business logic stays backend-only.
This ensures optimal UX in the remote development scenario where the IDE has separate frontend and backend processes.
This is what we call **Split Mode**.

A high-level overview of the plugin structure:
- a UI for a chat with an AI assistant natively rendered in the frontend IDE in split mode
- data transfer between the frontend and backend via RPC
- RPC implementation in the backend IDE is capable of touching any backend entities and APIs like a file system

A more detailed explanation of how it is implemented:
1. The frontend registers the tool window and creates `ChatViewModel`.
2. `ChatViewModel` depends on the frontend-facing `ChatRepositoryApi` abstraction instead of directly depending on backend services.
3. `FrontendChatRepositoryModel` implements that abstraction by calling the shared `ChatRepositoryRpcApi` and collecting the backend message `Flow`.
4. The shared module defines `ChatRepositoryRpcApi` plus the DTOs used to cross the RPC boundary.
5. The backend registers `BackendRpcApiProvider`, which exposes `BackendChatRepositoryRpcApi` as the RPC implementation.
6. `BackendChatRepositoryRpcApi` resolves the backend project from `ProjectId` and delegates to `BackendChatRepositoryModel`.
7. `BackendChatRepositoryModel` owns the mutable message list and the demo response generation logic.

This separation keeps the frontend focused on rendering, local UI state, and interaction handling, while the backend owns project-scoped state and logic that should execute on the backend side in split mode.

## Predefined Run/Debug configurations

Within the default project structure, there is a `.run` directory provided containing predefined *Run/Debug configurations* that expose corresponding Gradle tasks:

| Configuration name               | Description                                                                                                                                                      |
|----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Run IDE with Plugin (Frontend)   | Runs [`:runIdeFrontend`][docs:intellij-platform-gradle-plugin-runIde] IntelliJ Platform Gradle Plugin task. Use the *Debug* icon for plugin debugging.           |
| Run IDE with Plugin (Backend)    | Runs [`:runIdeBackend`][docs:intellij-platform-gradle-plugin-runIde] IntelliJ Platform Gradle Plugin task. Use the *Debug* icon for plugin debugging.            |
| Run IDE with Plugin (Split Mode) | Runs both *Run IDE (Backend)* and *Run IDE (Frontend)* configurations simultaneously to launch the plugin in split mode.                                         |

> [!NOTE]
> You can find the logs from the running task in the `idea.log` tab.

## Publishing the plugin

> [!TIP]
> Make sure to follow all guidelines listed in [Publishing a Plugin][docs:publishing] to follow all recommended and required steps.

Releasing a plugin to [JetBrains Marketplace](https://plugins.jetbrains.com) is a straightforward operation that uses the `publishPlugin` Gradle task provided by the [intellij-platform-gradle-plugin][gh:intellij-platform-gradle-plugin].

You can also upload the plugin to the [JetBrains Plugin Repository](https://plugins.jetbrains.com/plugin/upload) manually via UI.

## Useful links

- [IntelliJ Platform SDK Plugin SDK][docs]
- [IntelliJ Platform Gradle Plugin Documentation][docs:intellij-platform-gradle-plugin-docs]
- [IntelliJ Platform Explorer][jb:ipe]
- [JetBrains Marketplace Quality Guidelines][jb:quality-guidelines]
- [IntelliJ Platform UI Guidelines][jb:ui-guidelines]
- [JetBrains Marketplace Paid Plugins][jb:paid-plugins]
- [IntelliJ SDK Code Samples][gh:code-samples]
- [Remote Development / Split Mode][docs:remote-dev]

[docs]: https://plugins.jetbrains.com/docs/intellij
[docs:intro]: https://plugins.jetbrains.com/docs/intellij/intellij-platform.html?from=IJPluginReadmeFile
[docs:logo]: https://plugins.jetbrains.com/docs/intellij/plugin-icon-file.html?from=IJPluginReadmeFile
[docs:plugin.xml]: https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html?from=IJPluginReadmeFile
[docs:publishing]: https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginReadmeFile
[docs:remote-dev]: https://plugins.jetbrains.com/docs/intellij/plugin-content-modules.html?from=IJPluginReadmeFile
[docs:target-version]: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html?from=IJPluginReadmeFile#target-versions
[docs:intellij-platform-gradle-plugin-docs]: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html?from=IJPluginReadmeFile
[docs:intellij-platform-gradle-plugin-runIde]: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html?from=IJPluginReadmeFile#runIde
[docs:intellij-platform-gradle-plugin-verifyPlugin]: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html?from=IJPluginReadmeFile#verifyPlugin

[file:build.gradle.kts]: ./build.gradle.kts
[file:CHANGELOG.md]: ./CHANGELOG.md
[file:gradle.properties]: ./gradle.properties
[file:plugin.xml]: ./src/main/resources/META-INF/plugin.xml

[gh:code-samples]: https://github.com/JetBrains/intellij-sdk-code-samples
[gh:intellij-platform-gradle-plugin]: https://github.com/JetBrains/intellij-platform-gradle-plugin

[gradle:lifecycle-tasks]: https://docs.gradle.org/current/userguide/java_plugin.html#lifecycle_tasks

[jb:github]: https://github.com/JetBrains/.github/blob/main/profile/README.md
[jb:forum]: https://platform.jetbrains.com/
[jb:quality-guidelines]: https://plugins.jetbrains.com/docs/marketplace/quality-guidelines.html
[jb:paid-plugins]: https://plugins.jetbrains.com/docs/marketplace/paid-plugins-marketplace.html
[jb:ipe]: https://jb.gg/ipe
[jb:ui-guidelines]: https://jetbrains.github.io/ui
