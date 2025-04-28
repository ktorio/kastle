val versionCatalogEnabled: Boolean by _properties

if (_module.gradle.plugins.isNotEmpty()) {
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
}

group = _project.group
version = "1.0.0-SNAPSHOT"

_slots("gradleConfigurations")

repositories {
    mavenCentral()
    _slots("gradleRepositories")
}

dependencies {
    for (dependency in _module.dependencies) {
        when(dependency.type) {
            "maven" ->   { implementation("${dependency.group}:${dependency.artifact}:${dependency.version}") }
            "project" -> { testImplementation(project(dependency.path)) }
            "catalog" -> { implementation(_unsafe("${dependency.key}")) }
        }
    }

    for (dependency in _module.testDependencies) {
        when(dependency.type) {
            "maven" ->   { testImplementation("${dependency.group}:${dependency.artifact}:${dependency.version}") }
            "project" -> { testImplementation(project(dependency.path)) }
            "catalog" -> { testImplementation(_unsafe("${dependency.key}")) }
        }
    }
}