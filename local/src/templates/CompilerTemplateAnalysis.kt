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
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtContainerNodeForControlStructureBody
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addToStdlib.indexOfOrNull
import org.jetbrains.kotlin.utils.addToStdlib.lastIndexOfOrNull

fun KtFile.endOfImports(): Int? =
    importDirectives.maxOfOrNull { it.textRange.endOffset }

fun PsiElement.bodyPosition(): SourcePosition {
    childrenOfType<KtContainerNodeForControlStructureBody>().firstOrNull()?.let { bodyElement ->
        return bodyElement.bodyPosition()
    }
    // TODO when clause issue
    val start = text.indexOfOrNull('{')?.plus(1) ?: 0
    val length = text.lastIndexOfOrNull('}') ?: textRange.length
    val range = (textRange.startOffset + start) until (textRange.startOffset + length)
    val indent = findIndent(this)

    return SourcePosition.TopLevel(range, indent)
}

fun PsiElement.sourcePosition(
    includeTrailingNewline: Boolean = false,
    trim: Boolean = false,
): SourcePosition {
    val receiver = parents.firstNotNullOfOrNull(::inlineContext)
    val range = textIntRange(includeTrailingNewline, trim)
    val indent = findIndent(this)
    if (receiver != null)
        return SourcePosition.Inline(range, indent, receiver)

    return SourcePosition.TopLevel(range, indent)
}

fun PsiElement.textIntRange(
    includeTrailingNewline: Boolean = false,
    trim: Boolean = false,
): IntRange =
    if (includeTrailingNewline && hasWhitespaceSibling())
        textRange.startOffset until nextSibling.textRange.endOffset
    else if (trim) {
        val (startWs, endWs) = text.takeWhile { it.isWhitespace() }.length to text.takeLastWhile { it.isWhitespace() }.length
        textRange.startOffset + startWs until textRange.endOffset - endWs
    }
    else textRange.toIntRange()

fun PsiElement.hasWhitespaceSibling() =
    nextSibling is PsiWhiteSpace && nextSibling.text.all { it == '\n' }

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

fun PsiElement.findReferencesTo(vararg names: String): Sequence<KtNameReferenceExpression> =
    descendantsOfType<KtNameReferenceExpression>().filter {
        it.text in names
    }

fun KtCallExpression.readSlotBlock(): Slot {
    val functionName = childrenOfType<KtNameReferenceExpression>().first().text
    val slotName = valueArguments[0].text.unwrapQuotes()
    // TODO look for !! to establish required
    // TODO look for expected return type
    return when (functionName) {
        SLOT -> NamedSlot(
            slotName,
            sourcePosition(),
        )
        SLOTS -> RepeatingSlot(
            slotName,
            sourcePosition(),
        )
        else -> throw IllegalArgumentException("Unsupported template function: $functionName")
    }
}

/**
 * Reads logical expressions that can be inlined, or returns property literal match.
 */
fun KtExpression.readPropertyBlocks(): Sequence<Block> {
    // handles deep references (i.e., foo.bar)
    var variableReference: KtExpression = this
    var ancestor: PsiElement? = parent
    while (ancestor is KtExpression || ancestor is KtContainerNode || ancestor is KtStringTemplateEntry) {
        val blocks = ancestor.tryReadBlocks(variableReference.text)
        if (blocks != null)
            return blocks
        variableReference = ancestor as? KtQualifiedExpression ?: variableReference
        ancestor = ancestor.parent
    }
    return variableReference.asLiteralReference()
}

private fun KtExpression.asLiteralReference(): Sequence<Block> =
    sequenceOf(
        PropertyLiteral(
            property = text,
            position = sourcePosition(),
            embedded = false,
        )
    )

private fun PsiElement.tryReadBlocks(variableName: String): Sequence<Block>? =
    when(this) {
        is KtWhenExpression -> asWhenBlock(variableName)
        is KtIfExpression -> asIfBlock(variableName)
        is KtForExpression -> asEachBlock(variableName)
        is KtBlockStringTemplateEntry -> asStringTemplateLiteral(variableName)
        else -> null
    }

private fun KtWhenExpression.asWhenBlock(variableName: String): Sequence<Block> = sequence {
    yield(
        WhenBlock(
            property = variableName,
            position = sourcePosition(),
            body = bodyPosition()
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
            yieldAll(subjectReference.readPropertyBlocks())
        }
    }
}

// TODO handle else-if
// TODO trailing }
private fun KtIfExpression.asIfBlock(variableName: String): Sequence<Block> =
    if (then == null)
        emptySequence()
    else {
        sequenceOf(
            IfBlock(
                property = variableName,
                // if expression includes else, so we trim to that
                position = sourcePosition()
                    .withBounds(end = then!!.textIntRange(trim = true).endInclusive),
                body = then!!.bodyPosition(),
            ),
            `else`?.let {
                ElseBlock(
                    property = variableName,
                    position = it.sourcePosition()
                        .withBounds(start = then!!.textIntRange().endInclusive + 1),
                    body = it.bodyPosition(),
                )
            }
        ).filterNotNull()
    }

private fun KtForExpression.asEachBlock(variableName: String): Sequence<Block> = sequence {
    val entryName = this@asEachBlock.loopParameter?.name ?: "it"
    yield(
        EachBlock(
            property = variableName,
            position = this@asEachBlock.sourcePosition(),
            variable = entryName,
            body = this@asEachBlock.bodyPosition(),
        )
    )
    // include references to the element variable
    val bodyNode = childrenOfType<KtContainerNodeForControlStructureBody>().first()
    for (entryReference in bodyNode.findReferencesTo(entryName)) {
        yieldAll(entryReference.readPropertyBlocks())
    }
}

private fun KtBlockStringTemplateEntry.asStringTemplateLiteral(variableName: String): Sequence<Block> =
    sequenceOf(
        PropertyLiteral(
            variableName,
            position = sourcePosition(),
            body = childrenOfType<KtExpression>().first().sourcePosition(),
        )
    )

private fun findIndent(element: PsiElement): Int {
    val startOffset = element.textRange.startOffset
    val fileText = element.containingFile.text
    val lineStartOffset =
        fileText.lastIndexOf('\n', startOffset - 1)
            .takeIf { it != -1 }?.plus(1) ?: return 0
    val whiteSpaceCountAfterLineStart =
        fileText.subSequence(lineStartOffset, startOffset + 1)
            .indexOfFirst { !it.isWhitespace() }
            .takeIf { it != -1 } ?: return 0

    return whiteSpaceCountAfterLineStart
}

// TODO validation, unchecked casts
sealed interface TemplateParentReference {
    companion object {
        fun classify(reference: KtNameReferenceExpression): TemplateParentReference =
            when (reference.text) {
                SLOT, SLOTS -> Slot(reference.parent as KtCallExpression)
                PROPERTIES -> PropertyDelegate(reference.parent.parent as KtDeclaration)
                MODULE, PROJECT -> {
                    val expression = reference.parent as KtDotQualifiedExpression
                    // TODO other kinds of module references
                    PropertyReferenceChain(expression)
                }
                else -> throw IllegalArgumentException("Unrecognized reference: ${reference.text}")
            }
    }

    data class PropertyDelegate(val declaration: KtDeclaration): TemplateParentReference
    data class PropertyReferenceChain(val expression: KtDotQualifiedExpression): TemplateParentReference
    data class Slot(val expression: KtCallExpression): TemplateParentReference
}