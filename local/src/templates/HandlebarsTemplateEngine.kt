package org.jetbrains.kastle.templates

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.jetbrains.kastle.*
import org.jetbrains.kastle.BlockPosition.Companion.bumpEnd
import org.jetbrains.kastle.io.relativeTo
import org.jetbrains.kastle.utils.Expression.StringLiteral
import org.jetbrains.kastle.utils.Expression.VariableRef
import org.jetbrains.kastle.utils.ListStack
import org.jetbrains.kastle.utils.indentAt
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

        private val bracesPattern = Regex("\\{\\{(?:#?(?<helper>[\\w_]+)\\s+)?(?<content>.*?)}}")
        private val variablePattern = Regex("[\\w_.]+")
    }

    fun read(modulePath: Path, file: Path): SourceTemplate {
        val template = fs.source(file).buffered()
            .readByteArray()
            .decodeToString()
        return SourceTemplate(
            text = template,
            target = "file:${file.relativeTo(modulePath).toString().removeSuffix(".hbs")}",
            blocks = findBlocks(template).toList(),
        )
    }

    fun read(target: Url, text: String): SourceTemplate =
        SourceTemplate(
            text = text,
            target = target,
            blocks = findBlocks(text).toList()
        )

    private fun findBlocks(template: String): Sequence<Block> {
        val stack = ListStack<BlockMatch>()
        val matches = bracesPattern.findAll(template)
        return sequence {
            for (match in matches) {
                val helper = match.groups["helper"]?.value
                val startOfLine = // template.startOfLine(match.range.start) ?: match.range.start
                     if (helper != null) template.startOfLine(match.range.start) ?: match.range.start else match.range.start
                val text = match.groups["content"]?.value.orEmpty().trim()
                // if when clause or empty lines from parent
                val indent = stack.top?.takeIf { parent ->
                    parent.helper == null || isEmptyLines(template, parent, match)
                }?.indent ?: template.indentAt(match.range.start) ?: -1

                if (helper == null && variablePattern.matches(text)) {
                    when(text) {
                        ELSE -> {
                            val parent = stack.pop() ?: error("Bad else placement: missing parent")
                            when(parent.helper) {
                                IF -> {
                                    val ifBlock = parent.toBlock(match)
                                    yield(ConditionalBlock(ifBlock.position)) // TODO position not right...
                                    yield(ifBlock)
                                    stack += BlockMatch(match, indent, helper = ELSE)
                                }
                                null -> {
                                    require(stack.top?.helper == WHEN) { "Bad else placement: missing when parent" }
                                    yield(parent.toBlock(match, inclusive = false))
                                    stack += BlockMatch(match, indent, helper = ELSE)
                                }
                            }
                        }
                        else -> yield(InlineValue(
                            expression = VariableRef(text),
                            position = BlockPosition(
                                range = match.range.bumpEnd(),
                                indent = indent,
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
                    BlockMatch(match, indent, startOfLine).let { blockMatch ->
                        when (blockMatch.helper) {
                            SLOT -> yield(
                                NamedSlot(
                                    name = blockMatch.expression
                                        ?: throw IllegalArgumentException("Missing slot name in block: ${match.value}"),
                                    position = BlockPosition(
                                        range = match.range.bumpEnd(),
                                        indent = indent,
                                    ),
                                )
                            )

                            SLOTS -> yield(
                                RepeatingSlot(
                                    name = blockMatch.expression
                                        ?: throw IllegalArgumentException("Missing slot name in block: ${match.value}"),
                                    position = BlockPosition(
                                        range = match.range.bumpEnd(),
                                        indent = indent,
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

    private fun isEmptyLines(template: String, parent: BlockMatch, child: MatchResult): Boolean {
        val endOfParent = parent.match.range.endInclusive + 1
        val startOfLine = template.startOfLine(child.range.start, ignoreNonWhitespace = true) ?: return false
        if (startOfLine <= endOfParent) return true
        return template.substring(endOfParent, startOfLine).isBlank()
    }

    data class BlockMatch(
        val match: MatchResult,
        val indent: Int,
        val outerStart: Int = match.range.start,
        val helper: String? = match.groups["helper"]?.value,
        val expression: String? = match.groups["content"]?.value?.trim()?.takeIf { it.isNotEmpty() }
    ) {
        // TODO parse expression; not always variables
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
                // TODO parse expression; not always string literal
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

        private fun position(endMatch: MatchResult, inclusive: Boolean): BlockPosition = BlockPosition(
            range = outerStart..if (inclusive) endMatch.range.endInclusive + 1 else endMatch.range.start,
            outer = outerStart..if (inclusive) endMatch.range.endInclusive + 1 else endMatch.range.start,
            inner = match.range.endInclusive + 1..endMatch.range.start,
            indent = indent,
        )
    }

}