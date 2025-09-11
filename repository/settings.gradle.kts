@file:Suppress("UnstableApiUsage")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }

    versionCatalogs {
        maybeCreate("libs").apply { from(files("../gradle/libs.versions.toml")) }
    }
}

include("packs")
includeBuild("..")