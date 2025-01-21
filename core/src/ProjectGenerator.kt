package org.jetbrains.kastle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.io.Buffer
import kotlinx.io.writeString
import org.jetbrains.kastle.utils.*
import kotlin.collections.isNotEmpty

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

        val slotSources: Map<Url, List<SourceTemplate>> = features.flatMap { it.sources }
            .filter { it.target.protocol == "slot" }
            .groupBy { it.target }

        return flow {
            for ((featureId, sources) in fileSourcesByFeatureId) {
                for (source in sources) {
                    println("Template ${source.target}")
                    val slots = source.blocks?.asSequence().orEmpty()
                        .filterIsInstance<Slot>()
                        .flatMap { slotSources.lookup(featureId, it) }
                        .toList()
                    val blocks = source.blocks?.sortedBy { it.rangeStart } ?: emptyList()

                    emit(SourceFileEntry(source.target.afterProtocol) {
                        Buffer().also { buffer ->
                            when(source.target.extension) {
                                "kt" -> buffer.writeKotlinSourcePreamble(project, slots)
                            }
                            var start = 0
                            var i = 0
                            var stack = mutableListOf<Block>()
                            while (i < blocks.size) {
                                val block = blocks[i]
                                println("  Block $block")

                                // ancestors
                                stack.findParent(block) { parent ->
                                    val bodyEnd = parent.body?.rangeEnd ?: return@findParent
                                    buffer.writeString(source.text, start, bodyEnd)
                                    start = parent.rangeEnd
                                }

                                // interstitial content
                                buffer.writeString(source.text, start, block.rangeStart)

                                // block's body
                                var child = blocks.getOrNull(i + 1)?.takeIf { it in block }
                                fun skipContents() {
                                    child = null
                                    while (blocks.getOrNull(i + 1) in block)
                                        i++
                                }

                                val indent = source.text.lastIndexOf('\n', block.rangeStart).takeIf { it > 0 }?.let { newLineIndex ->
                                    source.text.substring(newLineIndex + 1, block.rangeStart)
                                }?.takeIf {
                                    it.all { it.isWhitespace() }
                                } ?: ""

                                when(block) {
                                    is Slot -> {
                                        val sources = slotSources.lookup(featureId, block)
                                        buffer.writeString(sources.joinToString("\n\n$indent") {
                                            it.text.indent(indent)
                                        })
                                    }
                                    is CompareBlock -> {
                                        val contents = block.body?.let { body ->
                                            source.text.substring(body.rangeStart, child?.rangeStart ?: body.rangeEnd)
                                        }?.indent(indent)

                                        when (block) {
                                            is EqualsBlock -> {
                                                val parent = stack.lastOrNull() as? WhenBlock
                                                    ?: error("__equals found with no parent __when")
                                                val value = project.properties[parent.property]
                                                // TODO types
                                                if (value == block.value)
                                                    buffer.writeString(contents ?: "")
                                                else skipContents()
                                            }
                                        }
                                    }
                                    is PropertyBlock -> {
                                        val contents = block.body?.let { body ->
                                            source.text.substring(body.rangeStart, child?.rangeStart ?: body.rangeEnd)
                                        }?.indent(indent)
                                        val value = project.properties[block.property]

                                        when(block) {
                                            is PropertyLiteral -> {
                                                // writes "null" when missing
                                                buffer.writeString(value.toString())
                                            }
                                            is IfBlock -> {
                                                if (value.isTruthy())
                                                    buffer.writeString(contents ?: "")
                                                else skipContents()
                                            }
                                            is EachBlock -> buffer.writeString(contents ?: "")
                                            is WhenBlock -> {} // ignore contents of when block
                                        }
                                    }
                                }

                                if (child != null) {
                                    stack += block
                                    start = child!!.rangeStart
                                } else {
                                    start = block.rangeEnd
                                }
                                i++
                            }

                            stack.removeAll { parent ->
                                val bodyEnd = parent.body?.rangeEnd ?: return@removeAll
                                buffer.writeString(source.text, start, bodyEnd)
                                start = parent.rangeEnd
                            }
                            buffer.writeString(source.text, start, source.text.length)
                        }
                    })
                }
            }
        }
    }

    private fun Buffer.writeKotlinSourcePreamble(project: ProjectDescriptor, blocks: List<SourceTemplate>) {
        // TODO replace existing package in template if present
        writeString("package ${project.group}")
        writeString("\n\n")

        val imports: List<String> = blocks.asSequence()
            .flatMap { it.imports.orEmpty() }
            .distinct()
            .toList()

        if (imports.isNotEmpty()) {
            for (import in imports)
                writeString("import $import\n")
            writeString("\n")
        }
    }

//    private fun SourceTemplate.resolveBlocks(
//        project: ProjectDescriptor,
//        featureId: FeatureId,
//        slotSources: Map<Url, List<SourceTemplate>>,
//    ): Sequence<ResolvedBlock> {
//        if (blocks == null)
//            return emptySequence()
//
//        val sortedBlocks = blocks.sortedBy {
//            it.rangeStart
//        }
//        return sequence {
//            var i = 0
//            while (i < blocks.size) {
//                yield(when(val block = sortedBlocks[i]) {
//                    is Slot -> {
//                        slotSources.lookup(featureId, block)
//                    }
//                    is LogicalBlock -> {
//                        val range = block.position.range
//                        val value = project.properties[block.property]
//
//                        sortedBlocks.getOrNull(i + 1)?.let {
//                            var next = it
//                            while (next.rangeStart in range) {
//                                // TODO handle nested blocks
//                                next = sortedBlocks.getOrNull(++i) ?: break
//                            }
//                        }
//                        // TODO do logic
//                        println("  Block ${block.property} $value")
//                        when (block) {
//                            is PropertyLiteral ->
//                                ResolvedBlock.Property(range, value)
//
//                            is IfBlock ->
//                                if (value.isTruthy() && block.body != null)
//                                    ResolvedBlock.Snippet(range, block.body.text)
//                                else ResolvedBlock.Empty(range)
//
//                            is EachBlock -> ResolvedBlock.Empty(range) // TODO
//                            is WhenBlock -> ResolvedBlock.Empty(range) // TODO
//                        }
//                    }
//                })
//
//                i++
//            }
//        }
//    }

    private fun Map<Url, List<SourceTemplate>>.lookup(featureId: FeatureId, block: Slot): List<SourceTemplate> {
        // TODO omitted should continue
        val key = "slot://$featureId/${block.name}"
        val values = this[key] ?: emptyList()
        require(block.requirement == Requirement.OPTIONAL || values.isNotEmpty()) {
            "Missing slot ${block.name}"
        }
        require(block is RepeatingSlot || values.size <= 1) {
            "More than one target for non-repeating slot://$featureId/${block.name}"
        }
        println("  Slot $key $values")
        return values
    }
}

private typealias BlockStack = MutableList<Block>

private fun BlockStack.findParent(next: Block, onNotParent: (Block) -> Unit): Block? {
    while (isNotEmpty()) {
        val parent = last()
        if (next in parent)
            return parent
        onNotParent(removeLast())
    }
    return null
}

private fun BlockStack.removeAll(onEach: (Block) -> Unit) {
    while (isNotEmpty())
        onEach(removeLast())
}


class MissingFeatureException(feature: FeatureId) : Exception("Missing feature: $feature")