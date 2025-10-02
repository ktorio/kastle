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

include(
    "kastle-core",
    "kastle-test",
    "kastle-client",
    "kastle-server",
    "kastle-local",
    "kastle-templates",
    "kastle-gradle-plugin",
)