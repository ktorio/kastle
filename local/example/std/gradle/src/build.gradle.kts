val versionCatalogEnabled: Boolean by Project
val groupName: String by Project
val gradlePluginIds: List<String> by Module

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

Module.slots("gradleConfigurations")

repositories {
    Project.slots("gradleRepositories")
}

// TODO multiplatform
dependencies {
    if (versionCatalogEnabled) {
        for (dependency in Module.dependencies) {
            implementation(dependency.catalogReference)
        }

        for (dependency in Module.testDependencies) {
            testImplementation(dependency.catalogReference)
        }
    } else {
        for (dependency in Module.dependencies) {
            implementation("${dependency.group}:${dependency.artifact}:${dependency.version}}")
        }

        for (dependency in Module.testDependencies) {
            testImplementation("${dependency.group}:${dependency.artifact}:${dependency.version}}")
        }
    }
}