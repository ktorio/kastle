// Use version catalog
val versionCatalogEnabled: Boolean by _properties

plugins {
    for (plugin in _project.gradle.plugins) {
        alias(_unsafe("libs.plugins.${plugin.name}")) apply false
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