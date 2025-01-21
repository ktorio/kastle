package org.jetbrains.kastle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.io.Buffer
import kotlinx.io.writeCodePointValue
import kotlinx.io.writeString
import org.jetbrains.kastle.SourceFileWriteContext.Companion.writeSourceFile
import org.jetbrains.kastle.utils.*
import java.lang.IllegalArgumentException
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
                    val slots = source.blocks?.asSequence().orEmpty()
                        .flatMap { slotSources.lookup(featureId, it) }
                        .toList()
                    val blocks = source.blocks?.sortedBy { it.rangeStart }.orEmpty()

                    emit(SourceFileEntry(source.target.afterProtocol) {
                        writeSourceFile {
                            when (source.target.extension) {
                                "kt" -> writeKotlinSourcePreamble(project, slots)
                            }
                            withBlocks(blocks) {
                                forEach { block ->
                                    println("  Block $block")

                                    // ancestors
                                    stack.findParent(block) { parent ->
                                        val bodyEnd = parent.body?.rangeEnd ?: return@findParent
                                        append(source.text, start, bodyEnd)
                                        start = parent.rangeEnd
                                    }

                                    // interstitial content
                                    append(source.text, start, block.rangeStart)

                                    // current block
                                    appendBlockContents(
                                        block,
                                        source,
                                        project.properties,
                                        slotSources.lookup(featureId, block)
                                    )

                                    // where to go next
                                    if (child != null) {
                                        stack += block
                                        start = child!!.rangeStart
                                    } else {
                                        start = block.rangeEnd
                                    }
                                }

                                // trailing ancestors
                                stack.removeAll { parent ->
                                    val bodyEnd = parent.body?.rangeEnd ?: return@removeAll
                                    append(source.text, start, bodyEnd)
                                    start = parent.rangeEnd
                                }

                                // trailing content
                                append(source.text, start, source.text.length)
                            }
                        }
                    })
                }
            }
        }
    }

    private fun Appendable.writeKotlinSourcePreamble(project: ProjectDescriptor, blocks: List<SourceTemplate>) {
        // TODO replace existing package in template if present
        append("package ${project.group}")
        append("\n\n")

        val imports: List<String> = blocks.asSequence()
            .flatMap { it.imports.orEmpty() }
            .distinct()
            .toList()

        if (imports.isNotEmpty()) {
            for (import in imports)
                append("import $import\n")
            append("\n")
        }
    }

    private fun Map<Url, List<SourceTemplate>>.lookup(featureId: FeatureId, block: Block): List<SourceTemplate> {
        if (block !is Slot)
            return emptyList()
        // TODO omitted should continue
        val key = "slot://$featureId/${block.name}"
        val values = this[key] ?: emptyList()
        if (values.isEmpty()) {
            when (block.requirement) {
                Requirement.REQUIRED ->
                    throw IllegalArgumentException("Missing slot slot://$featureId/${block.name}")
                Requirement.OMITTED -> return emptyList()
                Requirement.OPTIONAL -> {}
            }
        }
        require(block is RepeatingSlot || values.size <= 1) {
            "More than one target for non-repeating slot://$featureId/${block.name}"
        }
        return values
    }
}

private class SourceFileWriteContext(
    private val buffer: Buffer = Buffer(),
): Appendable {
    companion object {
        fun writeSourceFile(action: SourceFileWriteContext.() -> Unit): Buffer =
            SourceFileWriteContext().apply(action).buffer
    }

    override fun append(csq: CharSequence?): java.lang.Appendable? {
        if (csq == null) return this
        buffer.writeString(csq)
        return this
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): java.lang.Appendable? {
        if (csq == null) return this
        buffer.writeString(csq, start, end)
        return this
    }

    override fun append(c: Char): java.lang.Appendable? {
        buffer.writeCodePointValue(c.code)
        return this
    }

    internal fun withBlocks(blocks: List<Block>, op: SourceFileBlockIterationContext.() -> Unit) {
        SourceFileBlockIterationContext(blocks).apply(op)
    }

    inner class SourceFileBlockIterationContext(
        private val blocks: List<Block>,
        var start: Int = 0,
        var i: Int = 0,
        var stack: BlockStack = mutableListOf<Block>()
    ): Appendable by this {
        val child: Block? get() = next?.takeIf { it in blocks[i] }
        val next: Block? get() = blocks.getOrNull(i + 1)

        fun skipContents() {
            while (next in blocks[i])
                i++
        }

        fun forEach(onEach: (Block) -> Unit) {
            while (i < blocks.size) {
                onEach(blocks[i])
                i++
            }
        }
    }
}

private fun SourceFileWriteContext.SourceFileBlockIterationContext.appendBlockContents(
    block: Block,
    source: SourceTemplate,
    properties: Map<String, Any?> = emptyMap(),
    slots: List<SourceTemplate> = emptyList()
) {
    val indent = source.text.lastIndexOf('\n', block.rangeStart)
        .takeIf { it > 0 }
        ?.let { newLineIndex ->
            source.text.substring(newLineIndex + 1, block.rangeStart)
        }?.takeIf {
            it.all { it.isWhitespace() }
        } ?: ""

    when (block) {
        is Slot -> {
            append(slots.joinToString("\n\n$indent") {
                it.text.indent(indent)
            })
        }

        is CompareBlock -> {
            val contents = block.body?.let { body ->
                source.text.substring(
                    body.rangeStart,
                    child?.rangeStart ?: body.rangeEnd
                )
            }?.indent(indent)

            when (block) {
                is EqualsBlock -> {
                    val parent = stack.lastOrNull() as? WhenBlock
                        ?: error("__equals found with no parent __when")
                    val value = properties[parent.property]
                    // TODO types
                    if (value == block.value)
                        append(contents ?: "")
                    else skipContents()
                }
            }
        }

        is PropertyBlock -> {
            val contents = block.body?.let { body ->
                source.text.substring(
                    body.rangeStart,
                    child?.rangeStart ?: body.rangeEnd
                )
            }?.indent(indent)
            val value = properties[block.property]

            when (block) {
                is PropertyLiteral -> append(value.toString()) // writes "null" when missing
                is IfBlock ->
                    if (value.isTruthy())
                        append(contents ?: "")
                    else skipContents()

                is EachBlock -> append(contents ?: "")
                is WhenBlock -> {} // ignore contents of when block
            }
        }
    }
}

private typealias BlockStack = MutableList<Block>

/**
 * Removes from top of stack until parent is found, executing `onNotParent` for non-matches.
 */
private fun BlockStack.findParent(next: Block, onNotParent: (Block) -> Unit): Block? {
    while (isNotEmpty()) {
        val parent = last()
        if (next in parent)
            return parent
        onNotParent(removeLast())
    }
    return null
}

/**
 * Empties the stack, performing `onEach` for every element.
 */
private fun BlockStack.removeAll(onEach: (Block) -> Unit) {
    while (isNotEmpty())
        onEach(removeLast())
}


class MissingFeatureException(feature: FeatureId) : Exception("Missing feature: $feature")