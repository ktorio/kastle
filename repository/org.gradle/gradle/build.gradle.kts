if (_module.gradle.plugins.isNotEmpty()) {
    plugins {
        for (item in _module.gradle.plugins) {
            alias(_unsafe("libs.plugins.${item.name}"))
        }
    }
}

if (_project.modules.size == 1) {
    group = _project.group
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        _slots("gradleRepositories")
    }
}

_slots("gradleConfigurations")

if (_module.platforms.size() > 1) {
    kotlin {
        sourceSets {
            for (e in _module.dependencies.entries) {
                if (e.value.isNotEmpty()) {
                    _unsafe("${e.key}Main").dependencies {
                        for (dependency in e.value) {
                            when (dependency.type) {
                                "maven" -> {
                                    implementation("${dependency.group}:${dependency.artifact}:${dependency.version}")
                                }
                                "project" -> {
                                    implementation(project(dependency.gradlePath))
                                }
                                "catalog" -> {
                                    implementation(_unsafe("${dependency.key}"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
} else {
    dependencies {
        for (dependency in _module.dependencies.values.flatten()) {
            when (dependency.type) {
                "maven" -> {
                    implementation("${dependency.group}:${dependency.artifact}:${dependency.version}")
                }
                "project" -> {
                    implementation(project(dependency.gradlePath))
                }
                "catalog" -> {
                    implementation(_unsafe("${dependency.key}"))
                }
            }
        }

        for (dependency in _module.testDependencies) {
            when (dependency.type) {
                "maven" -> {
                    testImplementation("${dependency.group}:${dependency.artifact}:${dependency.version}")
                }
                "project" -> {
                    testImplementation(project(dependency.gradlePath))
                }
                "catalog" -> {
                    testImplementation(_unsafe("${dependency.key}"))
                }
            }
        }
    }
}