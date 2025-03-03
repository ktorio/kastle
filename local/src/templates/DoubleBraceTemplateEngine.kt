package org.jetbrains.kastle.templates

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.jetbrains.kastle.*

class DoubleBraceTemplateEngine(val fs: FileSystem = SystemFileSystem) {
    companion object {
        private const val IF = "if"
        private const val SLOT = "slot"
        private const val SLOTS = "slots"
        // TODO
        private const val WHEN = "when"
        private const val ELSE = "else"
        private const val FOR = "for"

        private val bracesPattern = Regex("\\{\\{([^{}]*)}}")
        private val variablePattern = Regex("[\\w_.]+")
        private val forEachPattern = Regex("for\\s+(\\w+)\\s+in\\s+([\\w_.]+)")

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
                val text = match.groupValues[1].trim()
                val indent = template.indentOf(match)
                if (variablePattern.matches(text)) {
                    when(text) {
                        ELSE -> {
                            val previousIf = stack.removeLastOrNull()
                            require(previousIf?.keyword == IF) { "Unexpected else outside if" }
                            yield(previousIf.toBlock(match))
                            stack.add(BlockMatch(match, indent, property = previousIf.property))
                        }
                        else -> yield(PropertyLiteral(
                            property = text,
                            position = SourcePosition.TopLevel(match.range, indent),
                        ))
                    }
                } else if (text.startsWith('/')) {
                    val parent = stack.removeLastOrNull() ?: error("Unexpected close term: $text")
                    require(parent.keyword == text.drop(1) || (parent.keyword == ELSE && text.drop(1) == IF)) {
                        "Unexpected close term: $text; expected /${parent.keyword}"
                    }
                    yield(parent.toBlock(match))
                } else BlockMatch(match, indent).let { blockMatch ->
                    when (blockMatch.keyword) {
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

                        IF, FOR -> stack.add(blockMatch)
                    }
                }
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
        val keyword: String = match.groupValues[1].trim().split(' ').first(),
        val property: String? = match.groupValues.getOrNull(1)?.trim()?.split(' ')?.getOrNull(1)
    ) {
        fun toBlock(endMatch: MatchResult): Block = when(keyword) {
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
            FOR -> {
                val forMatch = forEachPattern.matchEntire(match.groupValues[1].trim())
                require(forMatch != null) { "Invalid `for` block: ${match.groupValues[1].trim()}" }
                val (element, list) = forMatch.destructured
                EachBlock(
                    property = list,
                    position = SourcePosition.TopLevel(match.range.start..endMatch.range.endInclusive, indent),
                    variable = element,
                    body = SourcePosition.TopLevel(match.range.endInclusive + 1 .. endMatch.range.start - 1, indent),
                )
            }
            else -> error("Unexpected keyword: $keyword")
        }
    }

}