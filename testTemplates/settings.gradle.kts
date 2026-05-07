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
    // Substitute the published Kastle Gradle plugin with the local module from the root project.
    // This allows working on the plugin and testTemplates together without publishing snapshots.
    //
    // When this build is *included* by the root `kastle` build (via `includeBuild("testTemplates")`
    // in the root `settings.gradle.kts`), the parent already provides the local
    // `kastle-gradle-plugin`, so we must NOT call `includeBuild("..")` — that would create a
    // circular composite and fail with "Expected vintage state ... transitioning to SettingsLoaded".
    // Detect that case by checking whether this build has a parent (i.e. it is itself an included
    // build) and skip the substitution.
    if (gradle.parent == null) {
        includeBuild("..")
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
        create("ktorLibs").apply {
            from("io.ktor:ktor-version-catalog:3.4.1")
        }
    }
}
