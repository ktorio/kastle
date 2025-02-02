val versionCatalogEnabled: Boolean by Template
val gradlePluginIds: List<String> by Template
val gradleDependencies: List<String> by Template
val gradleTestDependencies: List<String> by Template
val groupName: String by Template

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

Template.Slots("gradlePluginConfigurations")

repositories {
    Template.Slots("gradleRepositories")
}

dependencies {
    for (dependency in gradleDependencies) {
        implementation(dependency)
    }
    for (dependency in gradleTestDependencies) {
        testImplementation(dependency)
    }
}