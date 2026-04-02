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
            from("io.ktor:ktor-version-catalog:3.4.2")
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

// Sample repository,
//   Requires plugin to be published.  Uncomment for editing
//includeBuild("repository")
