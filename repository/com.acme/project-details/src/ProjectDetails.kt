package kastle

import kastle.unaryPlus

fun main() {
    +"Project ${_project.group}:${_project.name}"
    for (module in _project.modules) {

        if (module.path.isEmpty()) {
            +  "Root module"
        } else {
            +  "Module ${module.path}"
        }
        for (platform in module.dependencies.entries) {
            +    "Dependencies (${platform.key}):"
            for (dependency in platform.value) {
                when(dependency.type) {
                    "maven" -> {
                       +        "${dependency.group}:${dependency.artifact}:${dependency.version}"
                    }
                    "project" -> {
                        +        "${dependency.path}"
                    }
                    "catalog" -> {
                        +        "${dependency.key}"
                    }
                }
            }
        }
        if (module.gradle.plugins.isNotEmpty()) {
            +    "Gradle Plugins:"
            for (gradlePlugin in module.gradle.plugins) {
                +      gradlePlugin
            }
        }
    }
}

private operator fun String.unaryPlus() {
    println(this)
}