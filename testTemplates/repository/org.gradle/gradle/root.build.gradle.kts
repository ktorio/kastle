val versionCatalogEnabled: Boolean by _properties

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    for (plugin in _project.gradle.plugins) {
        alias(_unsafe("${plugin.name}")) apply false
    }
}
