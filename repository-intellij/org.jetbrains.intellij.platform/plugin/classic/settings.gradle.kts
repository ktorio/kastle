import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = _project.name

pluginManagement {
    plugins {
        for (plugin in _project.gradle.plugins) {
            id(plugin.id) version plugin.version
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    val intellijPlatformGradlePluginVersion: String by _properties
    id("org.jetbrains.intellij.platform.settings") version intellijPlatformGradlePluginVersion
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    // Configure all projects' repositories
    repositories {
        mavenCentral()

        // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
        intellijPlatform {
            defaultRepositories()
        }
    }
}
