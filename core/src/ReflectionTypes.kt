package org.jetbrains.kastle

import kotlinx.serialization.Serializable

typealias QualifiedName = String

@Serializable(SourcePositionSerializer::class)
sealed interface SourcePosition {
    companion object {
        private val Regex by lazy {
            Regex("(\\w+)\\(([^)]+)\\)")
        }

        fun parse(text: String): SourcePosition {
            val (function, argsString) = Regex.matchEntire(text)?.destructured
                ?: throw IllegalArgumentException("Invalid slot position: $text")
            val args = argsString.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val range = try {
                val (start, end) = args
                IntRange(start.toInt(), end.toInt())
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid slot position: $text", e)
            }
            return when(function) {
                "top" -> TopLevel(range)
                "inline" -> Inline(range, args[2])
                else -> throw IllegalArgumentException("Invalid slot position: $text")
            }
        }
    }

    val range: IntRange

    data class TopLevel(override val range: IntRange): SourcePosition {
        override fun toString(): String = "top(${range.start}, ${range.endInclusive})"
    }

    // TODO receiver type, multiple receivers, context parameters, scope parameters
    data class Inline(override val range: IntRange, val receiver: String): SourcePosition {
        override fun toString(): String = "inline(${range.start}, ${range.endInclusive}, $receiver)"
    }
}