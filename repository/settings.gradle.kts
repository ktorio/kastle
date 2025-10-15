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
//        mavenLocal()
        repositories {
            maven("https://packages.jetbrains.team/maven/p/kastle/maven")
        }
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
//        mavenLocal()
        repositories {
            maven("https://packages.jetbrains.team/maven/p/kastle/maven")
        }
    }

    versionCatalogs {
        create("libs").apply {
            from(files("../gradle/libs.versions.toml"))
        }
        create("ktor").apply {
            from("io.ktor:ktor-version-catalog:3.3.1")
        }
    }
}