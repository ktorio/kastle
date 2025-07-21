rootProject.name = _project.name

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        _slots("gradlePluginRepositories")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        _slots("gradleRepositories")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    _slots("gradleSettingsPlugins")
}

for (module in _project.modules) {
    if (module.path.isNotEmpty()) {
        include(":${module.path.replace('/', ':')}")
    }
}