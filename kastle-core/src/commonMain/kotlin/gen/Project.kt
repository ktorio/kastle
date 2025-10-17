package org.jetbrains.kastle.gen

import kotlinx.io.files.Path
import org.jetbrains.kastle.*
import org.jetbrains.kastle.io.resolve
import org.jetbrains.kastle.utils.Variables
import org.jetbrains.kastle.utils.normalize
import kotlin.collections.map

data class Project(
    val descriptor: ProjectDescriptor,
    val packs: List<PackDescriptor>,
    val properties: Map<VariableId, Any?>,
    val slotSources: Map<Url, List<SourceFile>>,
    val moduleSources: ProjectModules,
    val commonSources: List<SourceFile>,
    val versions: Map<String, String>,
    val libraries: Map<String, CatalogArtifact>,
    val gradle: GradleProjectSettings,
) {
    val name: String get() = descriptor.name
    val group: String get() = descriptor.group
}

// TODO make smarter, maybe use serialization
fun Project.toVariableEntry(): Pair<String, Any?> =
    "_project" to mapOf(
        "name" to name,
        "group" to group,
        "modules" to moduleSources.modules.sortedBy { it.path }.map { it.toVariableMap() },
        "versions" to versions,
        "libraries" to libraries.mapValues { (_, value) -> value.toVariableMap() },
        "gradle" to gradle.toVariableMap(),
    )

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
    "_module" to toVariableMap()

private fun SourceModule.toVariableMap(): Map<String, Any?> = mapOf(
    "path" to path,
    "type" to if (amper.application != null && platforms.size == 1) "${platforms.single()}/app" else "lib",
    "platform" to platforms.singleOrNull()?.code,
    "platforms" to platforms.map { it.code },
    "dependencies" to dependencies.asSequence()
        .filter { it.value.isNotEmpty() }
        .associate { (platform, deps) ->
            platform.code to deps.map { it.toVariableMap(path) }
        },
    "testDependencies" to testDependencies.asSequence()
        .associate { (platform, deps) ->
            platform.code to deps.map { it.toVariableMap(path) }
        },
    "gradle" to gradle.toVariableMap(),
    "amper" to amper.toVariableMap(),
)

fun Dependency.toVariableMap(modulePath: String) =
    when(this) {
        is ArtifactDependency -> mapOf(
            "type" to "maven",
            "group" to group,
            "artifact" to artifact,
            "version" to version,
            "exported" to exported,
        )
        is ModuleDependency -> mapOf(
            "type" to "project",
            "path" to path,
            "gradlePath" to gradlePath(modulePath),
            "exported" to exported,
        )
        // TODO find artifact from catalog?
        is CatalogReference -> mapOf(
            "type" to "catalog",
            "key" to key,
            "exported" to exported,
        )
    }

private fun ModuleDependency.gradlePath(modulePath: String): String = buildString {
    val actualPath = Path(modulePath).resolve(path).normalize()
    append(':')
    append(actualPath.toString().replace('/', ':'))
}

fun GradleSettings.toVariableMap() = mapOf(
    "plugins" to plugins
)

fun GradleProjectSettings.toVariableMap() = mapOf(
    "repositories" to repositories.map {
        it.toVariableMap()
    },
    "plugins" to plugins.map {
        mapOf(
            "id" to it.id,
            "name" to it.name,
            "version" to it.version.toVariableMap()
        )
    },
)

fun MavenRepository.toVariableMap() = mapOf(
    "id" to id,
    "url" to url,
    "gradleFunction" to gradleFunction,
)

fun AmperSettings.toVariableMap(): Map<String, String?> = mapOf(
    "compose" to compose
).filterValues {
    it != null
}

fun CatalogArtifact.toVariableMap() = mapOf(
    "module" to module,
    "group" to group,
    "artifact" to artifact,
    "version" to version.toVariableMap(),
)

fun CatalogVersion.toVariableMap(): Map<String, String?> = when(this) {
    is CatalogVersion.Ref -> mapOf("ref" to ref)
    is CatalogVersion.Number -> mapOf("number" to number)
}