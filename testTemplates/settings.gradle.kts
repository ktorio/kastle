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
        mavenLocal()
    }
    // Substitute the published `org.jetbrains.kastle` Gradle plugin with the
    // local `kastle-gradle-plugin` module from the parent `kastle` build, so
    // this build never depends on the plugin being published to mavenLocal
    // or the plugin portal. Including the parent build also pulls in
    // `kastle-core`, `kastle-local`, and `kastle-server` (the plugin's
    // runtime dependencies) via composite-build dependency substitution.
    includeBuild("..")
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
        create("ktorLibs").apply {
            from("io.ktor:ktor-version-catalog:3.4.1")
        }
    }
}

rootProject.name = "testTemplates"
