package org.jetbrains.kastle.gen

import kotlinx.io.files.Path
import org.jetbrains.kastle.*
import org.jetbrains.kastle.io.resolve
import org.jetbrains.kastle.structure.BuildToolModules
import org.jetbrains.kastle.utils.Stack.Companion.toStack
import org.jetbrains.kastle.utils.Variables
import org.jetbrains.kastle.utils.isSlot
import org.jetbrains.kastle.utils.normalize
import org.jetbrains.kastle.utils.relativeFile
import org.jetbrains.kastle.utils.wrapQuotes
import kotlin.collections.buildMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.flatten
import kotlin.collections.map
import kotlin.collections.plus
import kotlin.collections.set

data class Project(
    val descriptor: ProjectDescriptor,
    val packs: List<PackDescriptor>,
    val properties: Map<PropertyScope, Map<VariableId, PropertyInstance>>,
    val slotSources: SourcesByUrl,
    val moduleSources: ProjectModules,
    val commonSources: List<SourceFile>,
    val versions: Map<String, String>,
    val libraries: Map<String, CatalogArtifact>,
    val gradle: GradleProjectSettings,
    val packaging: PackagingStyle,
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
fun Project.resolvedVariables(
    pack: PackDescriptor,
    modulePath: String?,
): Variables {
    fun Map<VariableId, PropertyInstance>.toMap(): Map<String, Any?> =
        entries.mapNotNull { (variableId, propertyInstance) ->
            if (propertyInstance !is ResolvedProperty) return@mapNotNull null
            variableId.relativeString(pack.id) to propertyInstance.value
        }.toMap()

    val rootScope = properties[PropertyScope.Pack]?.toMap()
    val moduleScope = modulePath?.let {
        properties[PropertyScope.Module(modulePath)]?.toMap()
    }
    return listOfNotNull(rootScope, moduleScope).toStack()
}

// TODO relativize dynamic variableIds
fun Project.dynamicVariables(
    modulePath: String?,
    variables: Variables,
): Map<String, Any?> {
    val resolved = mutableMapOf<String, Any?>()
    val dynamicProperties = listOfNotNull(
        properties[PropertyScope.Pack]?.entries,
        modulePath?.let { properties[PropertyScope.Module(modulePath)] }?.entries,
    ).flatten().mapNotNull { (_, propertyInstance) ->
        if (propertyInstance !is DynamicProperty) return@mapNotNull null
        propertyInstance
    }.toMutableList()
    var evaluationFailed = false

    fun DynamicProperty.evaluate(assignment: PropertyAssignment, type: PropertyType = descriptor.type): Any? =
        when(assignment) {
            is ValueAssignment -> type.parse(assignment.value)
            is ExpressionAssignment -> type.cast(assignment.expression.evaluate(variables + resolved))
        }

    // to allow resolution of other values in the current map,
    // keep trying to resolve properties until no progress is made
    while (dynamicProperties.isNotEmpty()) {
        val initialSize = dynamicProperties.size
        val iterator = dynamicProperties.listIterator()
        while (iterator.hasNext()) {
            val property = iterator.next()
            val evalResult = try {
                if (property.descriptor.type.isList()) {
                    property.assignments.map {
                        property.evaluate(it, property.descriptor.type.elementType!!)
                    }
                } else {
                    property.evaluate(
                        property.assignments.singleOrNull()
                            ?: error("Multiple values supplied for property ${property.descriptor.key}")
                    )
                }
            } catch (e: Exception) {
                if (evaluationFailed) throw e
                continue
            }
            resolved[property.descriptor.key] = evalResult
            iterator.remove()
        }
        evaluationFailed = initialSize == dynamicProperties.size
    }

    return resolved
}

context(project: Project)
fun SourceModule.toVariableEntry(): Pair<String, Any?> =
    "_module" to toVariableMap()

context(project: Project)
fun SourceModule.slotsVariableEntry(packId: PackId): Pair<String, Any?> =
    "_slots" to buildMap {
        for ((url, value) in project.slotSources + this@slotsVariableEntry.slotSources) {
            // insert relative value
            if (packId.toString() in url)
                put(url.relativeFile.removePrefix(packId.toString()).trimStart('/'), value)
            // insert absolute value
            put(url, value)
        }
    }

// TODO support string templates
val SourceModule.slotSources: SourcesByUrl get() =
    sources
        .filter { it.isSlot() }
        .groupBy { it.target.toString() }

context(project: Project)
private fun SourceModule.toVariableMap(): Map<String, Any?> = mapOf(
    "path" to path,
    "parent" to path.substringBeforeLast('/').takeIf { it.isNotEmpty() },
    "type" to if (mainClass() != null && platforms.size == 1)
        "${platforms.single()}/app"
    else "lib",
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

/**
 * Find the main class from either amper settings, gradle properties, or use the default main.kt file.
 */
context(project: Project)
fun SourceModule.mainClass(): String? {
    amper.application?.mainClass?.let { amperMain ->
        return amperMain
    }
    val moduleProperties = project.properties[PropertyScope.Module(originalPath)]
        ?: return null
    val mainClassGradlePropertyKey = VariableId(BuildToolModules.GRADLE_PACK_ID, "mainClass")
    val mainClassProperty = moduleProperties[mainClassGradlePropertyKey] as? ResolvedProperty
    mainClassProperty?.let {
        return it.value as? String
    }
    sources.asSequence()
        .map { it.target.toString().removePrefix("file:") }
        .firstOrNull { it.endsWith("main.kt") }
        ?.let { return it.replace('/', '.').replace("main.kt", "MainKt") }
    return null
}

context(project: Project)
fun Dependency.toVariableMap(modulePath: String) =
    when(this) {
        is ArtifactDependency -> mapOf(
            "type" to "maven",
            "group" to group,
            "artifact" to artifact,
            "version" to version,
            "exported" to exported,
            "isJava" to isJavaLibrary(artifact),
        )
        is ModuleDependency -> mapOf(
            "type" to "project",
            "path" to path,
            "gradlePath" to gradlePath(modulePath),
            "exported" to exported,
        )
        is CatalogReference -> mapOf(
            "type" to "catalog",
            "key" to key,
            "exported" to exported,
            "isJava" to isJavaLibrary(key),
        ) + project.libraries[tomlKey]?.toVariableMap().orEmpty()

        is FunctionDependency -> mapOf(
            "type" to "function",
            "functionName" to functionName,
            // TODO should use expressions
            "args" to args.map { it.wrapQuotes() },
            "exported" to exported,
        )
    }

private fun ModuleDependency.gradlePath(modulePath: String): String = buildString {
    val actualPath = Path(modulePath).resolve(path).normalize()
    append(':')
    append(actualPath.toString().replace('/', ':'))
}

fun GradleSettings.toVariableMap() = mapOf(
    "plugins" to plugins.map { it.key }
)

context(project: Project)
fun GradleProjectSettings.toVariableMap() = mapOf(
    "repositories" to repositories.map {
        it.toVariableMap()
    },
    "pluginRepositories" to pluginRepositories.map {
        it.toVariableMap()
    },
    "plugins" to plugins.map {
        mapOf(
            "id" to it.id,
            "name" to it.name,
            "catalogKey" to it.catalogKey,
        ) + it.version.toVariableMap()
    },
)

fun MavenRepository.toVariableMap() = mapOf(
    "id" to id,
    "url" to url,
    "gradleFunction" to gradleFunction,
)

fun AmperSettings.toVariableMap(): Map<String, String?> = mapOf(
    "compose" to compose,
    "ktor" to ktor,
).filterValues {
    it != null
}

context(project: Project)
fun CatalogArtifact.toVariableMap() = mapOf(
    "module" to module,
    "group" to group,
    "artifact" to name,
) + version.toVariableMap()

context(project: Project)
fun CatalogVersion.toVariableMap(): Map<String, String?> =
    when(this) {
        is CatalogVersion.Ref -> mapOf(
            "version" to project.versions[ref],
            "versionRef" to ref
        )
        is CatalogVersion.Number -> mapOf("version" to number)
    }

// TODO small hack for working with maven
private val javaLibraries = listOf(
    "logback",
    "prometheus",
    "h2",
    "mongodb",
)
private fun isJavaLibrary(library: String): Boolean =
    javaLibraries.any { it in library }
