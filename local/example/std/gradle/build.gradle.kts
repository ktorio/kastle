val versionCatalogEnabled: Boolean by _properties

plugins {
    if (versionCatalogEnabled) {
        for (item in _module.gradlePluginIds) {
            alias(item)
        }
    } else {
        for (item in _module.gradlePluginIds) {
            id(item)
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
            implementation(dependency.catalogReference)
        }
        for (dependency in _module.testDependencies) {
            testImplementation(dependency.catalogReference)
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