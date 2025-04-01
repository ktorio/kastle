val versionCatalogEnabled: Boolean by _properties

plugins {
    if (versionCatalogEnabled) {
        for (item in _module.gradlePlugins) {
            alias("libs.plugins.${item.name}".unsafe())
        }
    } else {
        for (item in _module.gradlePlugins) {
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
            implementation("libs.${dependency.artifact}".unsafe())
        }
        for (dependency in _module.testDependencies) {
            testImplementation("libs.${dependency.artifact}".unsafe())
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