package org.jetbrains.kastle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.Buffer
import kotlinx.io.writeCodePointValue
import kotlinx.io.writeString
import org.jetbrains.kastle.gen.getVariables
import org.jetbrains.kastle.gen.load
import org.jetbrains.kastle.gen.toVariableEntry
import org.jetbrains.kastle.utils.*
import kotlin.collections.emptyList

interface ProjectGenerator {
    companion object {
        fun fromRepository(repository: PackRepository): ProjectGenerator =
            ProjectGeneratorImpl(repository)
    }

    suspend fun generate(projectDescriptor: ProjectDescriptor): Flow<SourceFileEntry>
}

internal class ProjectGeneratorImpl(
    private val repository: PackRepository,
    private val log: (() -> String) -> Unit = { println(it()) }
) : ProjectGenerator {
    companion object {
        val startOfLine = Regex("\n\\s*")
        val nonEmptyLine = Regex("\n[\t ]*\\S")
    }

    override suspend fun generate(projectDescriptor: ProjectDescriptor): Flow<SourceFileEntry> = flow {
        val project = projectDescriptor.load(repository)
        log { project.name }
        log {
            buildString {
                for (module in project.moduleSources.modules) {
                    appendLine("  ${module.path.ifEmpty { "<root>" }}")
                    append("    dependencies:")
                    if (module.dependencies.isEmpty()) appendLine(" <none>") else appendLine()
                    module.dependencies.forEach { appendLine("      - $it") }
                    append("    testDependencies:")
                    if (module.testDependencies.isEmpty()) appendLine(" <none>") else appendLine()
                    module.testDependencies.forEach { appendLine("      - $it") }
                }
            }
        }
        for (module in project.moduleSources.modules) {
            val moduleSources = buildList {
                addAll((module.sources.filter { it.target.protocol == "file" }))
                if (!module.ignoreCommon)
                    addAll(project.commonSources)
            }.distinctBy { it.target }

            for (source in moduleSources) {
                val packId = source.packId
                if (packId == null) {
                    log { "Skipping ${source.target}; missing pack ID" }
                    continue
                }
                val pack = project.packs.find { it.id == packId } ?: throw MissingPackException(packId)
                val variables = project.getVariables(pack) +
                        project.toVariableEntry() +
                        module.toVariableEntry()
                if (source.condition != null) {
                    val conditionValue = source.condition.evaluate(variables)
                    if (!conditionValue.isTruthy()) {
                        log { "Skipping ${source.target}; condition ${source.condition} evaluated to $conditionValue" }
                        continue
                    }
                }

                val path = module.path.appendPath(source.target.relativeFile)
                emit(SourceFileEntry(path) {
                    writeSourceFile(source, variables) {
                        writeSourcePreamble(
                            pack.id,
                            source,
                            project.slotSources,
                            projectDescriptor
                        )
                        log { source.target }
                        if (source.blocks.isNullOrEmpty()) {
                            append(source.text)
                            return@writeSourceFile
                        }

                        // print debug info to logs
                        forEachBlock(source.blocks) { block ->
                            log {
                                buildString {
                                    append("  ${block.range.toString().padEnd(12)} ")
                                    append((block.indent.stringOf(' ') + block::class.simpleName).padEnd(30))
                                    append("\"${block.outerContents.replace("\n", "\\n")}\"".padEnd(100))
                                    append("\"${block.bodyContents?.replace("\n", "\\n")}\"")
                                }
                            }
                        }
                        log { "" } // log empty line

                        forEachBlock(source.blocks) { block ->
                            // exited blocks
                            val ancestor = stack.findLast { block in it }
                            stack.popUntil({ block in it }) { parent ->
                                parent.close(ancestor?.indent ?: block.indent)
                                if (parent.tryLoopBack())
                                    return@forEachBlock
                            }

                            // interstitial
                            append(source.text, start, block.outerStart, ancestor?.indent)

                            // current block
                            val skipped = appendBlockContents(
                                indent = ancestor?.indent ?: block.indent,
                                block = block,
                                source = source,
                                slots = project.slotSources.lookup(pack.id, block)
                            )

                            // where to go next
                            start = when {
                                child != null -> {
                                    log { "  push $block" }
                                    stack += block
                                    child!!.outerStart
                                }
                                else -> block.rangeEnd
                            }

                            // Remove empty lines after skipped blocks
                            if (skipped && start < source.text.length) {
                                val initial = start
                                var next = source.text[start]
                                if (next.isWhitespace()) {
                                    while (next.isWhitespace() && start < source.text.length) {
                                        next = source.text[++start]
                                    }
                                    while (next != '\n' && start > initial) {
                                        next = source.text[--start]
                                    }
                                }
                            }

                            if (isLast()) {
                                // trailing ancestors
                                stack.popSequence().forEach { parent ->
                                    parent.close(parent.indent) // TODO use ancestor
                                    if (parent.tryLoopBack())
                                        return@forEachBlock
                                }

                                // trailing content
                                append(source.text, start, source.text.length)
                            }
                        }
                    }
                })
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

    private fun writeSourceFile(
        source: SourceTemplate,
        variables: Variables,
        action: SourceFileWriteContext.() -> Unit
    ): Buffer = SourceFileWriteContext(source, variables).apply(action).buffer

    /**
     * Writer context for building a source file.
     */
    private inner class SourceFileWriteContext(
        val source: SourceTemplate,
        val variables: Variables,
        val buffer: Buffer = Buffer(),
    ): Appendable {

        val Block.outerContents: String get() =
            source.text.substring(outerStart, outerEnd)
        val Block.contents: String get() =
            source.text.substring(rangeStart, rangeEnd)
        val Block.bodyContents: String? get() =
            source.text.substring(bodyStart, bodyEnd)

        override fun append(csq: CharSequence?): java.lang.Appendable {
            if (csq == null) return this
            buffer.writeString(csq)
            return this
        }

        /**
         * Replaces any currently indented lines with the new indent.
         * TODO need to find baseline first then replace only that much
         */
        fun append(csq: CharSequence?, start: Int, end: Int, indent: Int?, trimStart: Boolean = false, trimEnd: Boolean = false): java.lang.Appendable {
            when(indent) {
                null, -1 -> append(csq, start, end)
                else -> {
                    val indentString = indent.stringOf(' ')
                    var matchStart = start
                    for (lineStart in startOfLine.findAll(source.text, start)) {
                        if (lineStart.range.first >= end) break
                        if (trimStart && lineStart.range.first == start) {
                            matchStart = lineStart.range.last + 1
                            continue
                        }
                        append(source.text, matchStart, lineStart.range.first)
                        if (trimEnd && lineStart.range.last == end - 1) return this
                        append('\n').append(indentString)
                        matchStart = lineStart.range.last + 1
                    }
                    if (matchStart < end)
                        append(source.text, matchStart, end)
                }
            }
            return this
        }

        override fun append(csq: CharSequence?, start: Int, end: Int): java.lang.Appendable {
            if (csq == null) return this
            require(start <= end) {
                "Overlap $start > $end: ${csq.substring(end, start)}"
            }
            buffer.writeString(csq, start, end)
            return this
        }

        override fun append(c: Char): java.lang.Appendable {
            buffer.writeCodePointValue(c.code)
            return this
        }

        fun forEachBlock(blocks: List<Block>?, op: SourceFileBlockIterationContext.(Block) -> Unit): SourceFileBlockIterationContext =
            SourceFileBlockIterationContext(
                blocks = blocks.orEmpty().sortedBy { it.rangeStart },
                variables = variables.copy(),
            ).also { context ->
                while (context.i < context.blocks.size) {
                    context.op(context.current)
                    context.i++
                }
            }

        /**
         * Context for iterating over the given blocks.  Allows extra navigation controls.
         */
        inner class SourceFileBlockIterationContext(
            val blocks: List<Block>,
            val variables: Variables,
            var start: Int = 0,
            var i: Int = 0,
            var stack: Stack<Block> = Stack.of()
        ): Appendable by this {
            val current: Block get() = blocks[i]
            val child: Block? get() = next?.takeIf { it in blocks[i] }
            val next: Block? get() = blocks.getOrNull(i + 1)
            val loops: MutableMap<DeclaringBlock, MutableList<*>> = mutableMapOf()
            val conditions: MutableMap<Block, Boolean> = mutableMapOf()

            fun skipContents(): Boolean {
                val current = blocks[i]
                while (next in current)
                    i++
                return true
            }

            fun isLast() =
                i == blocks.lastIndex

            /**
             * Return to the start of the loop for the next element.
             */
            fun Block.tryLoopBack(): Boolean {
                if (this !is DeclaringBlock) return false
                val items = loops[this] ?: return false
                if (items.isEmpty()) {
                    loops -= this
                    return false
                }
                i = blocks.indexOf(this) - 1
                start = outerStart
                return true
            }

            // TODO the indent should be based on this block's parents
            fun Block.close(indent: Int) {
                log { "  pop $this" }
                if (start < bodyEnd) {
                    append(source.text, start, bodyEnd, indent, trimEnd = true)
                }
                start = rangeEnd
                // TODO synchronize push/pop
                // variables.pop()
            }

            fun appendBlockContents(
                indent: Int,
                block: Block,
                source: SourceTemplate,
                slots: List<SourceTemplate> = emptyList()
            ): Boolean =
                when (block) {
                    is SkipBlock -> skipContents()

                    is Slot -> {
                        val indentString = indent.stringOf(' ')
                        append(source.text, block.outerStart, block.rangeStart)
                        append(slots.joinToString("\n\n$indentString") {
                            it.text.indent(indentString)
                        })
                        slots.isEmpty()
                    }

                    is WhenClauseBlock -> {
                        val end = child?.outerStart ?: block.bodyEnd

                        val parent = stack.lastOrNull() as? WhenBlock
                            ?: error("when clause with no parent: $block")
                        val value = parent.expression.evaluate(variables)

                        // TODO types?
                        if (value in block.value.map { it.evaluate(variables) }) {
                            append(source.text, block.outerStart, block.rangeStart)
                            append(source.text, block.bodyStart, end, indent)
                            false
                        } else skipContents()
                    }

                    is UnsafeBlock -> {
                        append(source.text, block.outerStart, block.rangeStart)
                        append(source.text, block.bodyStart, child?.outerStart ?: block.bodyEnd, indent)
                        false
                    }

                    is ConditionalBlock -> {
                        append(source.text, block.outerStart, block.rangeStart)
                        false
                    }

                    is ElseBlock -> {
                        val parent = stack.lastOrNull() ?: error("else without parent: $block")
                        if (conditions[parent] == false) {
                            append(source.text, block.outerStart, block.rangeStart)
                            append(source.text, block.bodyStart, child?.outerStart ?: block.bodyEnd, indent)
                            false
                        } else skipContents()
                    }

                    is ExpressionBlock -> {
                        val value = block.expression.evaluate(variables)

                        when (block) {
                            is InlineValue -> {
                                append(
                                    when {
                                        value is String && !block.embedded -> "\"$value\""
                                        else -> value.toString()
                                    }
                                )
                                false
                            }
                            is IfBlock -> {
                                val parent = stack.lastOrNull() ?: error("if without parent: $block")
                                val condition = value.isTruthy().also { conditions[parent] = it }
                                if (condition) {
                                    append(source.text, block.outerStart, block.rangeStart)
                                    append(source.text, block.bodyStart, child?.outerStart ?: block.bodyEnd, indent)
                                    false
                                } else skipContents()
                            }

                            is ForEachBlock -> {
                                val list = loops[block] ?: (value as? Iterable<*> ?: emptyList<Any>()).toMutableList()
                                if (list.isNotEmpty()) {
                                    val element = list.removeFirst()
                                    when(val variable = block.variable) {
                                        null -> {
                                            val elementFields = element as? Map<String, Any> ?: emptyMap()
                                            variables += mapOf("this" to element, *elementFields.toList().toTypedArray())
                                        }
                                        else -> variables += variable to element
                                    }
                                    loops[block] = list
                                    append(source.text, block.bodyStart, child?.outerStart ?: block.bodyEnd, indent)
                                    false
                                } else skipContents()
                            }
                            // contents provided by children
                            is WhenBlock -> false
                        }
                    }
                }
        }
    }
}

class MissingPackException(pack: PackId) : Exception("Missing pack: $pack")