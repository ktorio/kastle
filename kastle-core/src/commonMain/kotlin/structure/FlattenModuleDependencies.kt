package org.jetbrains.kastle.structure

import kotlinx.io.files.Path
import org.jetbrains.kastle.Dependency
import org.jetbrains.kastle.DependenciesMap
import org.jetbrains.kastle.ModuleDependency
import org.jetbrains.kastle.Platform
import org.jetbrains.kastle.ProjectModules
import org.jetbrains.kastle.SourceModule
import org.jetbrains.kastle.gen.ProjectMapping
import org.jetbrains.kastle.utils.normalize

/**
 * Merges library modules that have exactly one other module depending on them into the dependent.
 *
 * A module is considered for flattening when:
 * - At least one other module references it via a [ModuleDependency], and
 * - It is referenced by exactly one such dependent module.
 *
 * When merging:
 * - The library module's sources are moved into the dependent.
 * - The library module's manifest (dependencies, test dependencies, gradle and amper settings,
 *   plugins) is merged into the dependent's manifest.
 * - The [ModuleDependency] reference is removed from the dependent.
 * - Any [ModuleDependency] paths in the merged module are rewritten so they remain valid
 *   relative to the dependent's path.
 */
val FlattenModuleDependencies = ProjectMapping { project ->
    val modules = project.moduleSources.modules
    if (modules.size < 2) return@ProjectMapping project

    val byAbsolutePath: Map<String, SourceModule> = modules.associateBy { it.path }

    // Build a map: absolute path of referenced module -> list of dependents.
    val dependentsOf: Map<String, List<SourceModule>> = buildMap<String, MutableList<SourceModule>> {
        for (module in modules) {
            for ((_, deps) in module.dependencies) {
                for (dep in deps) {
                    if (dep !is ModuleDependency) continue
                    val absolutePath = resolveModuleDependencyPath(module.path, dep.path)
                    if (absolutePath !in byAbsolutePath) continue
                    getOrPut(absolutePath, ::mutableListOf).add(module)
                }
            }
        }
    }

    // Identify lib modules that have exactly one dependent.
    val mergeInto: Map<String, SourceModule> = dependentsOf
        .filter { (libPath, dependents) -> dependents.size == 1 && libPath != dependents.single().path }
        .mapValues { (_, dependents) -> dependents.single() }
    if (mergeInto.isEmpty()) return@ProjectMapping project

    // Compute final merge targets, following chains so that A -> B -> C all merge into C.
    val finalTarget = mutableMapOf<String, String>()
    fun resolveFinalTarget(libPath: String): String {
        val direct = mergeInto[libPath]?.path ?: return libPath
        return finalTarget.getOrPut(libPath) { resolveFinalTarget(direct) }
    }
    for (libPath in mergeInto.keys) resolveFinalTarget(libPath)

    val toMergeByTarget: Map<String, List<SourceModule>> = mergeInto.keys
        .groupBy { finalTarget.getValue(it) }
        .mapValues { (_, libPaths) -> libPaths.map(byAbsolutePath::getValue) }
    val mergedPaths: Set<String> = mergeInto.keys

    val resultModules = modules.mapNotNull { module ->
        if (module.path in mergedPaths) return@mapNotNull null

        val toMerge = toMergeByTarget[module.path].orEmpty()
        if (toMerge.isEmpty()) return@mapNotNull module

        toMerge.fold(module) { acc, lib -> mergeLibInto(acc, lib) }
    }

    project.copy(moduleSources = ProjectModules.fromList(resultModules))
}

private fun resolveModuleDependencyPath(modulePath: String, dependencyPath: String): String =
    Path(modulePath, dependencyPath).normalize().toString()

private fun mergeLibInto(dependent: SourceModule, lib: SourceModule): SourceModule {
    val libAbsolutePath = lib.path
    val dependentAbsolutePath = dependent.path

    // Remove the dependency reference from `dependent` that points at `lib`, and rewrite all
    // module dependencies coming from `lib` so they stay relative to `dependent`.
    val mergedDependencies = mergeDependencies(
        dependent.dependencies,
        lib.dependencies,
        dependentAbsolutePath,
        libAbsolutePath,
    )
    val mergedTestDependencies = mergeDependencies(
        dependent.testDependencies,
        lib.testDependencies,
        dependentAbsolutePath,
        libAbsolutePath,
    )

    val mergedManifest = dependent.manifest.copy(
        dependencies = mergedDependencies,
        testDependencies = mergedTestDependencies,
        platforms = dependent.platforms.ifEmpty { lib.platforms },
        gradle = dependent.gradle.copy(
            plugins = (dependent.gradle.plugins + lib.gradle.plugins).distinct(),
        ),
        toolchain = dependent.toolchain.copy(
            compose = dependent.toolchain.compose ?: lib.toolchain.compose,
            ktor = dependent.toolchain.ktor ?: lib.toolchain.ktor,
            application = dependent.toolchain.application ?: lib.toolchain.application,
            kotlin = dependent.toolchain.kotlin ?: lib.toolchain.kotlin,
        ),
    )

    return dependent.copy(
        manifest = mergedManifest,
        sources = dependent.sources + lib.sources,
        condition = dependent.condition ?: lib.condition,
    )
}

private fun mergeDependencies(
    dependent: DependenciesMap,
    lib: DependenciesMap,
    dependentAbsolutePath: String,
    libAbsolutePath: String,
): Map<Platform, Set<Dependency>> {
    val dependentWithoutLib = dependent.mapValues { (_, deps) ->
        deps.filterNot { dep ->
            dep is ModuleDependency &&
                    resolveModuleDependencyPath(dependentAbsolutePath, dep.path) == libAbsolutePath
        }.toSet()
    }
    val rewrittenLib = lib.mapValues { (_, deps) ->
        deps.mapNotNull { dep ->
            if (dep !is ModuleDependency) return@mapNotNull dep
            val absolute = resolveModuleDependencyPath(libAbsolutePath, dep.path)
            if (absolute == dependentAbsolutePath) return@mapNotNull null
            dep.copy(path = relativizePath(dependentAbsolutePath, absolute))
        }.toSet()
    }
    return (dependentWithoutLib.keys + rewrittenLib.keys).associateWith { platform ->
        dependentWithoutLib[platform].orEmpty() + rewrittenLib[platform].orEmpty()
    }
}

private fun relativizePath(fromAbsolute: String, toAbsolute: String): String {
    val fromSegments = fromAbsolute.split('/').filter { it.isNotEmpty() }
    val toSegments = toAbsolute.split('/').filter { it.isNotEmpty() }
    var common = 0
    while (common < fromSegments.size && common < toSegments.size && fromSegments[common] == toSegments[common]) {
        common++
    }
    val upSegments = List(fromSegments.size - common) { ".." }
    val downSegments = toSegments.drop(common)
    return (upSegments + downSegments).joinToString("/").ifEmpty { "." }
}
