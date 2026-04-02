package org.jetbrains.kastle.gen

import kotlinx.coroutines.flow.toList
import org.jetbrains.kastle.*
import org.jetbrains.kastle.utils.TreeMap
import org.jetbrains.kastle.utils.isFile
import org.jetbrains.kastle.utils.isSlot
import org.jetbrains.kastle.utils.merge
import kotlin.collections.groupBy

fun interface ProjectResolver {
    companion object {
        val BaseImpl = ProjectResolver { descriptor, repository ->
            val packs = repository.getAllWithRequirements(descriptor.packs)
                .toList()
                .distinctBy { it.id }
            val moduleSources = packs.asSequence()
                .map { it.sources.modules }
                .reduceOrNull(ProjectModules::plus)
                ?.flatten() ?: ProjectModules.Empty
            val slotSources: SourcesByUrl = packs.asSequence()
                .flatMap { it.commonAndRootSources }
                .filter { it.isSlot() }
                .groupBy { it.target.toString() }
            val commonSourceFiles = packs
                .flatMap { it.commonSources }
                .filter { it.isFile() }
            val rootSources = packs
                .flatMap { it.rootSources }
                .filter { it.isFile() }
            val propertyValues = packs.asSequence()
                .map { it.propertyValues }
                .reduceOrNull { acc, map -> acc.merge(map) }.orEmpty()
                .mapValues { (_, assignments) -> assignments.groupBy { it.key } }
            val propertyDescriptors = packs.flatMap { pack ->
                pack.properties.map { property ->
                    VariableId(pack.id, property.key) to property
                }
            }.toMap()
            val properties: Map<PropertyScope, Map<VariableId, PropertyInstance>> =
                propertyValues.mapValues { (propertyScope, assignments) ->
                    propertyDescriptors.mapValues { (variableId, property) ->
                        resolveProperty(descriptor, assignments, variableId, property)
                    }
                }
            // Merge all catalogs for library lookups
            // TODO handle collisions
            val repositoryCatalog = repository.catalogs().reduce { acc, catalog -> acc + catalog }
            val versions = TreeMap<String, String>().also { versions ->
                versions["kotlin"] = repositoryCatalog.versions["kotlin"] ?: missingVersion("kotlin")
            }
            val libraries = TreeMap<String, CatalogArtifact>()
            val gradlePlugins = TreeMap<String, GradlePlugin>()

            for (module in moduleSources.modules) {
                for (catalogRef in module.gradlePlugins) {
                    val catalogKey = catalogRef.tomlKey
                    val (id, version) = repositoryCatalog.plugins[catalogKey] ?: continue
                    if (version is CatalogVersion.Ref)
                        versions[version.ref] = repositoryCatalog.versions[version.ref] ?: missingVersion(version.ref)
                    gradlePlugins[catalogKey] = GradlePlugin(id, catalogRef.key, catalogKey, version)
                }

                for (dependency in module.allDependencies) {
                    if (dependency !is CatalogReference) continue
                    val artifact = repositoryCatalog.libraries[dependency.tomlKey]
                    if (artifact == null) {
                        // skip libraries supplied from other catalogs
                        if (!dependency.tomlKey.startsWith("lib"))
                            continue
                        missingDependency(dependency)
                    }
                    when(val version = artifact.version) {
                        is CatalogVersion.Ref -> {
                            val versionValue = repositoryCatalog.versions[version.ref]
                            versions[version.ref] = versionValue ?: missingVersion(version.ref)
                        }
                        is CatalogVersion.Number -> {
                            // nothing else required
                        }
                    }
                    repositoryCatalog.libraries[dependency.tomlKey]?.let {
                        libraries[dependency.tomlKey] = it
                    }
                }
            }

            // Include SDK versions when android is included
            if (moduleSources.modules.any { Platform.ANDROID in it.platforms }) {
                versions += repositoryCatalog.versions.entries.mapNotNull { (key, value) ->
                    if (key.startsWith("android") && key.contains("Sdk")) {
                        key to value
                    } else null
                }
            }

            val gradleSettings = GradleProjectSettings(
                repositories = packs.flatMap { it.repositories }.distinct(),
                pluginRepositories = packs.flatMap { it.pluginRepositories }.distinct(),
                plugins = gradlePlugins.values.toList(),
            )

            // TODO validate structure, check for collisions, etc.
            Project(
                descriptor = descriptor,
                packs = packs,
                properties = properties,
                slotSources = slotSources,
                moduleSources = moduleSources + rootSources,
                commonSources = commonSourceFiles,
                versions = versions,
                libraries = libraries,
                gradle = gradleSettings,
                packaging = descriptor.packaging,
            )
        }

        private fun resolveProperty(
            projectDescriptor: ProjectDescriptor,
            packPropertyValues: Map<VariableId, List<PropertyAssignment>>,
            variableId: VariableId,
            property: PropertyDescriptor
        ): PropertyInstance {
            try {
                return projectDescriptor.properties[variableId]?.let {
                    ResolvedProperty(property, property.type.parse(it))
                } ?: packPropertyValues[variableId]?.let { assignments ->
                    DynamicProperty(property, assignments)
                } ?: property.default?.let {
                    ResolvedProperty(property, property.type.parse(it))
                } ?: if (property.type.isNullable()) {
                    ResolvedProperty(property, null)
                } else UnresolvedProperty(property)
            } catch (e: Exception) {
                throw IllegalArgumentException("Failed to read property $variableId: ${e.message}", e)
            }
        }

        private operator fun ProjectModules.plus(rootSources: List<SourceFile>): ProjectModules =
            when (this) {
                is ProjectModules.Empty ->
                    ProjectModules.Single(SourceModule(sources = rootSources))
                is ProjectModules.Single ->
                    copy(module = module.copy(sources = module.sources + rootSources))
                is ProjectModules.Multi ->
                    copy(modules = modules + SourceModule(sources = rootSources))
            }

        private fun missingDependency(dependency: Dependency): Nothing =
            throw IllegalArgumentException("Missing dependency $dependency")

        private fun missingVersion(versionRef: String): Nothing =
            throw IllegalArgumentException("Missing version $versionRef")
    }

    suspend fun resolve(
        descriptor: ProjectDescriptor,
        repository: PackRepository,
    ): Project
}
