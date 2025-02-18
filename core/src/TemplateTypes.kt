package org.jetbrains.kastle

import kotlinx.serialization.Serializable

@Serializable
data class SourceTemplateReference(
    val path: String,
    val target: Url = "file:$path",
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
    val position: SourcePosition
    val body: SourcePosition
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
    val variable: String
}

@Serializable
sealed interface CompareBlock<T>: Block {
    val value: T
}

@Serializable
data class NamedSlot(
    override val name: String,
    override val position: SourcePosition,
    override val requirement: Requirement = Requirement.OPTIONAL,
    override val body: SourcePosition = position
): Slot {
    override fun toString(): String = "slots(\"$name\")"
}

@Serializable
data class RepeatingSlot(
    override val name: String,
    override val position: SourcePosition,
    override val requirement: Requirement = Requirement.OPTIONAL,
    override val body: SourcePosition = position
): Slot {
    override fun toString(): String = "slots(\"$name\")"
}

/**
 * @param embedded this indicates the value should not be wrapped in quotes
 */
@Serializable
data class PropertyLiteral(
    override val property: String,
    override val position: SourcePosition,
    override val body: SourcePosition = position,
    val embedded: Boolean = true,
): PropertyBlock {
    override fun toString(): String = "property(\"$property\")"
}

@Serializable
data class SkipBlock(override val position: SourcePosition): Block {
    override val body: SourcePosition = position
    override fun toString(): String = "skip"
}

@Serializable
data class IfBlock(
    override val property: String,
    override val position: SourcePosition,
    override val body: SourcePosition = position
): PropertyBlock {
    override fun toString(): String = "if(\"$property\")"
}

@Serializable
data class ElseBlock(
    override val property: String,
    override val position: SourcePosition,
    override val body: SourcePosition = position
): PropertyBlock {
    override fun toString(): String = "else(\"$property\")"
}

@Serializable
data class EachBlock(
    override val property: String,
    override val position: SourcePosition,
    override val variable: String,
    override val body: SourcePosition = position
): PropertyBlock, DeclaringBlock {
    override fun toString(): String = "each(\"$variable\" in \"$property\")"
}

@Serializable
data class WhenBlock(
    override val property: String,
    override val position: SourcePosition,
    override val body: SourcePosition = position
): PropertyBlock {
    override fun toString(): String = "when(\"$property\")"
}

// TODO can include different conditions
@Serializable
data class OneOfBlock(
    override val value: List<String>,
    override val position: SourcePosition,
    override val body: SourcePosition = position
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