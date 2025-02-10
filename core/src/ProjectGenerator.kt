package org.jetbrains.kastle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.Buffer
import kotlinx.io.writeCodePointValue
import kotlinx.io.writeString
import org.jetbrains.kastle.SourceFileWriteContext.Companion.writeSourceFile
import org.jetbrains.kastle.utils.*
import kotlin.collections.isNotEmpty

interface ProjectGenerator {
    companion object {
        fun fromRepository(repository: PackRepository): ProjectGenerator =
            ProjectGeneratorImpl(repository)
    }

    suspend fun generate(project: ProjectDescriptor): Flow<SourceFileEntry>
}

internal class ProjectGeneratorImpl(private val repository: PackRepository) : ProjectGenerator {

    override suspend fun generate(project: ProjectDescriptor): Flow<SourceFileEntry> = flow {
        val packs = project.packs.map {
            repository.get(it) ?: throw MissingPackException(it)
        }
        // TODO ensure structure consistency
        val structure = packs.asSequence()
            .map { it.structure }
            .reduceOrNull(ProjectStructure::plus)
            ?: ProjectStructure.Empty
        val allFileNames = mutableSetOf<String>()
        val slotSources: Map<Url, List<SourceTemplate>> = packs.asSequence()
            .flatMap { it.sources }
            .filter { it.target.protocol == "slot" }
            .groupBy { it.target }

        for (pack in packs) {
            for (module in pack.structure.modules) {
                for (source in module.sources.filter { it.target.protocol == "file" }) {
                    val path = source.target.relativeFile
                    require(allFileNames.add(path)) {
                        "File conflict for module \"${pack.id}\" at \"${source.target}\""
                    }
                    emit(SourceFileEntry(path) {
                        writeSourceFile {
                            writeSourcePreamble(
                                pack.id,
                                source,
                                slotSources,
                                project
                            )
                            withBlocks(source.blocks) {
                                forEach { block ->
                                    // ancestors
                                    stack.removeUpToParent(block) { parent ->
                                        val bodyEnd = parent.body?.rangeEnd ?: return@removeUpToParent
                                        append(source.text, start, bodyEnd)
                                        start = parent.rangeEnd
                                    }

                                    // interstitial
                                    append(source.text, start, block.rangeStart)

                                    // current block
                                    appendBlockContents(
                                        block,
                                        source,
                                        project.properties,
                                        slotSources.lookup(pack.id, block)
                                    )

                                    // where to go next
                                    start = when (child) {
                                        null -> block.rangeEnd
                                        else -> {
                                            stack += block
                                            child!!.rangeStart
                                        }
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

    private fun Appendable.writeSourcePreamble(
        packId: PackId,
        source: SourceTemplate,
        slotSources: Map<Url, List<SourceTemplate>>,
        project: ProjectDescriptor
    ) {
        val slots = source.blocks?.asSequence().orEmpty()
            .flatMap { slotSources.lookup(packId, it) }
            .toList()
        when (source.target.extension) {
            "kt" -> writeKotlinSourcePreamble(project, slots)
        }
    }

    private fun Appendable.writeKotlinSourcePreamble(
        project: ProjectDescriptor,
        blocks: List<SourceTemplate>
    ) {
        // TODO replace existing package in template if present
        //      include sub-packages when present in the target file
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

    private fun Map<Url, List<SourceTemplate>>.lookup(packId: PackId, block: Block): List<SourceTemplate> {
        if (block !is Slot)
            return emptyList()

        val key = "slot://$packId/${block.name}"
        val values = this[key] ?: emptyList()
        if (values.isEmpty()) {
            when (block.requirement) {
                Requirement.REQUIRED ->
                    throw IllegalArgumentException("Missing slot slot://$packId/${block.name}")
                Requirement.OMITTED -> return emptyList()
                Requirement.OPTIONAL -> {}
            }
        }
        require(block is RepeatingSlot || values.size <= 1) {
            "More than one target for non-repeating slot://$packId/${block.name}"
        }
        return values
    }
}

/**
 * Writer context for building a source file.
 */
private class SourceFileWriteContext(
    private val buffer: Buffer = Buffer(),
): Appendable {
    companion object {
        fun writeSourceFile(action: SourceFileWriteContext.() -> Unit): Buffer =
            SourceFileWriteContext().apply(action).buffer
    }

    override fun append(csq: CharSequence?): java.lang.Appendable {
        if (csq == null) return this
        buffer.writeString(csq)
        return this
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): java.lang.Appendable {
        if (csq == null) return this
        buffer.writeString(csq, start, end)
        return this
    }

    override fun append(c: Char): java.lang.Appendable {
        buffer.writeCodePointValue(c.code)
        return this
    }

    fun withBlocks(blocks: List<Block>?, op: SourceFileBlockIterationContext.() -> Unit) {
        SourceFileBlockIterationContext(blocks.orEmpty().sortedBy { it.rangeStart }).apply(op)
    }

    /**
     * Context for iterating over the given blocks.  Allows extra navigation controls.
     */
    inner class SourceFileBlockIterationContext(
        private val blocks: List<Block>,
        var start: Int = 0,
        var i: Int = 0,
        var stack: BlockStack = mutableListOf()
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

        fun appendBlockContents(
            block: Block,
            source: SourceTemplate,
            properties: Map<String, String> = emptyMap(),
            slots: List<SourceTemplate> = emptyList()
        ) {
            val startIndex = block.rangeStart
            val indent = source.text.getIndentAt(startIndex)

            when (block) {
                is SkipBlock -> skipContents()

                is Slot -> {
                    append(slots.joinToString("\n\n$indent") {
                        it.text.indent(indent)
                    })
                }

                is CompareBlock<*> -> {
                    val contents = block.body?.let { body ->
                        source.text.substring(
                            body.rangeStart,
                            child?.rangeStart ?: body.rangeEnd
                        )
                    }?.indent(indent)

                    when (block) {
                        is OneOfBlock -> {
                            val parent = stack.lastOrNull() as? WhenBlock
                                ?: error("__equals found with no parent __when")
                            val value = properties[parent.property]
                            // TODO types
                            if (value in block.value)
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
                        is PropertyLiteral ->
                            append(when(value) {
                                is String -> "\"$value\""
                                else -> "null"
                            })
                        is IfBlock ->
                            if (value.isTruthy())
                                append(contents ?: "")
                            else skipContents()
                        is ElseBlock ->
                            if (value.isTruthy())
                                skipContents()
                            else append(contents ?: "")
                        is EachBlock ->
                            // TODO iterate on values
                            if (value.isTruthy())
                                append(contents ?: "")
                            else skipContents()
                        is WhenBlock -> {} // ignore contents of when block
                    }
                }
            }
        }
    }
}

private typealias BlockStack = MutableList<Block>

/**
 * Removes from top of stack until parent is found, executing `onNotParent` for non-matches.
 */
private fun BlockStack.removeUpToParent(next: Block, onNotParent: (Block) -> Unit): Block? {
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


class MissingPackException(pack: PackId) : Exception("Missing pack: $pack")