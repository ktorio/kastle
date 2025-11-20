val pluginName: String by _properties
rootProject.name = pluginName

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
