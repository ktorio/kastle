package org.jetbrains.kastle.templates

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kastle.Block
import org.jetbrains.kastle.BlockPosition
import org.jetbrains.kastle.BlockPosition.Companion.copy
import org.jetbrains.kastle.BlockPosition.Companion.include
import org.jetbrains.kastle.BlockPosition.Companion.toPosition
import org.jetbrains.kastle.ForEachBlock
import org.jetbrains.kastle.ElseBlock
import org.jetbrains.kastle.IfBlock
import org.jetbrains.kastle.NamedSlot
import org.jetbrains.kastle.OneOfBlock
import org.jetbrains.kastle.Property
import org.jetbrains.kastle.PropertyLiteral
import org.jetbrains.kastle.PropertyType
import org.jetbrains.kastle.RepeatingSlot
import org.jetbrains.kastle.Slot
import org.jetbrains.kastle.UnsafeBlock
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
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.utils.addToStdlib.indexOfOrNull
import org.jetbrains.kotlin.utils.addToStdlib.lastIndexOfOrNull

fun KtFile.endOfImports(): Int? =
    importDirectives.maxOfOrNull { it.textRange.endOffset }

// TODO outer
fun PsiElement.blockPosition(
    body: PsiElement = this,
    also: PsiElement? = null,
    start: Int? = null,
): BlockPosition {
    var range = blockRange()
    if (also != null)
        range = range include also.blockRange()
    if (start != null)
        range = range.copy(start = start)

    return BlockPosition(
        range = range,
        outer = outerRange(range),
        inner = body.bodyRange(),
        indent = findIndent(),
    )
}

fun PsiElement.blockRange(
    trim: Boolean = false,
): IntRange {
    val range = textIntRange(trim)
//    val receiver = parents.firstNotNullOfOrNull(::inlineContext)
//    val indent = findIndent(this)
    // TODO use receiver / context

    return range
}

fun PsiElement.bodyRange(): IntRange {
    childrenOfType<KtContainerNodeForControlStructureBody>().firstOrNull()?.let { bodyElement ->
        return bodyElement.bodyRange()
    }
    // TODO hack for unsafe templates
    if (this is KtStringTemplateExpression) {
        return textRange.toIntRange()
    }
    // TODO when clause issue
    val start = text.indexOfOrNull('{')?.plus(1) ?: 0
    val length = text.lastIndexOfOrNull('}') ?: textRange.length
    val range = (textRange.startOffset + start)..(textRange.startOffset + length)

    return range
}

fun PsiElement.textIntRange(
    trim: Boolean = false,
): IntRange =
    if (trim) {
        val (startWs, endWs) = text.takeWhile { it.isWhitespace() }.length to text.takeLastWhile { it.isWhitespace() }.length
        textRange.startOffset + startWs until textRange.endOffset - endWs
    }
    else textRange.toIntRange()

fun PsiElement.hasWhitespaceSibling() =
    nextSibling is PsiWhiteSpace && nextSibling.text.all { it == '\n' }

fun TextRange.toIntRange(): IntRange =
    startOffset .. endOffset

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
            blockPosition()
        )
        SLOTS -> RepeatingSlot(
            slotName,
            blockPosition()
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
            position = blockRange().toPosition(), // TODO
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
            position = blockPosition()
        )
    )
    for (child in childrenOfType<KtWhenEntry>()) {
        yield(
            // TODO
            if (child.isElse)
                ElseBlock(
                    property = variableName,
                    position = child.blockPosition(body = child.children[1])
                )
            else OneOfBlock(
                value = child.conditions.map { it.text.unwrapQuotes() },
                position = child.blockPosition(body = child.children[1])
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
                position = then!!.blockPosition(also = this)
            ),
            `else`?.let { elseElem ->
                ElseBlock(
                    property = variableName,
                    position = elseElem.blockPosition(start = then!!.endOffset)
                )
            }
        ).filterNotNull()
    }

private fun KtForExpression.asEachBlock(variableName: String): Sequence<Block> = sequence {
    val entryName = this@asEachBlock.loopParameter?.name ?: "it"
    yield(
        ForEachBlock(
            property = variableName,
            position = this@asEachBlock.blockPosition(),
            variable = entryName,
        )
    )
    // include references to the element variable
    val bodyNode = childrenOfType<KtContainerNodeForControlStructureBody>().first()
    for (entryReference in bodyNode.findReferencesTo(entryName)) {
        yieldAll(entryReference.readPropertyBlocks())
    }
}

private fun KtBlockStringTemplateEntry.asStringTemplateLiteral(variableName: String): Sequence<Block> = sequence {
    val grandParent = parent.parent
    if (grandParent is KtDotQualifiedExpression && grandParent.selectorExpression is KtCallExpression) {
        val selectorExpression = grandParent.selectorExpression as? KtCallExpression
        val callReference = selectorExpression?.calleeExpression as? KtNameReferenceExpression
        if (callReference?.text == UNSAFE) {
            yield(
                UnsafeBlock(
                    grandParent.blockPosition(
                        body = parent
                    )
                )
            )
        }
    }
    yield(
        PropertyLiteral(
            variableName,
            position = blockPosition(
                body = childrenOfType<KtExpression>().first()
            ),
            embedded = true,
        )
    )
}

/**
 * When this block occupies a set of complete lines, we find the start and end of those lines.
 */
private fun PsiElement.outerRange(range: IntRange): IntRange {
    val startOffset = range.first
    val endOffset = range.last
    val fileText = containingFile.text
    val startOfFirstLine =
        fileText
            .lastIndexOf('\n', startOffset - 1)
            .takeIf { i -> i != -1 && fileText.substring(i, startOffset).isBlank() }
            ?: startOffset
    val endOfLastLine =
        fileText
            .indexOf('\n', endOffset)
            .takeIf { i -> i != -1 && fileText.substring(endOffset, i).isBlank() }
            ?: endOffset

    assert(startOfFirstLine <= endOfLastLine && startOfFirstLine <= startOffset && endOfLastLine >= endOffset) {
        "Bad outer range ($startOffset, $endOffset) -> ($startOfFirstLine, $endOfLastLine)"
    }

    return startOfFirstLine.. endOfLastLine
}

private fun PsiElement.findIndent(): Int {
    val startOffset = this.textRange.startOffset
    val fileText = this.containingFile.text
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