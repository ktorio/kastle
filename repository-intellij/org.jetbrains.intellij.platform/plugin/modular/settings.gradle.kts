@file:Suppress("UnstableApiUsage")

import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/")
    }
    plugins {
        id("rpc") version "2.3.20-RC2-0.1"
        id("org.jetbrains.kotlin.jvm") version "2.3.20"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20"
        for (plugin in _project.gradle.plugins) {
        id(plugin.id) version plugin.version
        }
    }
}

plugins {
    val intellijPlatformGradlePluginVersion: String by _properties
    id("org.jetbrains.intellij.platform.settings") version intellijPlatformGradlePluginVersion
}

val pluginModuleName: String by _properties
rootProject.name = pluginModuleName

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}

include("shared")
include("frontend")
include("backend")
