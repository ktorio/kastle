package org.jetbrains.kastle.templates

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.jetbrains.kastle.*

class HandlebarsTemplateEngine(val fs: FileSystem = SystemFileSystem) {
    companion object {
        private const val IF = "if"
        private const val SLOT = "slot"
        private const val SLOTS = "slots"
        // TODO
        private const val WHEN = "when"
        private const val ELSE = "else"
        private const val EACH = "each"

        private val bracesPattern = Regex("\\{\\{(?:#(?<helper>[\\w_]+))?(?<content>.*?)}}")
        private val variablePattern = Regex("[\\w_.]+")

    }

    fun read(file: Path): SourceTemplate {
        val template = fs.source(file).buffered()
            .readByteArray()
            .decodeToString()
        return SourceTemplate(
            text = template,
            target = "file:$file",
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
        val stack = mutableListOf<BlockMatch>()
        val matches = bracesPattern.findAll(template)
        return sequence {
            for (match in matches) {
                val helper = match.groups["helper"]?.value
                val text = match.groups["content"]?.value.orEmpty().trim()
                val indent = template.indentOf(match)
                if (helper == null && variablePattern.matches(text)) {
                    when(text) {
                        ELSE -> {
                            val previousIf = stack.removeLastOrNull()
                            require(previousIf?.helper == IF) { "Unexpected else outside if" }
                            yield(previousIf.toBlock(match))
                            stack.add(BlockMatch(match, indent, helper = ELSE, property = previousIf.property))
                        }
                        else -> yield(PropertyLiteral(
                            property = text,
                            position = SourcePosition.TopLevel(match.range, indent),
                        ))
                    }
                } else if (text.startsWith('/')) {
                    val parent = stack.removeLastOrNull() ?: error("Unexpected close term: $text")
                    require(parent.helper == text.drop(1) || (parent.helper == ELSE && text.drop(1) == IF)) {
                        "Unexpected close term: $text; expected /${parent.helper}"
                    }
                    yield(parent.toBlock(match))
                } else if (helper != null) {
                    BlockMatch(match, indent).let { blockMatch ->
                        when (blockMatch.helper) {
                            SLOT -> yield(
                                NamedSlot(
                                    name = blockMatch.property
                                        ?: throw IllegalArgumentException("Missing slot name in block: ${match.value}"),
                                    position = SourcePosition.TopLevel(match.range, indent),
                                )
                            )

                            SLOTS -> yield(
                                RepeatingSlot(
                                    name = blockMatch.property
                                        ?: throw IllegalArgumentException("Missing slot name in block: ${match.value}"),
                                    position = SourcePosition.TopLevel(match.range, indent),
                                )
                            )

                            IF, EACH -> stack.add(blockMatch)
                        }
                    }
                } else throw IllegalArgumentException("Unexpected handlebars block: ${match.value}")
            }
        }
    }

    private fun String.indentOf(match: MatchResult): Int =
        lastIndexOf('\n', match.range.start).takeIf { it >= 0 }?.let { newLineIndex ->
            subSequence(newLineIndex, match.range.start).count { it == ' ' }
        } ?: 0

    data class BlockMatch(
        val match: MatchResult,
        val indent: Int,
        val helper: String = match.groups["helper"]!!.value,
        val property: String? = match.groups["content"]?.value?.trim()?.takeIf { it.isNotEmpty() }
    ) {
        fun toBlock(endMatch: MatchResult): Block = when(helper) {
            IF -> IfBlock(
                property = property ?: throw IllegalArgumentException("Missing property name in if block: ${match.value}"),
                position = SourcePosition.TopLevel(match.range.start..endMatch.range.endInclusive, indent),
                body = SourcePosition.TopLevel(match.range.endInclusive + 1 .. endMatch.range.start - 1, indent),
            )
            ELSE -> ElseBlock(
                property = property ?: throw IllegalArgumentException("Missing property name in if block: ${match.value}"),
                position = SourcePosition.TopLevel(match.range.start..endMatch.range.endInclusive, indent),
                body = SourcePosition.TopLevel(match.range.endInclusive + 1 .. endMatch.range.start - 1, indent),
            )
            EACH -> {
                EachBlock(
                    property = property ?: throw IllegalArgumentException("Missing property name in if block: ${match.value}"),
                    position = SourcePosition.TopLevel(match.range.start..endMatch.range.endInclusive, indent),
                    variable = null,
                    body = SourcePosition.TopLevel(match.range.endInclusive + 1 .. endMatch.range.start - 1, indent),
                )
            }
            else -> error("Unexpected keyword: $helper")
        }
    }

}