package org.jetbrains.kastle

import kotlinx.serialization.Serializable
import org.jetbrains.kastle.utils.Expression

@Serializable
data class SourceDefinition(
    val path: String? = null,
    val text: String? = null,
    val target: Url? = path?.let { "file:$path" },
    val `if`: String? = null,
)

@Serializable
sealed interface SourceText {
    val text: String
}

@Serializable
data class SourceTemplate(
    override val text: String,
    val target: Url,
    val imports: List<String>? = null,
    val blocks: List<Block>? = null,
    val condition: Expression? = null,
    val packId: PackId? = null,
): SourceText

@JvmInline
@Serializable
value class Snippet(
    override val text: String
): SourceText

@Serializable
sealed interface Block {
    var position: BlockPosition
}

@Serializable
sealed interface StructuralBlock: Block

@Serializable(BlockPositionSerializer::class)
data class BlockPosition(
    val range: IntRange,
    val outer: IntRange = range,
    val inner: IntRange = range,
    val level: Int = 0,
    val context: SourceContext = SourceContext.TopLevel
) {
    companion object {
        fun parse(text: String): BlockPosition {
            var items = text.split(Regex("\\s*/\\s*"))
            val (outer, range, inner) = items.take(3).map { item ->
                val (first, last) = item.split(',').map { it.toInt() }
                IntRange(first, last)
            }
            val level = items[3].toInt()
            val context = items[4].let(SourceContext::valueOf)

            return BlockPosition(range, outer, inner, level, context)
        }

        // expanded range to contain both
        infix fun IntRange.include(range: IntRange): IntRange =
            if (start < range.start) {
                IntRange(start, range.endInclusive)
            } else {
                IntRange(range.start, endInclusive)
            }

        fun IntRange.copy(start: Int = this.start, end: Int = endInclusive) =
            IntRange(start, end)

        fun IntRange.bumpEnd() =
            IntRange(start, endInclusive + 1)

        fun IntRange.reduceEnd() =
            IntRange(start, endInclusive - 1)
    }

    override fun toString(): String =
        "${outer.first},${outer.last} / ${range.first},${range.last} / ${inner.first},${inner.last} / $level / ${context.name}"
}

enum class SourceContext {
    TopLevel,
    Inline
}

@Serializable
sealed interface Slot: Block {
    val name: String
    val requirement: Requirement
}

@Serializable
sealed interface ExpressionBlock: Block {
    val expression: Expression
}

@Serializable
sealed interface DeclaringBlock: Block {
    val variable: String?
}

@Serializable
data class NamedSlot(
    override val name: String,
    override var position: BlockPosition,
    override val requirement: Requirement = Requirement.OPTIONAL,
): Slot {
    override fun toString(): String = "slots(\"$name\")"
}

@Serializable
data class RepeatingSlot(
    override val name: String,
    override var position: BlockPosition,
    override val requirement: Requirement = Requirement.OPTIONAL,
): Slot {
    override fun toString(): String = "slots(\"$name\")"
}

/**
 * @param embedded this indicates the value should not be wrapped in quotes
 */
@Serializable
data class InlineValue(
    override val expression: Expression,
    override var position: BlockPosition,
    val embedded: Boolean = true,
): ExpressionBlock {
    override fun toString(): String = "property(\"$expression\")"
}

/**
 * Inject arbitrary strings as code.
 */
@Serializable
data class UnsafeBlock(
    override var position: BlockPosition,
): Block

@Serializable
data class SkipBlock(override var position: BlockPosition): Block {
    override fun toString(): String = "skip"
}

// Wrapper for If / Else blocks
@Serializable
data class ConditionalBlock(
    override var position: BlockPosition,
): Block

@Serializable
data class IfBlock(
    override val expression: Expression,
    override var position: BlockPosition,
): ExpressionBlock, StructuralBlock {
    override fun toString(): String = "if(\"$expression\")"
}

// Used in both if/when, evaluated from context
@Serializable
data class ElseBlock(
    override var position: BlockPosition,
): StructuralBlock {
    override fun toString(): String = "else"
}

@Serializable
data class ForEachBlock(
    override val expression: Expression,
    override var position: BlockPosition,
    override val variable: String?,
): ExpressionBlock, DeclaringBlock, StructuralBlock {
    override fun toString(): String = "each(\"$variable\" in \"$expression\")"
}

@Serializable
data class WhenBlock(
    override val expression: Expression,
    override var position: BlockPosition,
): ExpressionBlock, StructuralBlock {
    override fun toString(): String = "when(\"$expression\")"
}

// TODO different condition types
@Serializable
data class WhenClauseBlock(
    val value: List<Expression>,
    override var position: BlockPosition,
): Block, StructuralBlock {
    override fun toString(): String = "-> ${value.joinToString(", ") { "\"$it\"" }})"
}

typealias Url = String

/**
 * Rule for slot requirment
 */
enum class Requirement {
    // Fail when missing
    REQUIRED,
    // Skip when missing
    OMITTED,
    // Ignore
    OPTIONAL,
}