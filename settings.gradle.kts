rootProject.name = "kastle"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        google()
    }
    versionCatalogs {
        create("ktorLibs") {
            from("io.ktor:ktor-version-catalog:3.5.0")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(
    "kastle-core",
    "kastle-test",
    "kastle-client",
    "kastle-local",
    "kastle-templates",
    "kastle-gradle-plugin",
    "kastle-server",
    "kastle-server-jib",
)

// Due to certain limitations in gradle, including testTemplates
// in this gradle project for IDE features is not possible.
// To modify the test templates, it's best to open the testTemplates
// folder as a separate project in Intellij.
