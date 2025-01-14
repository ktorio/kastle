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
                    val resolvedSlots = source.slots.orEmpty().asSequence()
                        .map { slot ->
                            // TODO omitted should continue
                            val values = slotSources["slot://$featureId/${slot.name}"] ?: emptyList()
                            require(slot.requirement == Requirement.OPTIONAL || values.isNotEmpty()) {
                                "Missing slot ${slot.name}"
                            }
                            require(slot is RepeatingSlot || values.size <= 1) {
                                "More than one target for non-repeating slot://$featureId/${slot.name}"
                            }
                            ResolvedBlock.Templates(slot.position.range, values)
                        }
                    val resolvedLogicalBlocks = source.blocks.orEmpty().asSequence()
                        .map { block ->
                            val value = project.properties[block.property]
                            // TODO do logic
                            ResolvedBlock.Property(block.position.range, value)
                        }
                    val blocks = (resolvedSlots + resolvedLogicalBlocks)
                        .sortedBy { it.range.start }
                        .toList()

                    emit(SourceFileEntry(source.target.afterProtocol) {
                        Buffer().also { buffer ->
                            when(source.target.extension) {
                                "kt" -> {
                                    buffer.writeString("package ${project.group}")
                                    buffer.writeString("\n\n")

                                    val allImports: List<String> = blocks.asSequence()
                                        .filterIsInstance<ResolvedBlock.Templates>()
                                        .flatMap { it.templates.flatMap { it.imports.orEmpty() } }
                                        .distinct()
                                        .toList()

                                    if (allImports.isNotEmpty()) {
                                        for (import in allImports)
                                            buffer.writeString("import $import\n")
                                        buffer.writeString("\n")
                                    }
                                }
                            }
                            var start = 0
                            for (block in blocks) {
                                val indent = source.text.lastIndexOf('\n', block.range.start).takeIf { it > 0 }?.let { newLineIndex ->
                                    source.text.substring(newLineIndex + 1, block.range.start)
                                }?.takeIf { it.all { it.isWhitespace() } }

                                buffer.writeString(source.text, start, block.range.start)

                                when(block) {
                                    is ResolvedBlock.Property -> block.value?.let {
                                        buffer.writeString(it.toString())
                                    }
                                    is ResolvedBlock.Templates -> buffer.writeString(block.templates.joinToString("\n\n$indent") {
                                        it.text.lines().joinToString("\n$indent")
                                    })
                                }

                                start = block.range.endInclusive + 1
                            }
                            buffer.writeString(source.text, start, source.text.length)
                        }
                    })
                }
            }
        }
    }
}

sealed interface ResolvedBlock {
    val range: IntRange

    data class Templates(override val range: IntRange, val templates: List<SourceTemplate>): ResolvedBlock
    data class Property(override val range: IntRange, val value: Any?): ResolvedBlock
}

class MissingFeatureException(feature: FeatureId) : Exception("Missing feature: $feature")