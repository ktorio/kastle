val versionCatalogEnabled: Boolean by _properties
val groupName: String by _properties
val gradlePluginIds: List<String> by _properties

plugins {
    if (versionCatalogEnabled) {
        for (item in gradlePluginIds) {
            alias(item)
        }
    } else {
        for (item in gradlePluginIds) {
            id(item)
        }
    }
}

group = groupName
version = "1.0.0-SNAPSHOT"

_slots("gradleConfigurations")

repositories {
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