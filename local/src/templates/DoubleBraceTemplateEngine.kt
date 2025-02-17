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
        private const val ELSE = "else"
        private const val FOR = "for"

        private val bracesPattern = Regex("\\{\\{([^{}]*)}}")
        private val wordPattern = Regex("\\w+")
        private val forEachPattern = Regex("for\\s+(\\w+)\\s+in\\s+(\\w+)")

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

    fun read(path: String, text: String): SourceTemplate =
        SourceTemplate(
            text = text,
            target = "file:$path",
            blocks = findBlocks(text).toList()
        )

    private fun findBlocks(template: String): Sequence<Block> {
        val stack = mutableListOf<BlockMatch>()
        val matches = bracesPattern.findAll(template)
        return sequence {
            for (match in matches) {
                val text = match.groupValues[1].trim()
                if (wordPattern.matches(text)) {
                    when(text) {
                        ELSE -> {
                            val previousIf = stack.removeLastOrNull()
                            require(previousIf?.keyword == IF) { "Unexpected else outside if" }
                            yield(previousIf.toBlock(match))
                            stack.add(BlockMatch(match, property = previousIf.property))
                        }
                        else -> yield(PropertyLiteral(
                            property = text,
                            position = SourcePosition.TopLevel(match.range),
                        ))
                    }
                } else if (text.startsWith('/')) {
                    val parent = stack.removeLastOrNull() ?: error("Unexpected close term: $text")
                    require(parent.keyword == text.drop(1) || (parent.keyword == ELSE && text.drop(1) == IF)) {
                        "Unexpected close term: $text; expected /${parent.keyword}"
                    }
                    yield(parent.toBlock(match))
                } else BlockMatch(match).let { blockMatch ->
                    when (blockMatch.keyword) {
                        SLOT -> yield(
                            NamedSlot(
                                name = blockMatch.property
                                    ?: throw IllegalArgumentException("Missing slot name in block: ${match.value}"),
                                position = SourcePosition.TopLevel(match.range),
                            )
                        )

                        SLOTS -> yield(
                            RepeatingSlot(
                                name = blockMatch.property
                                    ?: throw IllegalArgumentException("Missing slot name in block: ${match.value}"),
                                position = SourcePosition.TopLevel(match.range),
                            )
                        )

                        IF, FOR -> stack.add(blockMatch)
                    }
                }
            }
        }
    }

    data class BlockMatch(
        val match: MatchResult,
        val keyword: String = match.groupValues[1].trim().split(' ').first(),
        val property: String? = match.groupValues.getOrNull(1)?.trim()?.split(' ')?.getOrNull(1)
    ) {

        fun toBlock(endMatch: MatchResult): Block = when(keyword) {
            IF -> IfBlock(
                property = property ?: throw IllegalArgumentException("Missing property name in if block: ${match.value}"),
                position = SourcePosition.TopLevel(match.range.start..endMatch.range.endInclusive),
                body = SourcePosition.TopLevel(match.range.endInclusive + 1 .. endMatch.range.start - 1),
            )
            ELSE -> ElseBlock(
                property = property ?: throw IllegalArgumentException("Missing property name in if block: ${match.value}"),
                position = SourcePosition.TopLevel(match.range.start..endMatch.range.endInclusive),
                body = SourcePosition.TopLevel(match.range.endInclusive + 1 .. endMatch.range.start - 1),
            )
            FOR -> {
                val (element, list) = forEachPattern.matchEntire(match.groupValues[1].trim())!!.destructured
                EachBlock(
                    property = list,
                    position = SourcePosition.TopLevel(match.range.start..endMatch.range.endInclusive),
                    variable = element,
                    body = SourcePosition.TopLevel(match.range.endInclusive + 1 .. endMatch.range.start - 1),
                )
            }
            else -> error("Unexpected keyword: $keyword")
        }
    }

}