rootProject.name = _project.name

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        for (repository in _project.gradle.repositories) {
            _unsafe("${repository.gradleFunction}()")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        for (repository in _project.gradle.repositories) {
            _unsafe("${repository.gradleFunction}()")
        }
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