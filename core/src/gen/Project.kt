package org.jetbrains.kastle.gen

import org.jetbrains.kastle.ArtifactDependency
import org.jetbrains.kastle.CatalogReference
import org.jetbrains.kastle.Dependency
import org.jetbrains.kastle.GradlePlugin
import org.jetbrains.kastle.MissingPackException
import org.jetbrains.kastle.ModuleDependency
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.ProjectDescriptor
import org.jetbrains.kastle.ProjectModules
import org.jetbrains.kastle.ProjectModules.Empty.modules
import org.jetbrains.kastle.SourceModule
import org.jetbrains.kastle.SourceTemplate
import org.jetbrains.kastle.Url
import org.jetbrains.kastle.VariableId
import org.jetbrains.kastle.flatten
import org.jetbrains.kastle.plus
import org.jetbrains.kastle.allSources
import org.jetbrains.kastle.utils.Variables
import org.jetbrains.kastle.utils.isFile
import org.jetbrains.kastle.utils.isSlot
import org.jetbrains.kastle.utils.protocol
import kotlin.sequences.filter

class Project(
    val descriptor: ProjectDescriptor,
    val packs: List<PackDescriptor>,
    val properties: Map<VariableId, Any?>,
    val slotSources: Map<Url, List<SourceTemplate>>,
    val moduleSources: ProjectModules,
    val commonSources: List<SourceTemplate>,
) {
    val name: String get() = descriptor.name
    val group: String get() = descriptor.group
}

/**
 * Fetch all pack descriptors and extract relevant information for templating.
 */
suspend fun ProjectDescriptor.load(repository: PackRepository): Project {
    val packs = packs.map { repository.get(it) ?: throw MissingPackException(it) }
    val moduleSources = packs.asSequence()
        .map { it.modules }
        .reduceOrNull(ProjectModules::plus)
        ?.flatten() ?: ProjectModules.Empty
    val slotSources: Map<Url, List<SourceTemplate>> = packs.asSequence()
        .flatMap { it.allSources }
        .filter { it.isSlot() }
        .groupBy { it.target }
    val commonSourceFiles = packs
        .flatMap { it.commonSources }
        .filter { it.isFile() }
    val rootSourceFiles = packs
        .flatMap { it.rootSources }
        .filter { it.isFile() }
    val properties = packs.flatMap { pack ->
        pack.properties.map { property ->
            VariableId(pack.id, property.key).let { variableId ->
                variableId to (properties[variableId] ?: property.default)?.let(property.type::parse)
            }
        }
    }.toMap()

    // TODO validate structure, check for collisions, etc.

    return Project(
        descriptor = this,
        packs = packs,
        properties = properties,
        slotSources = slotSources,
        moduleSources = moduleSources + rootSourceFiles,
        commonSources = commonSourceFiles,
    )
}

// Add root sources
private operator fun ProjectModules.plus(rootSources: List<SourceTemplate>): ProjectModules =
    when (this) {
        is ProjectModules.Empty ->
            ProjectModules.Single(SourceModule(sources = rootSources, ignoreCommon = true))
        is ProjectModules.Single ->
            copy(module = module.copy(sources = module.sources + rootSources))
        is ProjectModules.Multi ->
            // TODO check for root
            copy(modules = modules + SourceModule(sources = rootSources, ignoreCommon = true))
    }

/**
 * Replace full variable ID keys with local variable names for referencing from template.
 */
fun Project.getVariables(pack: PackDescriptor): Variables {
    return Variables.of(
        properties.mapKeys { (variableId) ->
            when(variableId.packId) {
                pack.id -> variableId.name
                else -> variableId.toString()
            }
        }
    )
}

fun Project.toVariableEntry(): Pair<String, Any?> =
    "_project" to mapOf(
        "name" to name,
        "group" to group,
        "modules" to moduleSources.modules.map { it.toVariableMap() },
    )

// TODO let's leverage serializers here
fun SourceModule.toVariableEntry(): Pair<String, Any?> =
    "_module" to toVariableMap()

private fun SourceModule.toVariableMap(): Map<String, Any> = mapOf(
    "path" to path,
    "type" to type.toString(),
    "platforms" to platforms,
    "dependencies" to dependencies.map { it.toVariableMap() },
    "testDependencies" to testDependencies.map { it.toVariableMap() },
    "gradlePlugins" to gradlePlugins.map { it.toVariableMap() },
)

fun Dependency.toVariableMap() =
    when(this) {
        is ArtifactDependency -> mapOf(
            "group" to group,
            "artifact" to artifact,
            "version" to version,
        )
        is ModuleDependency -> mapOf(
            "module" to module
        )
        // TODO find artifact from catalog
        is CatalogReference -> mapOf(
            "key" to key
        )
    }

fun GradlePlugin.toVariableMap() = mapOf(
    "name" to name,
    "id" to id,
    "version" to version.toString(),
)