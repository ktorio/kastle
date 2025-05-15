// Use version catalog
val versionCatalogEnabled: Boolean by _properties

plugins {
    for (module in _project.modules) {
        if (versionCatalogEnabled) {
            for (item in module.gradle.plugins) {
                alias(_unsafe("libs.plugins.${item.name}")) apply false
            }
        } else {
            for (item in module.gradle.plugins) {
                id(item.id) apply false
            }
        }
    }
}

subprojects {
    group = _project.group
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        _slots("gradleRepositories")
    }
}