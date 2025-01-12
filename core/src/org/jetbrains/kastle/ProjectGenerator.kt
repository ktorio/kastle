package org.jetbrains.kastle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.io.Buffer
import kotlinx.io.writeString

interface ProjectGenerator {
    companion object {
        fun fromRepository(repository: FeatureRepository): ProjectGenerator =
            ProjectGeneratorImpl(repository)
    }

    suspend fun generate(project: ProjectDescriptor): Flow<SourceFileEntry>
}

internal class ProjectGeneratorImpl(private val repository: FeatureRepository) : ProjectGenerator {
    override suspend fun generate(project: ProjectDescriptor): Flow<SourceFileEntry> {
        val features = project.features.map {
            repository.get(it) ?: throw MissingFeatureException(it)
        }
        val fileSourcesByFeatureId = features.mapNotNull { feature ->
            feature.sources
                .filter { it.target.protocol == "file" }
                .takeIf { it.isNotEmpty() }
                ?.let { feature.id to it }
        }.toMap()

        if (fileSourcesByFeatureId.isEmpty())
            return emptyFlow()

        val slotSources = features.flatMap { it.sources }
            .filter { it.target.protocol == "slot" }
            .groupBy { it.target }

        return flow {
            for ((featureId, sources) in fileSourcesByFeatureId) {
                for (source in sources) {
                    val slotsForSource: Map<IntRange, List<SourceTemplate>> = source.slots.orEmpty().asSequence()
                        .associate { slot ->
                            // TODO omitted should continue
                            val values = slotSources["slot://$featureId/${slot.name}"] ?: emptyList()
                            require(slot.requirement == Requirement.OPTIONAL || values.isNotEmpty()) {
                                "Missing slot ${slot.name}"
                            }
                            require(slot is RepeatingSlot || values.size <= 1) {
                                "More than one target for non-repeating slot://$featureId/${slot.name}"
                            }
                            slot.position.range to values
                    }
                    emit(SourceFileEntry(source.target.afterProtocol) {
                        Buffer().also { buffer ->
                            when(source.target.extension) {
                                "kt" -> {
                                    buffer.writeString("package ${project.group}")
                                    buffer.writeString("\n\n")

                                    val allImports: List<String> = slotsForSource.values
                                        .flatMap { templates ->
                                            templates.flatMap { it.imports.orEmpty() }
                                        }.distinct()

                                    if (allImports.isNotEmpty()) {
                                        for (import in allImports)
                                            buffer.writeString("import $import\n")
                                        buffer.writeString("\n")
                                    }
                                }
                            }
                            var start = 0
                            for ((range, values) in slotsForSource) {
                                val indent = source.text.lastIndexOf('\n', range.start).takeIf { it > 0 }?.let { newLineIndex ->
                                    source.text.substring(newLineIndex + 1, range.start)
                                }?.takeIf { it.all { it.isWhitespace() } }

                                buffer.writeString(source.text, start, range.start)
                                buffer.writeString(values.joinToString("\n") { it.text.lines().joinToString("\n$indent") })

                                start = range.endInclusive + 1
                            }
                            buffer.writeString(source.text, start, source.text.length)
                        }
                    })
                }
            }
        }
    }
}

class MissingFeatureException(feature: FeatureId) : Exception("Missing feature: $feature")