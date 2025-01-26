plugins {
    __if("versionCatalogEnabled") {
        __each("gradlePluginIds") { item ->
            alias(__value(item))
        }
    }
    __else {
        __each("gradlePluginIds") { item ->
            id(__value(item))
        }
    }
}

group = __value("group")
version = "1.0.0-SNAPSHOT"

__slots("gradlePluginConfigurations")

repositories {
    __slots("gradleRepositories")
}

dependencies {
    __each("gradleDependencies") { item ->
        implementation(__value(item))
    }
    __each("gradleTestDependencies") { item ->
        testImplementation(__value(item))
    }
}