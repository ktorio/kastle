val versionCatalogEnabled: Boolean by _properties

plugins {
    if (versionCatalogEnabled) {
        for (item in _module.gradle.plugins) {
            alias(_unsafe("libs.plugins.${item.name}"))
        }
    } else {
        for (item in _module.gradle.plugins) {
            id(item.id)
        }
    }
}

group = _project.group
version = "1.0.0-SNAPSHOT"

_slots("gradleConfigurations")

repositories {
    mavenCentral()
    _slots("gradleRepositories")
}

// TODO multiplatform
dependencies {
    if (versionCatalogEnabled) {
        for (dependency in _module.dependencies) {
            implementation(_unsafe("libs.${dependency.artifact.replace('-','.')}"))
        }
        for (dependency in _module.testDependencies) {
            testImplementation(_unsafe("libs.${dependency.artifact.replace('-','.')}"))
        }
    } else {
        for (dependency in _module.dependencies) {
            implementation("${dependency.group}:${dependency.artifact}:${dependency.version}")
        }
        for (dependency in _module.testDependencies) {
            testImplementation("${dependency.group}:${dependency.artifact}:${dependency.version}")
        }
    }
}