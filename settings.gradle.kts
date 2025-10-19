rootProject.name = "kastle"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(
    "kastle-core",
    "kastle-test",
    "kastle-client",
    "kastle-server",
    "kastle-local",
    "kastle-templates",
    "kastle-gradle-plugin",
)