package org.jetbrains.kastle.gen

import kotlinx.coroutines.flow.toList
import org.jetbrains.kastle.*
import org.jetbrains.kastle.structure.BuildToolModules
import org.jetbrains.kastle.utils.TreeMap
import org.jetbrains.kastle.utils.Variables
import org.jetbrains.kastle.utils.isFile
import org.jetbrains.kastle.utils.isSlot
import org.jetbrains.kastle.utils.isTruthy
import org.jetbrains.kastle.utils.merge
import kotlin.collections.groupBy

fun interface ProjectResolver {
    companion object {
        val BaseImpl = ProjectResolver { descriptor, repository ->
            val chosenPacks = repository.readAll(descriptor.packs).toList()
            var moduleSources: ProjectModules = ProjectModules.Empty
            val packs = chosenPacks.toMutableList()
            val requirementsVisited = descriptor.packs
                .map(::PackRequirement)
                .toMutableSet()

            // Collect all module sources
            // This allows for remapping modules in requirements
            for (pack in chosenPacks) {
                moduleSources += pack.sources.modules
                for (requirement in pack.requires) {
                    if (!requirementsVisited.add(requirement))
                        continue
                    val requiredPack = repository.read(requirement.packId)
                        ?: throw MissingPackException(requirement.packId)
                    packs += requiredPack
                    moduleSources += requiredPack.sources.modules.map { module ->
                        val replacementPath = requirement.modules[module.path] ?: return@map module
                        module.copy(manifest = module.manifest.copy(path = replacementPath),)
                    }
                }
            }
            // Remove empty intermediate modules
            moduleSources = moduleSources.flatten()

            // TODO need to resolve target expression before grouping here
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
            val packAssignments = propertyValues[PropertyScope.Root].orEmpty()
            val properties: Map<PropertyScope, Map<VariableId, PropertyInstance>> =
                propertyValues.mapValues { (propertyScope, assignments) ->
                    propertyDescriptors.mapValues { (variableId, property) ->
                        resolveProperty(
                            descriptor,
                            packAssignments,
                            assignments,
                            variableId,
                            property
                        )
                    }
                }
            // Merge all catalogs for library lookups
            // TODO handle collisions
            // TODO proper solution for other catalogs

            // Currently, we only include non-libs catalogs for maven builds
            val repositoryLibsCatalog = when {
                BuildToolModules.MAVEN_PACK_ID in descriptor.packs ->
                    repository.catalogs().reduce { acc, catalog -> acc + catalog }
                else -> repository.versions()
            }
            val versions = TreeMap<String, String>().also { versions ->
                versions["kotlin"] = repositoryLibsCatalog.versions["kotlin"] ?: missingVersion("kotlin")
            }
            val projectCatalog = TreeMap<String, CatalogArtifact>()
            val gradlePlugins = TreeMap<String, GradlePlugin>()

            val eagerVars = eagerVariables(properties)

            for (module in moduleSources.modules) {
                if (!isModuleActive(module, eagerVars)) continue

                for (catalogRef in module.gradlePlugins) {
                    val catalogKey = catalogRef.tomlKey
                    // ignore plugins outside libs
                    if (catalogRef.catalog != "libs") continue
                    val artifact = repositoryLibsCatalog.plugins[catalogKey]
                    require(artifact != null) { "Missing gradle plugin: $catalogKey" }
                    val (id, version) = artifact
                    if (version is CatalogVersion.Ref)
                        versions[version.ref] = repositoryLibsCatalog.versions[version.ref] ?: missingVersion(version.ref)
                    gradlePlugins[catalogKey] = GradlePlugin(id, catalogRef.key, catalogKey, version)
                }

                for (dependency in module.allDependencies) {
                    if (dependency !is CatalogReference) continue
                    val artifact = repositoryLibsCatalog.libraries[dependency.tomlKey]
                    if (artifact == null) {
                        // skip libraries supplied from other catalogs
                        if (!dependency.tomlKey.startsWith("lib"))
                            continue
                        missingDependency(dependency)
                    }
                    when(val version = artifact.version) {
                        is CatalogVersion.Ref -> {
                            val versionValue = repositoryLibsCatalog.versions[version.ref]
                            versions[version.ref] = versionValue ?: missingVersion(version.ref)
                        }
                        is CatalogVersion.Number -> {
                            // nothing else required
                        }
                    }
                    repositoryLibsCatalog.libraries[dependency.tomlKey]?.let {
                        projectCatalog[dependency.tomlKey] = it
                    }
                }
            }

            // Include SDK versions when android is included
            if (moduleSources.modules.any { Platform.ANDROID in it.platforms }) {
                versions += repositoryLibsCatalog.versions.entries.mapNotNull { (key, value) ->
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
                libraries = projectCatalog,
                gradle = gradleSettings,
                packaging = descriptor.packaging,
            )
        }

        /**
         * Preference order of property resolution:
         * 1. User-supplied properties.
         * 2. Module manifest assignments.
         * 3. Root manifest assignments.
         * 3. Default values.
         * 4. Null (if nullable)
         * Otherwise, the property is marked as unresolved.
         */
        private fun resolveProperty(
            projectDescriptor: ProjectDescriptor,
            packAssignments: Map<VariableId, List<PropertyAssignment>>,
            moduleAssignments: Map<VariableId, List<PropertyAssignment>>,
            variableId: VariableId,
            property: PropertyDescriptor
        ): PropertyInstance {
            try {
                return projectDescriptor.properties[variableId]?.let {
                    ResolvedProperty(property, property.type.parse(it))
                } ?: moduleAssignments[variableId]?.let { assignments ->
                    DynamicProperty(property, assignments)
                } ?: packAssignments[variableId]?.let { assignments ->
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

        private fun isModuleActive(
            module: SourceModule,
            eagerVars: Variables,
        ): Boolean {
            val condition = module.condition ?: return true
            return condition.expression.evaluate(eagerVars.relativeTo(condition.packId)).isTruthy()
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
