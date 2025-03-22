package org.jetbrains.kastle

import kotlinx.serialization.Serializable

@Serializable
data class SourceDefinition(
    val path: String? = null,
    val text: String? = null,
    val target: Url? = path?.let { "file:$path" },
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
    val condition: String? = null,
    val packId: PackId? = null,
): SourceText

@JvmInline
@Serializable
value class Snippet(
    override val text: String
): SourceText

@Serializable
sealed interface Block {
    val position: BlockPosition
}

@Serializable(BlockPositionSerializer::class)
data class BlockPosition(
    val range: IntRange,
    val outer: IntRange = range,
    val inner: IntRange = range,
    val indent: Int = 0,
    val context: SourceContext = SourceContext.TopLevel
) {
    companion object {
        fun parse(text: String): BlockPosition {
            var items = text.split(Regex("\\s*/\\s*"))
            val (outer, range, inner) = items.take(3).map { item ->
                val (first, last) = item.split(',').map { it.toInt() }
                IntRange(first, last)
            }
            val indent = items[3].toInt()
            val context = items[4].let(SourceContext::valueOf)

            return BlockPosition(range, outer, inner, indent, context)
        }

        fun IntRange.toPosition() =
            BlockPosition(this)

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
        "${outer.first},${outer.last} / ${range.first},${range.last} / ${inner.first},${inner.last} / $indent / ${context.name}"
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
sealed interface PropertyBlock: Block {
    val property: String
}

@Serializable
sealed interface DeclaringBlock: Block {
    val variable: String?
}

@Serializable
sealed interface CompareBlock<T>: Block {
    val value: T
}

@Serializable
data class NamedSlot(
    override val name: String,
    override val position: BlockPosition,
    override val requirement: Requirement = Requirement.OPTIONAL,
): Slot {
    override fun toString(): String = "slots(\"$name\")"
}

@Serializable
data class RepeatingSlot(
    override val name: String,
    override val position: BlockPosition,
    override val requirement: Requirement = Requirement.OPTIONAL,
): Slot {
    override fun toString(): String = "slots(\"$name\")"
}

/**
 * @param embedded this indicates the value should not be wrapped in quotes
 */
@Serializable
data class PropertyLiteral(
    override val property: String,
    override val position: BlockPosition,
    val embedded: Boolean = true,
): PropertyBlock {
    override fun toString(): String = "property(\"$property\")"
}

@Serializable
data class SkipBlock(override val position: BlockPosition): Block {
    override fun toString(): String = "skip"
}

@Serializable
data class IfBlock(
    override val property: String,
    override val position: BlockPosition,
): PropertyBlock {
    override fun toString(): String = "if(\"$property\")"
}

@Serializable
data class ElseBlock(
    override val property: String,
    override val position: BlockPosition,
): PropertyBlock {
    override fun toString(): String = "else(\"$property\")"
}

@Serializable
data class ForEachBlock(
    override val property: String,
    override val position: BlockPosition,
    override val variable: String?,
): PropertyBlock, DeclaringBlock {
    override fun toString(): String = "each(\"$variable\" in \"$property\")"
}

@Serializable
data class WhenBlock(
    override val property: String,
    override val position: BlockPosition,
): PropertyBlock {
    override fun toString(): String = "when(\"$property\")"
}

// TODO can include different conditions
@Serializable
data class OneOfBlock(
    override val value: List<String>,
    override val position: BlockPosition,
): CompareBlock<List<String>> {
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