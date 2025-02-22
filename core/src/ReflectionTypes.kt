package org.jetbrains.kastle

import kotlinx.serialization.Serializable

@Serializable(SourcePositionSerializer::class)
sealed interface SourcePosition {
    companion object {
        private val ArgsRegex by lazy {
            Regex("(?<key>\\w+)=(?<value>[^;]*)")
        }
        private val PositionRegex by lazy {
            Regex("(?<type>\\w+)@(?<start>\\d+),(?<end>\\d+)(?<args>;\\w+=[^;]*)*")
        }

        fun parse(text: String): SourcePosition {
            val (type, start, end, rest) = PositionRegex.matchEntire(text)?.destructured
                ?: throw IllegalArgumentException("Invalid slot position: $text")
            val argsMap = rest.split(';').asSequence().filterNot(String::isEmpty).associate { item ->
                val (key, value) = ArgsRegex.matchEntire(item)?.destructured
                    ?: throw IllegalArgumentException("Invalid position clause \"$item\": $text")
                key to value
            }
            val indent = argsMap["indent"]?.toInt() ?: 0
            val range = IntRange(start.toInt(), end.toInt())
            return when(type) {
                "top" -> TopLevel(range, indent)
                "inline" -> Inline(range, indent, argsMap["receiver"])
                else -> throw IllegalArgumentException("Invalid position: $text")
            }
        }
    }

    val range: IntRange
    val indent: Int

    fun withBounds(start: Int? = null, end: Int? = null): SourcePosition

    data class TopLevel(
        override val range: IntRange,
        override val indent: Int,
    ): SourcePosition {
        override fun withBounds(start: Int?, end: Int?) =
            copy(range = IntRange(start ?: range.first, end ?: range.last))

        override fun toString(): String = "top@${range.start},${range.endInclusive};indent=$indent"
    }

    // TODO receiver type, multiple receivers, context parameters, scope parameters
    data class Inline(
        override val range: IntRange,
        override val indent: Int,
        val receiver: String? = null,
    ): SourcePosition {
        override fun withBounds(start: Int?, end: Int?) =
            copy(range = IntRange(start ?: range.first, end ?: range.last))

        override fun toString(): String = buildString {
            append("inline@${range.start},${range.endInclusive}")
            append(";indent=$indent")
            if (receiver != null) append(";receiver=$receiver")
        }
    }
}