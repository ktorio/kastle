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

// Include all modules from project.yaml
include(
    "core",
    "test",
    "client",
    "server",
    "local",
    "templates",
    // "tasks:bundle"
)