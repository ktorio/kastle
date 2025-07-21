package org.jetbrains.kastle

import kotlinx.io.buffered
import kotlinx.io.bytestring.ByteString
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.source
import kotlinx.io.readByteString
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.kastle.io.relativeTo
import org.jetbrains.kastle.utils.Expression

@Serializable
data class SourceDefinition(
    val path: String? = null,
    val text: String? = null,
    val target: Url? = path?.let { "file:$path" },
    val `if`: String? = null,
)

@Serializable
sealed interface SourceFile {
    val target: Url
    val condition: Expression?
}

fun SourceFile.withCondition(condition: Expression?) =
    if (condition == null || this.condition == condition) this else when (this) {
        is StaticSource -> this.copy(condition = condition)
        is SourceTemplate -> this.copy(condition = condition)
    }

@Serializable
@SerialName("static")
data class StaticSource(
    @Serializable(with = ByteStringSerializer::class)
    val contents: ByteString,
    override val target: Url,
    override val condition: Expression? = null,
): SourceFile {
    companion object {
        fun FileSystem.sourceFile(
            file: Path,
            basePath: Path,
            condition: Expression? = null,
        ): StaticSource {
            val bytes = source(file).buffered().use {
                it.readByteString()
            }
            return StaticSource(
                contents = bytes,
                target = "file:${file.relativeTo(basePath)}",
                condition = condition,
            )
        }
    }
}

@Serializable
@SerialName("template")
data class SourceTemplate(
    val text: String,
    override val target: Url,
    val imports: List<String>? = null,
    val blocks: List<Block>? = null,
    override val condition: Expression? = null,
    // this is here to sort out files after modules are merged
    val packId: PackId? = null,
): SourceFile

@Serializable
sealed interface Block {
    var position: BlockPosition
}

@Serializable
sealed interface StructuralBlock: Block

/**
 * Represents the positioning for a block of code within a source file.
 *
 * @property line  The line offset in the file.
 * @property range The primary range of characters pertinent to the block.
 * @property outer Includes surrounding whitespace of the element.
 * @property inner The body range of the block (i.e., the contents of an if statement)
 * @property level The degree of nesting for the block where inlining indentation is concerned.
 */
@Serializable(BlockPositionSerializer::class)
data class BlockPosition(
    val line: Int,
    val range: IntRange,
    val outer: IntRange = range,
    val inner: IntRange = range,
    val level: Int = 0,
) {
    companion object {
        fun parse(text: String): BlockPosition {
            val items = text.split(Regex("\\s*/\\s*")).toMutableList()
            val line = items.removeFirst().toInt()
            val level = items.removeFirst().toInt()
            val (outer, range, inner) = items.map { item ->
                val (first, last) = item.split(',').map { it.toInt() }
                IntRange(first, last)
            }

            return BlockPosition(line, range, outer, inner, level)
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
    }

    override fun toString(): String =
        "$line / $level / ${outer.first},${outer.last} / ${range.first},${range.last} / ${inner.first},${inner.last}"
}

enum class SourceContext {
    TopLevel,
    Inline
}

@Serializable
sealed interface Slot: Block {
    val name: String
    val requirement: Requirement
    val context: SourceContext
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
    override val context: SourceContext = SourceContext.TopLevel,
): Slot {
    override fun toString(): String = "slots(\"$name\")"
}

@Serializable
data class RepeatingSlot(
    override val name: String,
    override var position: BlockPosition,
    override val requirement: Requirement = Requirement.OPTIONAL,
    override val context: SourceContext = SourceContext.TopLevel,
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

// TODO variable declaration
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