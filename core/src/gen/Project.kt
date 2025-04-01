package org.jetbrains.kastle.gen

import org.jetbrains.kastle.ArtifactDependency
import org.jetbrains.kastle.Dependency
import org.jetbrains.kastle.GradlePlugin
import org.jetbrains.kastle.MissingPackException
import org.jetbrains.kastle.ModuleDependency
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.ProjectDescriptor
import org.jetbrains.kastle.ProjectModules
import org.jetbrains.kastle.SourceModule
import org.jetbrains.kastle.SourceTemplate
import org.jetbrains.kastle.Url
import org.jetbrains.kastle.VariableId
import org.jetbrains.kastle.sources
import org.jetbrains.kastle.utils.Variables
import org.jetbrains.kastle.utils.protocol

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
        ?: ProjectModules.Empty
    val slotSources: Map<Url, List<SourceTemplate>> = packs.asSequence()
        .flatMap { it.sources }
        .filter { it.target.protocol == "slot" }
        .groupBy { it.target }
    val commonSourceFiles = packs
        .flatMap { it.sources }
        .filter { it.target.protocol == "file" }
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
        moduleSources = moduleSources,
        commonSources = commonSourceFiles,
    )
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
    )

// TODO let's leverage serializers here
fun SourceModule.toVariableEntry(): Pair<String, Any?> =
    "_module" to mapOf(
        "path" to path.toString(),
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
            "version" to version.toString(),
        )
        is ModuleDependency -> mapOf(
            "module" to module
        )
    }

fun GradlePlugin.toVariableMap() = mapOf(
    "name" to id.split(".").last(), // TODO name
    "id" to id,
    "version" to version.toString(),
)