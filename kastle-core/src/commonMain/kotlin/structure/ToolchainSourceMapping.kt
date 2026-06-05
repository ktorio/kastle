package org.jetbrains.kastle.structure

import org.jetbrains.kastle.CatalogReference
import org.jetbrains.kastle.Dependency
import org.jetbrains.kastle.FunctionDependency
import org.jetbrains.kastle.SourceModule
import org.jetbrains.kastle.gen.ProjectMapping
import org.jetbrains.kastle.gradlePlugins
import kotlin.collections.mapValues

@Deprecated("use ToolchainSourceMapping", ReplaceWith("ToolchainSourceMapping"))
val AmperSourceMapping get() = ToolchainSourceMapping

val ToolchainSourceMapping = ProjectMapping { project ->
    if (project.packs.none { it.id == BuildToolModules.TOOLCHAIN_PACK_ID })
        return@ProjectMapping project

    for (module in project.moduleSources.modules) {
        // we assume that when amper is configured, then gradle plugins won't cause trouble
        if (module.toolchain.isNotEmpty())
            continue
        require(module.gradlePlugins.isEmpty()) {
            "Project has plugins that require the Gradle build system"
        }
        require(module.dependencies.values.flatten().none { it is FunctionDependency }) {
            "Project has dependencies not supported by Kotlin Toolchain"
        }
    }

    // Amper has a built-in ktor catalog, so we replace "ktorLibs" with "ktor" in references
    project.copy(
        moduleSources = project.moduleSources.map { module ->
            module.mapDependencies { dependency ->
                when (dependency) {
                    is CatalogReference -> dependency.copy(
                        key = mapKtorLibraryReference(dependency.key)
                    )
                    else -> dependency
                }
            }
        }
    )
}

private fun SourceModule.mapDependencies(mapping: (Dependency) -> Dependency) =
    copy(manifest = manifest.copy(
        dependencies = dependencies.mapValues { (_, dependencies) ->
            dependencies.map(mapping).toSet()
        },
        testDependencies = testDependencies.mapValues { (_, dependencies) ->
            dependencies.map(mapping).toSet()
        },
    ))

private fun mapKtorLibraryReference(key: String): String {
    if (!key.startsWith("ktorLibs")) return key
    val result = when(val library = key.removePrefix("ktorLibs.")) {
        "server.config.yaml" -> "server.configYaml"
        "websockets.serialization" -> "websocket.serialization"
        else -> library
    }
    return "ktor.$result"
}
