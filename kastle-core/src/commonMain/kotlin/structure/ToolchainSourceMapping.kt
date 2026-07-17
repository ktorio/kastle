package org.jetbrains.kastle.structure

import org.jetbrains.kastle.CatalogReference
import org.jetbrains.kastle.Dependency
import org.jetbrains.kastle.FunctionDependency
import org.jetbrains.kastle.SourceModule
import org.jetbrains.kastle.GradleSettings
import org.jetbrains.kastle.gen.ProjectMapping
import org.jetbrains.kastle.gradlePlugins
import kotlin.collections.mapValues

@Deprecated("use ToolchainSourceMapping", ReplaceWith("ToolchainSourceMapping"))
val AmperSourceMapping get() = ToolchainSourceMapping

private val TOOLCHAIN_HANDLED_PLUGINS: Set<String> = setOf(
    "kotlinMultiplatform",
    "kotlinJvm",
    "androidApplication",
    "androidMultiplatformLibrary",
    "composeMultiplatform",
    "composeCompiler",
)

val ToolchainSourceMapping = ProjectMapping { project ->
    if (project.packs.none { it.id == BuildToolModules.TOOLCHAIN_PACK_ID })
        return@ProjectMapping project

    for (module in project.moduleSources.modules) {
        // we assume that when the toolchain is configured, then gradle plugins won't cause trouble
        if (module.toolchain.isNotEmpty())
            continue
        val unhandledPlugins = module.gradlePlugins.filterNot {
            it.tomlKey in TOOLCHAIN_HANDLED_PLUGINS
        }
        require(unhandledPlugins.isEmpty()) {
            "Project has plugins that require the Gradle build system: " +
                    unhandledPlugins.joinToString { it.tomlKey }
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
    return "ktor.${key.removePrefix("ktorLibs.")}"
}
