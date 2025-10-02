package org.jetbrains.kastle

import java.nio.file.Paths

fun PackId.toProjectRef(module: String) =
    "$this/$module".formatRefString()

fun ModuleDependency.toProjectRef(modulePath: String) =
    Paths.get(modulePath).resolve(path).normalize().toString().formatRefString()

private fun String.formatRefString(): String =
    ":ksl-" + split(Regex("\\W+")).drop(1).joinToString("-").trimEnd('-')