package org.jetbrains.kastle

import java.nio.file.Paths

fun PackId.toProjectRef(module: String) =
    "$this/$module".formatRefString()

fun ModuleDependency.toProjectRef(modulePath: String) =
    Paths.get(modulePath).resolve(path).normalize().toString().formatRefString()

private fun String.formatRefString(): String =
    ":ksl-" + split(Regex("\\W+")).drop(1).joinToString("-").trimEnd('-')

val SourceModuleMetadata.buildStrategy: ModuleBuildStrategy get() =
    when {
        "androidApplication" in gradlePlugins.map { it.removePrefix($$"$libs.") } -> ModuleBuildStrategy.ANDROID_APP
        platforms.singleOrNull() == Platform.JVM -> ModuleBuildStrategy.JVM
        else -> ModuleBuildStrategy.KMP
    }

enum class ModuleBuildStrategy {
    ANDROID_APP,
    JVM,
    KMP
}
