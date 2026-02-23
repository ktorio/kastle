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
        maven("https://packages.jetbrains.team/maven/p/apl/product-analytics-platform-public")
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
    "kastle-analytics-fus",
)

// Sample repository,
//   Requires plugin to be published.  Uncomment for editing
includeBuild("repository")
