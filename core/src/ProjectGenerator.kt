package org.jetbrains.kastle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.Buffer
import kotlinx.io.writeCodePointValue
import kotlinx.io.writeString
import org.jetbrains.kastle.gen.ProjectResolver
import org.jetbrains.kastle.gen.getVariables
import org.jetbrains.kastle.gen.plus
import org.jetbrains.kastle.gen.toVariableEntry
import org.jetbrains.kastle.gradle.GradleTransformation
import org.jetbrains.kastle.logging.ConsoleLogger
import org.jetbrains.kastle.logging.LogLevel
import org.jetbrains.kastle.logging.Logger
import org.jetbrains.kastle.utils.*
import org.jetbrains.kastle.utils.extension

interface ProjectGenerator {
    companion object {
        fun fromRepository(
            repository: PackRepository,
            projectResolver: ProjectResolver = ProjectResolver.Default + GradleTransformation
        ): ProjectGenerator = ProjectGeneratorImpl(repository, projectResolver)
    }

    fun generate(projectDescriptor: ProjectDescriptor): Flow<SourceFileEntry>
}

class ProjectGeneratorImpl(
    private val repository: PackRepository,
    private val projectResolver: ProjectResolver,
    private val log: Logger = ConsoleLogger(),
) : ProjectGenerator {

    override fun generate(projectDescriptor: ProjectDescriptor): Flow<SourceFileEntry> = flow {
        val project = projectResolver.resolve(projectDescriptor, repository)
        log.trace { project.name }
        log.trace {
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
                addAll(project.commonSources)
            }.distinctBy { it.target }

            for (source in moduleSources) {
                val packId = source.packId
                if (packId == null) {
                    log.warn { "Skipping ${source.target}; missing pack ID" }
                    continue
                }
                val pack = project.packs.find { it.id == packId } ?: throw MissingPackException(packId)
                val variables = project.getVariables(pack) +
                        project.toVariableEntry() +
                        module.toVariableEntry()
                if (source.condition != null) {
                    val conditionValue = source.condition.evaluate(variables)
                    if (!conditionValue.isTruthy()) {
                        log.debug { "Skipping ${source.target}; condition ${source.condition} evaluated to $conditionValue" }
                        continue
                    }
                }

                val path = module.path.appendPath(source.target.relativeFile)

                emit(SourceFileEntry(path) {
                    writeSourceFile(source, variables) {
                        log.trace { path }
                        val slots = source.blocks?.asSequence().orEmpty()
                            .flatMap { project.slotSources.lookup(pack.id, it) }
                            .toList()
                        when (source.target.extension) {
                            "kt" -> writeKotlinSourcePreamble(
                                projectDescriptor,
                                slots
                            )
                        }

                        if (source.blocks.isNullOrEmpty()) {
                            log.trace { "  Not templated; returning verbatim" }
                            append(source.text)
                            return@writeSourceFile
                        }

                        // print debug info to logs
                        if (log.level == LogLevel.TRACE) {
                            forEachBlock(source.blocks) { block ->
                                log.trace {
                                    buildString {
                                        append("  ${(block.range.toString()).padEnd(10)} ")
                                        append("(${block.level})  ")
                                        append(((block.level * 2).stringOf(' ') + block::class.simpleName).padEnd(30))
                                        append("\"${block.outerContents.replace("\n", "\\n")}\"".padEnd(100))
                                        append("\"${block.bodyContents?.replace("\n", "\\n")}\"")
                                    }
                                }
                            }
                            log.trace { "" } // log empty line
                        }

                        forEachBlock(source.blocks) { block ->
                            // exited blocks
                            val parent = stack.popUntil({ block in it }) { parent ->
                                // log.trace { "  pop $parent (${block.range} !in ${parent.range})" }
                                parent.close(parent.level)
                                if (parent.tryLoopBack())
                                    return@forEachBlock
                            }

                            // interstitial
                            append(source.text, start, block.outerStart, parent?.level ?: 0)

                            // current block
                            val skipped = appendBlockContents(
                                block = block,
                                source = source,
                                slots = project.slotSources.lookup(pack.id, block)
                            )

                            // where to go next
                            start = when {
                                child != null -> {
                                    // log.trace { "  push $block" }
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
                                    // log.trace { "  pop $parent" }
                                    parent.close(parent.level) // TODO use ancestor
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
        val Block.bodyContents: String? get() =
            source.text.substring(bodyStart, bodyEnd)

        override fun append(csq: CharSequence?): java.lang.Appendable {
            if (csq == null) return this
            buffer.writeString(csq)
            return this
        }

        override fun append(csq: CharSequence?, start: Int, end: Int): java.lang.Appendable {
            if (csq == null || start > end) return this
            // TODO shouldn't be overlapping
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

            fun Block.close(level: Int) {
                if (start < bodyEnd) {
                    val trimmedEnd = source.text.lastNonWhitespace(bodyEnd, start)
                    append(source.text, start, trimmedEnd, level)
                }
                start = rangeEnd
                // TODO synchronize push/pop
                // variables.pop()
            }

            @Suppress("UNCHECKED_CAST")
            fun appendBlockContents(
                block: Block,
                source: SourceTemplate,
                slots: List<SourceTemplate> = emptyList()
            ): Boolean =
                when (block) {
                    is SkipBlock -> skipContents()

                    is Slot -> {
                        // TODO verify
                        val indentString = (source.text.indentAt(block.rangeStart) ?: 0).stringOf(' ')
                        if (slots.isNotEmpty()) {
                            append(source.text, block.outerStart, block.rangeStart, block.level)
                            append(slots.joinToString("\n\n$indentString") {
                                it.text.indent(indentString)
                            })
                        }
                        slots.isEmpty()
                    }

                    is WhenClauseBlock -> {
                        val parent = stack.top as? WhenBlock
                            ?: error("when clause with no parent: $block")
                        val value = parent.expression.evaluate(variables)
                        val matched = value in block.value.map { it.evaluate(variables) } // TODO types?
                        conditions[parent] = matched || conditions[parent] ?: false

                        if (matched) {
                            append(source.text, block.bodyStart, child?.outerStart ?: block.bodyEnd, block.level)
                            false
                        } else skipContents()
                    }

                    is UnsafeBlock -> {
                        log.trace { "unsafe $block" }
                        append(source.text, block.outerStart, block.rangeStart, block.level)
                        append(source.text, block.bodyStart, child?.outerStart ?: block.bodyEnd, block.level)
                        false
                    }

                    is ConditionalBlock -> {
                        false
                    }

                    is ElseBlock -> {
                        val parent = stack.lastOrNull() ?: error("else without parent: $block")
                        if (conditions[parent] == false) {
                            // TODO append(source.text, block.outerStart, block.rangeStart)
                            append(source.text, block.bodyStart, child?.outerStart ?: block.bodyEnd, block.level)
                            false
                        } else skipContents()
                    }

                    is ExpressionBlock -> {
                        val value = block.expression.evaluate(variables)

                        when (block) {
                            is InlineValue -> {
                                // TODO fail on nulls?
                                when {
                                    value is String && !block.embedded -> append("\"$value\"")
                                    else -> append(value.toString())
                                }
                                false
                            }
                            is IfBlock -> {
                                val parent = stack.lastOrNull() ?: error("if without parent: $block")
                                val condition = value.isTruthy().also { conditions[parent] = it }
                                if (condition) {
                                    // TODO append(source.text, block.outerStart, block.rangeStart)
                                    append(source.text, block.bodyStart, child?.outerStart ?: block.bodyEnd, block.level)
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
                                    append(source.text, block.bodyStart, child?.outerStart ?: block.bodyEnd, block.level)
                                    false
                                } else skipContents()
                            }

                            // contents provided by children
                            is WhenBlock -> true
                        }
                    }
                }
        }
    }
}

class MissingPackException(pack: PackId) : Exception("Missing pack: $pack")