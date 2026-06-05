package org.jetbrains.kastle.gen

import kastle.TemplateToolchainApplicationSettings
import kastle.TemplateToolchainKotlinSettings
import kastle.TemplateBuildDependency
import kastle.TemplateCatalogArtifact
import kastle.TemplateGradleModuleSettings
import kastle.TemplateGradlePlugin
import kastle.TemplateGradleProjectSettings
import kastle.TemplateMavenRepository
import kastle.TemplatePack
import kastle.TemplateProject
import kastle.TemplateSourceModule
import kastle.TemplateToolchainModuleSettings
import kotlinx.io.files.Path
import org.jetbrains.kastle.*
import org.jetbrains.kastle.io.resolve
import org.jetbrains.kastle.structure.BuildToolModules
import org.jetbrains.kastle.utils.encodeToMap
import org.jetbrains.kastle.utils.isSlot
import org.jetbrains.kastle.utils.normalize
import org.jetbrains.kastle.utils.relativeFile
import org.jetbrains.kastle.utils.wrapQuotes
import kotlin.collections.buildMap
import kotlin.text.contains

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

internal fun Project.asTemplateMap(): Map<String, Any?> =
    TemplateProject(
        name = name,
        group = group,
        modules = moduleSources.modules.sortedBy { it.path }.map { it.toTemplateType() },
        versions = versions,
        buildSystem = packs.firstOrNull {
            it.tags.contains("build-system")
        }?.id?.toString(),
        libraries = libraries.mapValues { (_, value) -> value.toTemplateType() },
        gradle = gradle.toTemplateType(),
        packs = packs.map {
            TemplatePack(
                id = it.id.toString(),
                name = it.name,
                group = it.group?.id,
                description = it.description,
                tags = it.tags,
            )
        },
    ).encodeToMap()

context(project: Project)
internal fun SourceModule.asTemplateMap(): Map<String, Any?> =
    toTemplateType().encodeToMap()

context(project: Project)
private fun SourceModule.toTemplateType(): TemplateSourceModule =
    TemplateSourceModule(
        path = path,
        type = if (mainClass() != null && platforms.size == 1) "${platforms.single()}/app" else "lib",
        platforms = platforms.map { it.code },
        gradle = gradle.toTemplateType(),
        toolchain = toolchain.toTemplateType(),
        dependencies = dependencies.asSequence()
            .filter { it.value.isNotEmpty() }
            .sortedBy { it.key.code }
            .associate { (platform, deps) ->
                platform.code to deps.map {
                    it.toTemplateType(path)
                }.sortedWith(
                    compareBy(
                        { it.scope },
                        { it.type },
                        { it.typesafeProjectAccessor ?: LAST_IN_ORDER },
                        { it.gradlePath ?: LAST_IN_ORDER },
                        { it.path ?: LAST_IN_ORDER },
                        { it.reference ?: LAST_IN_ORDER },
                        { it.functionName ?: LAST_IN_ORDER },
                        { it.key ?: LAST_IN_ORDER },
                        { it.group ?: LAST_IN_ORDER },
                        { it.artifact ?: LAST_IN_ORDER },
                    )
                )
            },
        testDependencies = testDependencies.asSequence()
            .filter { it.value.isNotEmpty() }
            .sortedBy { it.key.code }
            .associate { (platform, deps) ->
                platform.code to deps.map { it.toTemplateType(path) }
            },
    )

private const val LAST_IN_ORDER: String = "~"

context(project: Project)
private fun CatalogArtifact.toTemplateType(): TemplateCatalogArtifact =
    TemplateCatalogArtifact(
        module = module,
        version = lookupVersion(version),
        versionRef = (version as? CatalogVersion.Ref)?.ref,
        group = group,
        artifact = name,
        kmp = isKmp(group, name),
    )

context(project: Project)
private fun lookupVersion(version: CatalogVersion): String? =
    when (version) {
        is CatalogVersion.Ref -> project.versions[version.ref]
        is CatalogVersion.Number -> version.number
    }

context(project: Project)
private fun GradleProjectSettings.toTemplateType(): TemplateGradleProjectSettings =
    TemplateGradleProjectSettings(
        repositories = repositories.map { it.toTemplateType() },
        pluginRepositories = pluginRepositories.map { it.toTemplateType() },
        plugins = plugins.map { it.toTemplateType() },
    )

private fun MavenRepository.toTemplateType(): TemplateMavenRepository =
    TemplateMavenRepository(
        id = id,
        url = url,
        gradleFunction = gradleFunction,
    )

context(project: Project)
private fun GradlePlugin.toTemplateType(): TemplateGradlePlugin =
    TemplateGradlePlugin(
        id = id,
        name = name,
        catalogKey = catalogKey,
        version = lookupVersion(version),
        versionRef = (version as? CatalogVersion.Ref)?.ref,
    )

private fun GradleSettings.toTemplateType(): TemplateGradleModuleSettings =
    TemplateGradleModuleSettings(
        plugins = plugins.map { it.key },
    )

private fun ToolchainSettings.toTemplateType(): TemplateToolchainModuleSettings =
    TemplateToolchainModuleSettings(
        compose = compose,
        ktor = ktor,
        application = application?.toTemplateType(),
        kotlin = kotlin?.toTemplateType(),
    )

private fun ToolchainApplicationSettings.toTemplateType(): TemplateToolchainApplicationSettings =
    TemplateToolchainApplicationSettings(
        mainClass = mainClass,
    )

private fun ToolchainKotlinSettings.toTemplateType(): TemplateToolchainKotlinSettings =
    TemplateToolchainKotlinSettings(
        serialization = serialization,
    )

context(project: Project)
private fun Dependency.toTemplateType(modulePath: String): TemplateBuildDependency =
    when (this) {
        is ArtifactDependency -> TemplateBuildDependency(
            type = "maven",
            group = group,
            artifact = artifact,
            version = version,
            exported = scope == Dependency.EXPORTED_SCOPE,
            scope = scope,
        )

        is ModuleDependency -> TemplateBuildDependency(
            type = "project",
            path = path,
            gradlePath = gradlePath(modulePath),
            typesafeProjectAccessor = typesafeProjectAccessor(modulePath),
            exported = scope == Dependency.EXPORTED_SCOPE,
            scope = scope,
        )

        is CatalogReference -> TemplateBuildDependency(
            type = "catalog",
            key = key,
            exported = scope == Dependency.EXPORTED_SCOPE,
            scope = scope,
        ) + project.libraries[tomlKey]?.toTemplateType()

        is FunctionDependency -> TemplateBuildDependency(
            type = "function",
            functionName = functionName,
            // TODO should use expressions
            args = args.map { it.wrapQuotes() },
            exported = scope == Dependency.EXPORTED_SCOPE,
            scope = scope,
        )

        is ReferenceDependency -> TemplateBuildDependency(
            type = "reference",
            reference = reference,
            exported = scope == Dependency.EXPORTED_SCOPE,
            scope = scope,
        )
    }

private operator fun TemplateBuildDependency.plus(artifact: TemplateCatalogArtifact?): TemplateBuildDependency =
    if (artifact == null) this
    else copy(
        group = artifact.group ?: group,
        artifact = artifact.artifact ?: this@plus.artifact,
        versionRef = artifact.versionRef ?: versionRef,
        version = artifact.version ?: version,
        scope = scope,
    )

context(project: Project)
internal fun SourceModule.slotsTemplateMap(packId: PackId): Map<String, Any?> =
    buildMap {
        for ((url, value) in project.slotSources + this@slotsTemplateMap.slotSources) {
            // insert relative value
            if (packId.toString() in url)
                put(url.relativeFile.removePrefix(packId.toString()).trimStart('/'), value)
            // insert absolute value
            put(url, value.map { sourceFile ->
                mapOf(
                    "target" to sourceFile.target.toString(),
                    "condition" to sourceFile.condition.toString(),
                    "pack" to sourceFile.packId,
                )
            })
        }
    }

// TODO support string templates
val SourceModule.slotSources: SourcesByUrl
    get() =
        sources
            .filter { it.isSlot() }
            .groupBy { it.target.toString() }

/**
 * Find the main class from either amper settings, gradle properties, or use the default main.kt file.
 */
context(project: Project)
internal fun SourceModule.mainClass(): String? {
    toolchain.application?.mainClass?.let { toolchainMain ->
        return toolchainMain
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

private fun ModuleDependency.gradlePath(modulePath: String): String = buildString {
    val actualPath = Path(modulePath).resolve(path).normalize()
    append(':')
    append(actualPath.toString().replace('/', ':'))
}

private fun ModuleDependency.typesafeProjectAccessor(modulePath: String): String = buildString {
    val actualPath = Path(modulePath).resolve(path).normalize()
    append("projects")
    for (segment in actualPath.toString().split('/')) {
        append('.')
        val parts = segment.split('-', '_')
        append(parts.first())
        for (part in parts.drop(1)) {
            append(part.replaceFirstChar { it.titlecase() })
        }
    }
}

// TODO small hack for working with maven
private fun isKmp(group: String, artifact: String): Boolean =
    group in setOf(
        "org.jetbrains",
        "org.jetbrains.kotlinx",
        "io.ktor",
    )
