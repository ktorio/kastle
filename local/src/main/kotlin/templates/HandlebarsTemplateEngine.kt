package org.jetbrains.kastle.templates

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.jetbrains.kastle.*
import org.jetbrains.kastle.io.relativeTo
import org.jetbrains.kastle.utils.Expression.StringLiteral
import org.jetbrains.kastle.utils.Expression.VariableRef
import org.jetbrains.kastle.utils.ListStack
import org.jetbrains.kastle.utils.startOfLine
import org.jetbrains.kastle.utils.unwrapQuotes

class HandlebarsTemplateEngine(val fs: FileSystem = SystemFileSystem) {
    companion object {
        private const val IF = "if"
        private const val SLOT = "slot"
        private const val SLOTS = "slots"
        private const val WHEN = "when"
        private const val ELSE = "else"
        private const val EACH = "each"

        // Regex for normal (non-escaped) template blocks
        private val bracesPattern = Regex("(?<!\\\\)\\{\\{(?:#?(?<helper>[\\w_]+)\\s+)?(?<content>.*?)}}")
        // Pattern to find escaped braces
        private val escapedBracesPattern = Regex("\\\\(\\{\\{.*?}})")
        private val variablePattern = Regex("[\\w_.]+")
    }

    fun read(modulePath: Path, file: Path): SourceTemplate {
        val text = fs.source(file).buffered()
            .readByteArray()
            .decodeToString()
        return with(processEscapedBraces(text)) {
            SourceTemplate(
                text = text,
                target = "file:${file.relativeTo(modulePath).toString().removeSuffix(".hbs")}",
                blocks = findBlocks(text).toList(),
            )
        }
    }

    fun read(target: Url, text: String): SourceTemplate =
        with(processEscapedBraces(text)) {
            SourceTemplate(
                text = template,
                target = target,
                blocks = findBlocks(text).toList(),
            )
        }

    private fun processEscapedBraces(template: String): ParseContext {
        val backslashPositions = mutableListOf<Int>()
        val processedText = escapedBracesPattern.replace(template) { matchResult ->
            backslashPositions.add(matchResult.range.first)
            matchResult.groupValues[1]
        }
        return ParseContext(processedText, backslashPositions)
    }

    context(_: ParseContext)
    private fun findBlocks(template: CharSequence): Sequence<Block> {
        val stack = ListStack<BlockMatch>()
        val matches = bracesPattern.findAll(template)
        var position = 0
        var line = 1
        return sequence {
            for (match in matches) {
                line += template.substring(position, match.range.first).count { it == '\n' } // TODO use parse context
                position = match.range.last + 1

                val helper = match.groups["helper"]?.value
                val startOfLine = helper?.let {
                    template.startOfLine(match.range.first)?.minusDeletions()
                } ?: match.startAdjusted

                val text = match.groups["content"]?.value.orEmpty().trim()

                if (helper == null && variablePattern.matches(text)) {
                    when(text) {
                        ELSE -> {
                            val parent = stack.pop() ?: error("Bad else placement: missing parent")
                            when(parent.helper) {
                                IF -> {
                                    val ifBlock = parent.toBlock(match)
                                    yield(ConditionalBlock(ifBlock.position))
                                    yield(ifBlock)
                                    stack += BlockMatch(line, match, helper = ELSE)
                                }
                                null -> {
                                    require(stack.top?.helper == WHEN) { "Bad else placement: missing when parent" }
                                    yield(parent.toBlock(match, inclusive = false))
                                    stack += BlockMatch(line, match, helper = ELSE)
                                }
                            }
                        }
                        else -> yield(InlineValue(
                            expression = VariableRef(text),
                            position = BlockPosition(
                                line = line,
                                range = match.rangeAdjusted,
                            ),
                        ))
                    }
                } else if (text.startsWith('/')) {
                    val parent = stack.pop() ?: error("Unexpected close term: $text")
                    val block = parent.toBlock(match, parent.helper !in setOf(null, WHEN))
                    when(text.drop(1)) {
                        IF -> {
                            // conditional wrapper
                            require(parent.helper in setOf(IF, ELSE)) { "Unexpected close term: $text" }
                            yield(ConditionalBlock(block.position))
                            yield(block)
                        }
                        WHEN -> {
                            require(parent.helper == null || parent.helper == ELSE) { "Unexpected close term: $text" }
                            val whenParent = stack.pop() ?: error("Expected when clause: $text")
                            require(whenParent.helper == WHEN) { "Unexpected close term: $text" }
                            yield(whenParent.toBlock(match))
                            yield(block)
                        }
                        parent.helper -> yield(block)
                        else -> error("Unexpected close term: $text")
                    }
                } else {
                    BlockMatch(line, match, startOfLine).let { blockMatch ->
                        when (blockMatch.helper) {
                            SLOT -> yield(
                                NamedSlot(
                                    name = blockMatch.expression
                                        ?: throw IllegalArgumentException("Missing slot name in block: ${match.value}"),
                                    position = BlockPosition(
                                        line = line,
                                        range = match.rangeAdjusted,
                                    ),
                                )
                            )

                            SLOTS -> yield(
                                RepeatingSlot(
                                    name = blockMatch.expression
                                        ?: throw IllegalArgumentException("Missing slot name in block: ${match.value}"),
                                    position = BlockPosition(
                                        line = line,
                                        range = match.rangeAdjusted,
                                    ),
                                )
                            )

                            IF, EACH, WHEN -> stack += blockMatch

                            else -> {
                                // Value expression can only be used for when clauses
                                val parent = stack.top ?: error("Unexpected expression: ${blockMatch.match.value}")
                                when(parent.helper) {
                                    null -> {
                                        val previousValue = stack.pop() ?: error("Unexpected expression: ${blockMatch.match.value}")
                                        require(stack.top?.helper == WHEN) { "Unexpected expression: ${blockMatch.match.value}" }
                                        yield(previousValue.toBlock(match, inclusive = false))
                                        stack += blockMatch
                                    }
                                    WHEN -> stack += blockMatch
                                    else -> error("Unexpected expression: ${blockMatch.match.value}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    data class BlockMatch(
        val line: Int,
        val match: MatchResult,
        val outerStart: Int? = null,
        val helper: String? = match.groups["helper"]?.value,
        val expression: String? = match.groups["content"]?.value?.trim()?.takeIf { it.isNotEmpty() }
    ) {
        // TODO parse expression; not always variables
        //      (actually, handlebars requires variables... not sure how to handle this now...)
        context(_: ParseContext)
        fun toBlock(endMatch: MatchResult, inclusive: Boolean = true): Block = when(helper) {
            IF -> IfBlock(
                expression = expression?.let(::VariableRef) ?: throw IllegalArgumentException("Missing property name in if block: ${match.value}"),
                position = position(endMatch, inclusive),
            )
            ELSE -> ElseBlock(
                position = position(endMatch, inclusive),
            )
            WHEN -> {
                WhenBlock(
                    expression = expression?.let(::VariableRef) ?: throw IllegalArgumentException("Missing property name in if block: ${match.value}"),
                    position = position(endMatch, inclusive),
                )
            }
            EACH -> {
                ForEachBlock(
                    expression = expression?.let(::VariableRef) ?: throw IllegalArgumentException("Missing property name in if block: ${match.value}"),
                    position = position(endMatch, inclusive),
                    variable = null,
                )
            }
            null -> {
                val value = expression?.let {
                    StringLiteral(it.unwrapQuotes())
                } ?: throw IllegalArgumentException("Missing property name in if block: ${match.value}")

                WhenClauseBlock(
                    value = listOf(value),
                    position = position(endMatch, inclusive),
                )
            }
            else -> error("Unexpected keyword: $helper")
        }

        context(_: ParseContext)
        private fun position(endMatch: MatchResult, inclusive: Boolean): BlockPosition {
            val start = outerStart ?: match.startAdjusted
            val end = if (inclusive) endMatch.endAdjusted else endMatch.startAdjusted
            return BlockPosition(
                line = line,
                range = start..end,
                outer = start..end,
                inner = match.endAdjusted..endMatch.startAdjusted,
            )
        }
    }
}