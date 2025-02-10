package org.jetbrains.kastle.templates

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kastle.Block
import org.jetbrains.kastle.EachBlock
import org.jetbrains.kastle.ElseBlock
import org.jetbrains.kastle.IfBlock
import org.jetbrains.kastle.NamedSlot
import org.jetbrains.kastle.OneOfBlock
import org.jetbrains.kastle.Property
import org.jetbrains.kastle.PropertyLiteral
import org.jetbrains.kastle.PropertyType
import org.jetbrains.kastle.RepeatingSlot
import org.jetbrains.kastle.Slot
import org.jetbrains.kastle.SourcePosition
import org.jetbrains.kastle.WhenBlock
import org.jetbrains.kastle.utils.unwrapQuotes
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtContainerNodeForControlStructureBody
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.parents

private val bodyOpenRegex = Regex("^\\{\\s*(?:\\w+\\s*->\\s*)?")
private val bodyCloseRegex = Regex("\\s*}$")

fun KtFile.endOfImports(): Int? =
    importDirectives.maxOfOrNull { it.textRange.endOffset }

fun PsiElement.bodyPosition(): SourcePosition {
    childrenOfType<KtContainerNodeForControlStructureBody>().firstOrNull()?.let { bodyElement ->
        return bodyElement.bodyPosition()
    }
    val start = bodyOpenRegex.find(text)?.range?.endInclusive?.let { it + 1 } ?: 0
    val end = bodyCloseRegex.find(text)?.range?.start ?: text.length
    val range = (textRange.startOffset + start) until (textRange.startOffset + end)
    return SourcePosition.TopLevel(range)
}

fun PsiElement.sourcePosition(includeTrailingNewline: Boolean = false): SourcePosition {
    val inlineContext = parents.firstNotNullOfOrNull(::inlineContext)
    if (inlineContext != null)
        return SourcePosition.Inline(textIntRange(includeTrailingNewline), inlineContext)
    return SourcePosition.TopLevel(textIntRange(includeTrailingNewline))
}

fun PsiElement.textIntRange(includeTrailingNewline: Boolean = false): IntRange =
    if (includeTrailingNewline && nextSibling is PsiWhiteSpace && nextSibling.text.all { it == '\n' })
        textRange.startOffset until nextSibling.textRange.endOffset
    else textRange.toIntRange()

fun TextRange.toIntRange(): IntRange =
    startOffset until endOffset

private fun inlineContext(parent: PsiElement): String? = when (parent) {
    is KtClass -> parent.name
    is KtNamedFunction -> parent.receiverTypeReference?.name
    else -> null
}

fun KtDeclaration.asProperty(): Property {
    val variableName = name
    require(variableName != null) {
        "Missing variable name on template property declaration: $text"
    }

    val typeReference = children
        .filterIsInstance<KtTypeReference>()
        .firstOrNull()
    require(typeReference != null) {
        "Missing type on template property declaration: $text"
    }

    // TODO fixup
    val comment = descendantsOfType<PsiComment>()
        .firstOrNull()?.text?.trimStart('/')?.trim()

    return Property(
        key = variableName,
        type = PropertyType.Companion.parse(typeReference.text),
        default = null, // TODO
        description = comment,
    )
}

fun KtDeclaration.findReferences(): Sequence<KtNameReferenceExpression> =
    parent.findReferencesTo(name!!)

fun PsiElement.findReferencesTo(name: String): Sequence<KtNameReferenceExpression> =
    descendantsOfType<KtNameReferenceExpression>().filter {
        it.text == name
    }

fun KtNameReferenceExpression.readPropertyBlocks(variableName: String): Sequence<Block> =
    parent.tryReadBlocks(variableName) ?:
    parent.parent.tryReadBlocks(variableName) ?:
    asLiteralReference(variableName)

fun KtExpression.readSlotBlock(): Slot {
    val call = childrenOfType<KtCallExpression>().firstOrNull()
    require(call != null) {
        "Missing template function call: $text $textRange"
    }
    val functionName = call.childrenOfType<KtNameReferenceExpression>().first().text
    val slotName = call.valueArguments[0].text.unwrapQuotes()
    // TODO look for !! to establish required
    // TODO look for expected return type
    return when (functionName) {
        "Slot" -> NamedSlot(
            slotName,
            sourcePosition(),
        )
        "Slots" -> RepeatingSlot(
            slotName,
            sourcePosition(),
        )
        else -> throw IllegalArgumentException("Unsupported template function: $functionName")
    }
}

private fun KtNameReferenceExpression.asLiteralReference(variableName: String): Sequence<Block> =
    sequenceOf(
        PropertyLiteral(
            property = variableName,
            position = sourcePosition()
        )
    )

private fun PsiElement.tryReadBlocks(variableName: String): Sequence<Block>? =
    when(this) {
        is KtWhenExpression -> asWhenBlock(variableName)
        is KtIfExpression -> asIfBlock(variableName)
        is KtForExpression -> asEachBlock(variableName)
        else -> null
    }

private fun KtWhenExpression.asWhenBlock(variableName: String): Sequence<Block> = sequence {
    yield(
        WhenBlock(
            property = variableName,
            position = this@asWhenBlock.sourcePosition(),
            body = this@asWhenBlock.bodyPosition(),
        )
    )
    for (child in childrenOfType<KtWhenEntry>()) {
        yield(
            // TODO
            if (child.isElse)
                ElseBlock(
                    property = variableName,
                    position = child.sourcePosition(),
                    body = child.children[1].bodyPosition(),
                )
            else OneOfBlock(
                value = child.conditions.map { it.text.unwrapQuotes() },
                position = child.sourcePosition(),
                body = child.children[1].bodyPosition(),
            )
        )
    }
    // include references to subject variable if present
    this@asWhenBlock.subjectVariable?.let { subjectVariable ->
        val subjectName = subjectVariable.name!!
        this@asWhenBlock.findReferencesTo(subjectName).forEach { subjectReference ->
            yieldAll(subjectReference.readPropertyBlocks(subjectName))
        }
    }
}

private fun KtIfExpression.asIfBlock(variableName: String): Sequence<Block> =
    sequenceOf(
        IfBlock(
            property = variableName,
            position = this@asIfBlock.sourcePosition(),
            body = this@asIfBlock.bodyPosition(),
        )
    )

private fun KtForExpression.asEachBlock(variableName: String): Sequence<Block> = sequence {
    val entryName = this@asEachBlock.loopParameter?.name ?: "it"
    yield(
        EachBlock(
            property = variableName,
            position = this@asEachBlock.sourcePosition(),
            argument = entryName,
            body = this@asEachBlock.bodyPosition(),
        )
    )
    // include references to the element variable
    val bodyNode = childrenOfType<KtContainerNodeForControlStructureBody>().first()
    for (entryReference in bodyNode.findReferencesTo(entryName)) {
        yieldAll(entryReference.readPropertyBlocks(entryName))
    }
}

sealed interface TemplateReference {
    companion object {
        fun classify(reference: KtNameReferenceExpression): TemplateReference {
            if (reference.parent is KtPropertyDelegate)
                return PropertyDelegate(reference.parent.parent as KtDeclaration)
            else if (reference.parent is KtDotQualifiedExpression)
                return SlotExpression(reference.parent as KtDotQualifiedExpression)
            else
                throw IllegalArgumentException("Unrecognized Template reference: ${reference.parent.text} ${reference.parent.textRange}")
        }
    }

    data class PropertyDelegate(val declaration: KtDeclaration): TemplateReference
    data class SlotExpression(val expression: KtExpression): TemplateReference
}