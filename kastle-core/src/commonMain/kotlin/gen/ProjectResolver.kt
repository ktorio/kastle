package org.jetbrains.kastle.gen

import kotlinx.coroutines.flow.toList
import org.jetbrains.kastle.*
import org.jetbrains.kastle.utils.TreeMap
import org.jetbrains.kastle.utils.isFile
import org.jetbrains.kastle.utils.isSlot

fun interface ProjectResolver {
    companion object {
        val Default = ProjectResolver { descriptor, repository ->
            val packs = repository.getAllWithRequirements(descriptor.packs)
                .toList()
                .distinctBy { it.id }
            val moduleSources = packs.asSequence()
                .map { it.sources.modules }
                .reduceOrNull(ProjectModules::plus)
                ?.flatten() ?: ProjectModules.Empty
            val slotSources: Map<Url, List<SourceFile>> = packs.asSequence()
                .flatMap { it.allSources }
                .filter { it.isSlot() }
                .groupBy { it.target }
            val commonSourceFiles = packs
                .flatMap { it.commonSources }
                .filter { it.isFile() }
            val rootSourceFiles = packs
                .flatMap { it.rootSources }
                .filter { it.isFile() }
            val attributes = packs.flatMap { pack ->
                pack.attributes.entries
            }.groupBy({ it.key }) {
                it.value
            }
            val properties = packs.flatMap { pack ->
                pack.properties.map { property ->
                    VariableId(pack.id, property.key).let { variableId ->
                        variableId to findPropertyValue(descriptor, attributes, variableId, property)
                    }
                }
            }.toMap()
            val repositoryCatalog = repository.versions()
            // TODO hard-coded kotlin version
            val versions = mutableMapOf("kotlin" to "2.1.21")
            val libraries = TreeMap<String, CatalogArtifact>()
            val gradlePlugins = TreeMap<String, GradlePlugin>()
            for (module in moduleSources.modules) {
                for (pluginKey in module.gradlePlugins) {
                    val catalogKey = CatalogReference.lookupFormat(pluginKey)
                    val (id, version) = repositoryCatalog.plugins[catalogKey] ?: continue
                    gradlePlugins[pluginKey] = GradlePlugin(id, pluginKey, version)
                }

                for (dependency in module.allDependencies) {
                    if (dependency !is CatalogReference) continue
                    val artifact = repositoryCatalog.libraries[dependency.lookupKey]
                    if (artifact == null) {
                        // skip libraries supplied from other catalogs
                        if (!dependency.lookupKey.startsWith("lib"))
                            continue
                        missingDependency(dependency)
                    }
                    val version = artifact.version
                    // TODO allow non-refs?
                    val versionRef = (version as? CatalogVersion.Ref)?.ref ?: continue
                    val versionValue = repositoryCatalog.versions[versionRef]
                    if (versionValue != null)
                        versions[versionRef] = versionValue
                    val library = repositoryCatalog.libraries[dependency.lookupKey]
                    if (library != null)
                        libraries[dependency.lookupKey] = library
                }
            }

            // TODO validate structure, check for collisions, etc.
            Project(
                descriptor = descriptor,
                packs = packs,
                properties = properties,
                slotSources = slotSources,
                moduleSources = moduleSources + rootSourceFiles,
                commonSources = commonSourceFiles,
                versions = versions,
                libraries = libraries,
                gradle = GradleProjectSettings(gradlePlugins.values.toList()),
            )
        }

        private fun findPropertyValue(
            descriptor: ProjectDescriptor,
            attributes: Map<VariableId, List<String>>,
            variableId: VariableId,
            property: Property
        ): Any? {
            try {
                val stringValue = descriptor.properties[variableId]
                    ?: attributes[variableId]?.let { attrs -> attrs.singleOrNull() ?: attrs.joinToString(",") }
                    ?: property.default
                if (stringValue == null) {
                    require(property.type is PropertyType.Nullable) {
                        "Missing value for property $variableId"
                    }
                    return null
                }
                return property.type.parse(stringValue)
            } catch (e: Exception) {
                throw IllegalArgumentException("Failed to read property $variableId: ${e.message}", e)
            }
        }

        // Add root sources
        private operator fun ProjectModules.plus(rootSources: List<SourceFile>): ProjectModules =
            when (this) {
                is ProjectModules.Empty ->
                    ProjectModules.Single(SourceModule(sources = rootSources))
                is ProjectModules.Single ->
                    copy(module = module.copy(sources = module.sources + rootSources))
                is ProjectModules.Multi ->
                    // TODO check for root
                    copy(modules = modules + SourceModule(sources = rootSources))
            }

        private fun missingDependency(dependency: Dependency): Nothing =
            throw IllegalArgumentException("Missing dependency $dependency")
    }

    suspend fun resolve(
        descriptor: ProjectDescriptor,
        repository: PackRepository,
    ): Project
}