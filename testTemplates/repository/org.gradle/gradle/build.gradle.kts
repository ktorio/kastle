val isExecutable: Boolean by _properties

plugins {
    for (item in _module.gradle.plugins) {
        alias(_unsafe("${item}"))
    }
}

if (_project.modules.size == 1) {
    group = _project.group
    version = "1.0.0-SNAPSHOT"
}

if (_module.platform != "jvm" && _module.platform != "android") {
    kotlin {
        for (platform in _module.platforms) {
            when(platform) {
                "jvm" -> {
                    jvm()
                }
                "android" -> {}
                "ios" -> {
                    if (_slots.contains("iosOverride")) {
                        _slot("iosOverride")
                    } else {
                        iosArm64()
                        iosSimulatorArm64()
                    }
                }
                "js" -> {
                    js {
                        browser()
                        if (isExecutable) {
                            binaries.executable()
                        }
                    }
                }
                "wasmJs" -> {
                    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
                    wasmJs {
                        browser()
                        if (isExecutable) {
                            binaries.executable()
                        }
                    }
                }
                "web" -> {
                    js {
                        browser()
                        if (isExecutable) {
                            binaries.executable()
                        }
                    }
                    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
                    wasmJs {
                        browser()
                        if (isExecutable) {
                            binaries.executable()
                        }
                    }
                }
            }
        }

        _slots("kotlinRoot")

        sourceSets {
            for (e in _module.dependencies.entries) {
                if (e.value.isNotEmpty()) {
                    _unsafe("${e.key}Main").dependencies {
                        for (dependency in e.value) {
                            if (dependency.scope == "implementation" || dependency.scope == "api" || dependency.scope == "runtimeOnly" || dependency.scope == "compileOnly") {
                                when (dependency.type) {
                                    "maven" -> {
                                        _unsafe("${dependency.scope}")("${dependency.group}:${dependency.artifact}:${dependency.version}")
                                    }
                                    "project" -> {
                                        _unsafe("${dependency.scope}")(_unsafe("${dependency.typesafeProjectAccessor}"))
                                    }
                                    "catalog" -> {
                                        _unsafe("${dependency.scope}")(_unsafe("${dependency.key}"))
                                    }
                                    "function" -> {
                                        _unsafe("${dependency.scope}")(_unsafe("${dependency.functionName}(${dependency.args.joinToString()})"))
                                    }
                                    "reference" -> {
                                        _unsafe("${dependency.scope}")(_unsafe("${dependency.reference}"))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            for (e in _module.testDependencies.entries) {
                if (e.value.isNotEmpty()) {
                    _unsafe("${e.key}Test").dependencies {
                        for (dependency in e.value) {
                            if (dependency.scope == "implementation" || dependency.scope == "api" || dependency.scope == "runtimeOnly" || dependency.scope == "compileOnly") {
                                when (dependency.type) {
                                    "maven" -> {
                                        _unsafe("${dependency.scope}")("${dependency.group}:${dependency.artifact}:${dependency.version}")
                                    }
                                    "project" -> {
                                        _unsafe("${dependency.scope}")(_unsafe("${dependency.typesafeProjectAccessor}"))
                                    }
                                    "catalog" -> {
                                        _unsafe("${dependency.scope}")(_unsafe("${dependency.key}"))
                                    }
                                    "function" -> {
                                        _unsafe("${dependency.scope}")(_unsafe("${dependency.functionName}(${dependency.args.joinToString()})"))
                                    }
                                    "reference" -> {
                                        _unsafe("${dependency.scope}")(_unsafe("${dependency.reference}"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if ((_module.dependencies.values.flatten().any { dependency -> !(dependency.scope == "implementation" || dependency.scope == "api" || dependency.scope == "runtimeOnly" || dependency.scope == "compileOnly") }) || _module.testDependencies.values.flatten().any { dependency -> !(dependency.scope == "implementation" || dependency.scope == "api" || dependency.scope == "runtimeOnly" || dependency.scope == "compileOnly") }) {

        dependencies {
            for (dependency in _module.dependencies.values.flatten()) {
                if (!(dependency.scope == "implementation" || dependency.scope == "api" || dependency.scope == "runtimeOnly" || dependency.scope == "compileOnly")) {
                    when (dependency.type) {
                        "maven" -> {
                            _unsafe("${dependency.scope}")("${dependency.group}:${dependency.artifact}:${dependency.version}")
                        }
                        "project" -> {
                            _unsafe("${dependency.scope}")(_unsafe("${dependency.typesafeProjectAccessor}"))
                        }
                        "catalog" -> {
                            _unsafe("${dependency.scope}")(_unsafe("${dependency.key}"))
                        }
                        "reference" -> {
                            _unsafe("${dependency.scope}")(_unsafe("${dependency.reference}"))
                        }
                    }
                }
            }
            for (dependency in _module.testDependencies.values.flatten()) {
                if (!(dependency.scope == "implementation" || dependency.scope == "api" || dependency.scope == "runtimeOnly" || dependency.scope == "compileOnly")) {
                    when (dependency.type) {
                        "maven" -> {
                            _unsafe("test${dependency.scope.replaceFirstChar { c -> c.uppercaseChar() }}")("${dependency.group}:${dependency.artifact}:${dependency.version}")
                        }
                        "project" -> {
                            _unsafe("test${dependency.scope.replaceFirstChar { c -> c.uppercaseChar() }}")(_unsafe("${dependency.typesafeProjectAccessor}"))
                        }
                        "catalog" -> {
                            _unsafe("test${dependency.scope.replaceFirstChar { c -> c.uppercaseChar() }}")(_unsafe("${dependency.key}"))
                        }
                        "reference" -> {
                            _unsafe("test${dependency.scope.replaceFirstChar { c -> c.uppercaseChar() }}")(_unsafe("${dependency.reference}"))
                        }
                    }
                }
            }
        }
    }
} else {
    if (_slots.contains("kotlinRoot")) {
        kotlin {
            _slots("kotlinRoot")
        }
    }
    dependencies {
        for (dependency in _module.dependencies.values.flatten()) {
            when (dependency.type) {
                "maven" -> {
                    _unsafe("${dependency.scope}")("${dependency.group}:${dependency.artifact}:${dependency.version}")
                }
                "project" -> {
                    _unsafe("${dependency.scope}")(_unsafe("${dependency.typesafeProjectAccessor}"))
                }
                "catalog" -> {
                    _unsafe("${dependency.scope}")(_unsafe("${dependency.key}"))
                }
                "reference" -> {
                    _unsafe("${dependency.scope}")(_unsafe("${dependency.reference}"))
                }
            }
        }
        for (dependency in _module.testDependencies.values.flatten()) {
            when (dependency.type) {
                "maven" -> {
                    _unsafe("test${dependency.scope.replaceFirstChar { c -> c.uppercaseChar() }}")("${dependency.group}:${dependency.artifact}:${dependency.version}")
                }
                "project" -> {
                    _unsafe("test${dependency.scope.replaceFirstChar { c -> c.uppercaseChar() }}")(_unsafe("${dependency.typesafeProjectAccessor}"))
                }
                "catalog" -> {
                    _unsafe("test${dependency.scope.replaceFirstChar { c -> c.uppercaseChar() }}")(_unsafe("${dependency.key}"))
                }
                "reference" -> {
                    _unsafe("test${dependency.scope.replaceFirstChar { c -> c.uppercaseChar() }}")(_unsafe("${dependency.reference}"))
                }
            }
        }
    }
}

_slots("buildRoot")
