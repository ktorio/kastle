package org.jetbrains.kastle.gen

import org.jetbrains.kastle.*
import org.jetbrains.kastle.utils.isFile
import org.jetbrains.kastle.utils.isSlot

fun interface ProjectResolver {
    companion object {
        val Default = ProjectResolver { descriptor, repository ->
            val packs = descriptor.packs.map { repository.get(it) ?: throw MissingPackException(it) }
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
                        variableId to (descriptor.properties[variableId] ?: property.default)?.let(property.type::parse)
                    }
                }
            }.toMap()
            // TODO validate structure, check for collisions, etc.
            Project(
                descriptor = descriptor,
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
    }

    suspend fun resolve(
        descriptor: ProjectDescriptor,
        repository: PackRepository,
    ): Project
}