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
