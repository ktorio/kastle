package org.jetbrains.kastle.gen

import org.jetbrains.kastle.*
import org.jetbrains.kastle.utils.isFile
import org.jetbrains.kastle.utils.isSlot

fun interface ProjectResolver {
    companion object {
        val Default = ProjectResolver { descriptor, repository ->
            val packs = descriptor.packs.map { repository.get(it) ?: throw MissingPackException(it) }
            val moduleSources = packs.asSequence()
                .map { it.sources.modules }
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
                        variableId to readValue(descriptor, variableId, property)
                    }
                }
            }.toMap()
            val repositoryCatalog = repository.versions()
            val versions = mutableMapOf<String, String>()
            val libraries = mutableMapOf<String, CatalogArtifact>()
            for (dependency in moduleSources.modules.flatMap { it.allDependencies }) {
                if (dependency !is CatalogReference) continue
                val version = dependency.version
                // TODO allow non-refs?
                val versionRef = (version as? CatalogVersion.Ref)?.ref ?: continue
                val versionValue = repositoryCatalog.versions[versionRef]
                if (versionValue != null)
                    versions[versionRef] = versionValue
                val library = repositoryCatalog.libraries[dependency.lookupKey]
                if (library != null)
                    libraries[dependency.lookupKey] = library
            }
            val gradleSettings = GradleSettings(
                moduleSources.modules.flatMap { module ->
                    module.gradlePlugins
                }
            )

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
                gradle = gradleSettings,
            )
        }

        private fun readValue(
            descriptor: ProjectDescriptor,
            variableId: VariableId,
            property: Property
        ): Any? {
            try {
                val value = descriptor.properties[variableId] ?: property.default
                return value?.let(property.type::parse)
            } catch (e: Exception) {
                throw IllegalArgumentException("Failed to read property $variableId: ${e.message}", e)
            }
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
    }

    suspend fun resolve(
        descriptor: ProjectDescriptor,
        repository: PackRepository,
    ): Project
}