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
): SourceText

@JvmInline
@Serializable
value class Snippet(
    override val text: String
): SourceText

@Serializable
sealed interface Block {
    val position: SourcePosition
    val body: SourcePosition?
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
sealed interface CompareBlock<T>: Block {
    val value: T
}

@Serializable
data class NamedSlot(
    override val name: String,
    override val position: SourcePosition,
    override val requirement: Requirement = Requirement.OPTIONAL,
    override val body: SourcePosition? = null
): Slot

@Serializable
data class RepeatingSlot(
    override val name: String,
    override val position: SourcePosition,
    override val requirement: Requirement = Requirement.OPTIONAL,
    override val body: SourcePosition? = null
): Slot

@Serializable
data class PropertyLiteral(
    override val property: String,
    override val position: SourcePosition,
    override val body: SourcePosition? = null
): PropertyBlock

@Serializable
data class SkipBlock(override val position: SourcePosition): Block {
    override val body: SourcePosition? = null
}

@Serializable
data class IfBlock(
    override val property: String,
    override val position: SourcePosition,
    override val body: SourcePosition? = null
): PropertyBlock

@Serializable
data class ElseBlock(
    override val property: String,
    override val position: SourcePosition,
    override val body: SourcePosition? = null
): PropertyBlock

@Serializable
data class EachBlock(
    override val property: String,
    override val position: SourcePosition,
    val argument: String,
    override val body: SourcePosition? = null
): PropertyBlock

@Serializable
data class WhenBlock(
    override val property: String,
    override val position: SourcePosition,
    override val body: SourcePosition? = null
): PropertyBlock

// TODO can include different conditions
@Serializable
data class OneOfBlock(
    override val value: List<String>,
    override val position: SourcePosition,
    override val body: SourcePosition? = null
): CompareBlock<List<String>>

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