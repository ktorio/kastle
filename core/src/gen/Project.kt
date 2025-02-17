package org.jetbrains.kastle.gen

import org.jetbrains.kastle.ArtifactDependency
import org.jetbrains.kastle.BuildSystemDependency
import org.jetbrains.kastle.Dependency
import org.jetbrains.kastle.MissingPackException
import org.jetbrains.kastle.ModuleDependency
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.ProjectDescriptor
import org.jetbrains.kastle.ProjectStructure
import org.jetbrains.kastle.PropertyType
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
    val structure: ProjectStructure,
    val slotSources: Map<Url, List<SourceTemplate>>,
    val properties: Map<VariableId, Any?>,
) {
    val name: String get() = descriptor.name
    val group: String get() = descriptor.group
}

/**
 * Fetch all pack descriptors and extract relevant information for templating.
 */
suspend fun ProjectDescriptor.load(repository: PackRepository): Project {
    val packs = packs.map { repository.get(it) ?: throw MissingPackException(it) }
    val structure = packs.asSequence()
        .map { it.structure }
        .reduceOrNull(ProjectStructure::plus)
        ?: ProjectStructure.Empty
    val slotSources: Map<Url, List<SourceTemplate>> = packs.asSequence()
        .flatMap { it.sources }
        .filter { it.target.protocol == "slot" }
        .groupBy { it.target }
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
        structure = structure,
        slotSources = slotSources,
        properties = properties,
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

fun SourceModule.toVariableEntry(): Pair<String, Any?> =
    "Module" to mapOf(
        "dependencies" to dependencies.map { it.toVariableMap() },
        "testDependencies" to testDependencies.map { it.toVariableMap() },
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