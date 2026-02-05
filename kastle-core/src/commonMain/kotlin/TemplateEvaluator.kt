package org.jetbrains.kastle

import kotlinx.io.Buffer
import kotlinx.io.bytestring.decodeToString
import kotlinx.io.readString
import kotlinx.io.writeCodePointValue
import kotlinx.io.writeString
import org.jetbrains.kastle.kotlin.KT_EXTENSION
import org.jetbrains.kastle.kotlin.KT_SCRIPT_EXTENSION
import org.jetbrains.kastle.kotlin.writeKotlinSourcePreamble
import org.jetbrains.kastle.logging.ConsoleLogger
import org.jetbrains.kastle.logging.LogLevel
import org.jetbrains.kastle.logging.Logger
import org.jetbrains.kastle.utils.ListStack
import org.jetbrains.kastle.utils.Stack
import org.jetbrains.kastle.utils.Variables
import org.jetbrains.kastle.utils.addVariableOrScope
import org.jetbrains.kastle.utils.append
import org.jetbrains.kastle.utils.bodyEnd
import org.jetbrains.kastle.utils.bodyStart
import org.jetbrains.kastle.utils.contains
import org.jetbrains.kastle.utils.extension
import org.jetbrains.kastle.utils.indent
import org.jetbrains.kastle.utils.indentAt
import org.jetbrains.kastle.utils.isTruthy
import org.jetbrains.kastle.utils.lastNonWhitespace
import org.jetbrains.kastle.utils.level
import org.jetbrains.kastle.utils.lineNumber
import org.jetbrains.kastle.utils.outerEnd
import org.jetbrains.kastle.utils.outerStart
import org.jetbrains.kastle.utils.popSequence
import org.jetbrains.kastle.utils.popUntil
import org.jetbrains.kastle.utils.positionPrefix
import org.jetbrains.kastle.utils.rangeEnd
import org.jetbrains.kastle.utils.rangeStart
import org.jetbrains.kastle.utils.stringOf
import kotlin.text.substring

/**
 * The `TemplateEvaluator` class is responsible for processing template sources
 * and generating output buffers based on the provided templates, configuration,
 * and slot data.
 *
 * @property log An instance of `Logger` used to output logs for processing steps
 * and debug information.
 */
class TemplateEvaluator(
    private val log: Logger = ConsoleLogger(level = LogLevel.TRACE)
) {
    companion object {
        /**
         * Handy function for testing.
         */
        fun SourceTemplate.toString(
            evaluator: TemplateEvaluator = TemplateEvaluator(),
            groupId: String = "com.example",
            packId: PackId = PackId("com.example", "project"),
            variables: Variables = ListStack(),
            slots: SourcesByUrl = emptyMap(),
        ) = evaluator.evaluateToBuffer(
            this,
            groupId,
            packId,
            variables,
            slots
        ).readString()
    }

    fun evaluateToBuffer(
        template: SourceTemplate,
        groupId: String,
        packId: PackId,
        variables: Variables,
        slots: SourcesByUrl,
    ): Buffer = withSourceContext(template.text) {
        log.trace { template.target.toString() }
        val slotImports = template.blocks?.asSequence().orEmpty()
            .flatMap { block -> slots.lookup(packId, block) }
            .filterIsInstance<SourceTemplate>()
            .flatMap { it.imports?.imports.orEmpty() }
            .toList()
        val startPosition = when (template.target.extension) {
            KT_EXTENSION, KT_SCRIPT_EXTENSION ->
                writeKotlinSourcePreamble(
                    groupId = groupId,
                    target = template.target.toString(),
                    source = template,
                    extraImports = slotImports,
                    skipPackage = template.target.extension == KT_SCRIPT_EXTENSION,
                )
            else -> 0
        }

        if (template.blocks.isNullOrEmpty()) {
            log.trace { "  Not templated; returning verbatim" }
            append(template.text, startPosition, template.text.length)
            return@withSourceContext
        }

        // print debug info to logs
        if (log.level == LogLevel.TRACE) {
            forEachBlock(template.blocks, startPosition, variables) { block ->
                log.trace {
                    buildString {
                        append("  ${block.lineNumber.toString().padEnd(5)} ")
                        append(((block.level * 2).stringOf(' ') + block::class.simpleName).padEnd(30))
                        append("\"${block.outerContents.replace("\n", "\\n")}\"".padEnd(100))
                        append("\"${block.bodyContents.replace("\n", "\\n")}\"")
                    }
                }
            }
            log.trace { "" } // log empty line
        }

        forEachBlock(template.blocks, startPosition, variables) { block ->
            // exited blocks
            val parent = stack.popUntil({ block in it }) { parent ->
                parent.close()
                if (parent.tryLoopBack())
                    return@forEachBlock
            }

            // interstitial
            append(template.text, start, block.outerStart, parent?.level ?: 0)

            // current block
            val skipped = appendBlockContents(
                block = block,
                source = template,
                slots = slots.lookup(packId, block).map { sourceFile ->
                    when(sourceFile) {
                        is SourceTemplate -> evaluateToBuffer(
                            sourceFile,
                            groupId,
                            sourceFile.packId ?: packId,
                            variables,
                            slots
                        ).readString()
                        is StaticSource -> sourceFile.contents.decodeToString()
                    }
                }
            )

            // where to go next
            start = when {
                child != null -> {
                    stack.push(block)
                    child!!.outerStart
                }

                else -> block.rangeEnd
            }

            // Remove empty lines after skipped blocks
            if (skipped && start < template.text.length) {
                val initial = start
                var next = template.text[start]
                if (next.isWhitespace()) {
                    while (next.isWhitespace() && start + 1 < template.text.length) {
                        next = template.text[++start]
                    }
                    while (next != '\n' && start > initial) {
                        next = template.text[--start]
                    }
                }
            }

            if (isLast()) {
                // trailing ancestors
                stack.popSequence().forEach { parent ->
                    parent.close()
                    if (parent.tryLoopBack())
                        return@forEachBlock
                }

                // trailing content
                append(template.text, start, template.text.length)
            }
        }
    }

    private fun withSourceContext(body: CharSequence, action: SourceFileWriteContext.() -> Unit): Buffer =
        SourceFileWriteContext(log, body)
            .apply(action)
            .buffer
}

/**
 * Writer context for building a source file.
 */
internal class SourceFileWriteContext(
    val log: Logger,
    val body: CharSequence,
    val buffer: Buffer = Buffer(),
) : Appendable {
    val Block.outerContents: String
        get() = body.substring(outerStart, outerEnd)
    val Block.bodyContents: String
        get() = body.substring(bodyStart, bodyEnd)

    override fun append(value: CharSequence?): Appendable {
        if (value == null) return this
        buffer.writeString(value)
        return this
    }

    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable {
        if (value == null) return this
        check(startIndex <= endIndex) {
            "Overlap $startIndex > $endIndex: ${value.substring(endIndex, startIndex)}"
        }
        check(endIndex <= value.length) {
            "End index out of bounds: $endIndex > ${value.length}"
        }
        buffer.writeString(value, startIndex, endIndex)
        return this
    }

    override fun append(c: Char): Appendable {
        buffer.writeCodePointValue(c.code)
        return this
    }

    fun forEachBlock(
        blocks: List<Block>?,
        startPosition: Int,
        variables: Variables,
        op: SourceFileBlockIterationContext.(Block) -> Unit
    ): SourceFileBlockIterationContext =
        SourceFileBlockIterationContext(
            blocks = blocks.orEmpty().sortedBy { it.rangeStart },
            variables = variables.copy(),
            start = startPosition,
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
        var start: Int,
        var i: Int = 0,
        var stack: Stack<Block> = Stack.of()
    ) : Appendable by this {
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

        fun Block.close() {
            if (start < bodyEnd) {
                // trim the contents of structural blocks when inlined, else extra whitespace will appear
                val trimmedEnd = body.lastNonWhitespace(bodyEnd, start)
                append(body, start, trimmedEnd, level)
            }
            start = rangeEnd

            if (this is DeclaringBlock)
                variables.pop()
        }

        @Suppress("UNCHECKED_CAST")
        fun appendBlockContents(
            block: Block,
            source: SourceTemplate,
            slots: List<String> = emptyList()
        ): Boolean =
            when (block) {
                is SkipBlock -> skipContents()

                is Slot -> {
                    val indentSize = ((source.text.indentAt(block.rangeStart) ?: 0) - block.level * 4).coerceAtLeast(0)
                    val indentString = indentSize.stringOf(' ')
                    if (slots.isNotEmpty()) {
                        append(source.text, block.outerStart, block.rangeStart, block.level)
                        append(slots.joinToString("\n$indentString") { slotText ->
                            slotText.trimIndent().indent(indentString)
                        })
                    }
                    slots.isEmpty()
                }

                is WhenClauseBlock -> {
                    val parent = stack.top as? WhenBlock ?: error("when clause with no parent: $block")
                    val value = parent.expression.evaluate(variables)
                    val matched = value in block.value.map { it.evaluate(variables) } // TODO types?
                    conditions[parent] = matched || conditions[parent] ?: false

                    if (matched) {
                        append(source.text, block.bodyStart, child?.outerStart ?: block.bodyEnd, block.level)
                        false
                    } else skipContents()
                }

                is UnsafeBlock -> {
                    log.trace { "  ${block.positionPrefix} UNSAFE $block" }
                    append(source.text, block.outerStart, block.rangeStart, block.level)
                    append(source.text, block.bodyStart, child?.outerStart ?: block.bodyEnd, block.level)
                    false
                }

                is ElseBlock -> {
                    val parent = stack.top ?: error("else without parent: $block")
                    val ifResult = conditions[parent]
                    log.trace { "  ${block.positionPrefix} ELSE  ^${parent.lineNumber} -> !$ifResult -> ${ifResult == false}" }
                    if (ifResult == false) {
                        append(source.text, block.bodyStart, child?.outerStart ?: block.bodyEnd, block.level)
                        false
                    } else skipContents()
                }

                // details handled by children
                is ConditionalBlock -> false

                is ExpressionBlock -> {
                    val value = try {
                        block.expression.evaluate(variables)
                    } catch (e: Exception) {
                        throw IllegalArgumentException(
                            "Failed to evaluate expression `${block.expression}` in ${source.target}",
                            e
                        )
                    }

                    when (block) {
                        is InlineValue -> {
                            log.trace { "  ${block.positionPrefix} VALUE ${block.expression} -> $value" }
                            when {
                                value is String && !block.embedded -> append("\"$value\"")
                                else -> append(value.toString())
                            }
                            false
                        }

                        is IfBlock -> {
                            val parent = stack.top
                                ?: error("if without parent: $block")
                            log.trace { "  ${block.positionPrefix} IF    ^${parent.lineNumber} ${block.expression} -> !!$value -> ${value.isTruthy()}" }
                            val condition = value.isTruthy().also {
                                conditions[parent] = it
                            }
                            if (condition) {
                                append(source.text, block.bodyStart, child?.outerStart ?: block.bodyEnd, block.level)
                                false
                            } else skipContents()
                        }

                        is ForEachBlock -> {
                            log.trace { "  ${block.positionPrefix} EACH  ${block.expression} -> $value" }
                            when (value) {
                                null -> skipContents()
                                is Iterable<*> -> {
                                    val list = loops[block] ?: value.toMutableList()
                                    if (list.isNotEmpty()) {
                                        val element = list.removeFirst()
                                        variables.addVariableOrScope(block.variable to element)
                                        loops[block] = list
                                        append(
                                            source.text,
                                            block.bodyStart,
                                            child?.outerStart ?: block.bodyEnd,
                                            block.level
                                        )
                                        false
                                    } else skipContents()
                                }

                                else -> error("Expected '${block.expression}' to be Iterable, but was $value")
                            }
                        }
                        // details handled by direct children
                        is WhenBlock -> {
                            log.trace { "  ${block.positionPrefix} WHEN  ${block.expression} -> $value" }
                            false
                        }
                    }
                }
            }
    }
}

private fun SourcesByUrl.lookup(packId: PackId, block: Block): List<SourceFile> {
    if (block !is Slot)
        return emptyList()

    val keys = listOf("slot://$packId/${block.name}", "slot:${block.name}")
    val values = keys.flatMap { get(it).orEmpty() }
    if (values.isEmpty()) {
        when (block.requirement) {
            Requirement.REQUIRED ->
                throw IllegalArgumentException("Missing slot://$packId/${block.name}")

            Requirement.OMITTED -> return emptyList()
            Requirement.OPTIONAL -> {}
        }
    }
    require(block is RepeatingSlot || values.size <= 1) {
        "More than one target for non-repeating slot://$packId/${block.name}"
    }
    return values.sortedWith(
        compareBy<SourceFile> { (it as? SourceTemplate)?.priority ?: Int.MAX_VALUE }
            .thenBy { (it as? SourceTemplate)?.text }
    )
}