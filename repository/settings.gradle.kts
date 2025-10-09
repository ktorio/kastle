@file:Suppress("UnstableApiUsage")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        repositories {
            maven("https://packages.jetbrains.team/maven/p/kastle/maven")
        }
        mavenLocal()
    }
}

plugins {
    id("org.jetbrains.kastle") version "1.0.0-SNAPSHOT"
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        mavenLocal()
    }

    versionCatalogs {
        maybeCreate("libs").apply {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}