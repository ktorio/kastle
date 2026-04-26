val versionCatalogEnabled: Boolean by _properties

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    if (_project.modules.any { it.platform == "jvm" }) {
        alias(libs.plugins.kotlinJvm) apply false
    }
    for (plugin in _project.gradle.plugins) {
        alias(_unsafe("${plugin.name}")) apply false
    }
}

subprojects {
    group = _project.group
    version = "1.0.0-SNAPSHOT"
}
