package org.jetbrains.kastle.templates

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kastle.*
import org.jetbrains.kastle.BlockPosition.Companion.copy
import org.jetbrains.kastle.BlockPosition.Companion.include
import org.jetbrains.kastle.utils.endOfLine
import org.jetbrains.kastle.utils.previousLine
import org.jetbrains.kastle.utils.startOfLine
import org.jetbrains.kastle.utils.unwrapQuotes
import org.jetbrains.kotlin.psi.*
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
    )
}

fun PsiElement.blockRange(
    trim: Boolean = false,
): IntRange {
    return textIntRange(trim)
}

fun PsiElement.bodyRange(): IntRange {
    childrenOfType<KtContainerNodeForControlStructureBody>().firstOrNull()?.let { bodyElement ->
        return bodyElement.bodyRange()
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

fun TextRange.toIntRange(): IntRange =
    startOffset .. endOffset

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

    // TODO only supports single-line comments
    // TODO needs to use other lines for details
    val commentText =
        descendantsOfType<PsiComment>().firstOrNull()?.text?.trimStart('/')?.trim()
            ?: containingFile.text.previousLine(textRange.startOffset)?.trimStart()?.takeIf { it.startsWith("//") }?.trimStart('/')?.trim()

    return Property(
        key = variableName,
        type = PropertyType.Companion.parse(typeReference.text),
        default = null, // TODO
        label = commentText,
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

fun KtCallExpression.readUnsafeBlock(): UnsafeBlock =
    UnsafeBlock(blockPosition().copy(
        // trim quotes from string argument
        inner = valueArguments.first().textIntRange().let {
            it.copy(
                start = it.start + 1,
                end = it.endInclusive - 1
            )
        }
    ))

/**
 * Reads logical expressions that can be inlined, or returns property literal match.
 */
fun KtExpression.readReferenceBlocks(): Sequence<Block> {
    // handles deep references (i.e., foo.bar)
    var variableReference: KtExpression = this
    var ancestor: PsiElement? = parent
    while (ancestor is KtExpression || ancestor is KtContainerNode || ancestor is KtStringTemplateEntry) {
        val blocks = ancestor.tryReadBlocks()
        if (blocks != null)
            return blocks
        variableReference = ancestor as? KtQualifiedExpression ?: variableReference
        ancestor = ancestor.parent
    }
    return variableReference.asLiteralReference()
}

private fun KtExpression.asLiteralReference(): Sequence<Block> =
    sequenceOf(
        InlineValue(
            expression = toTemplateExpression(),
            position = blockPosition(),
            embedded = false,
        )
    )

private fun PsiElement.tryReadBlocks(): Sequence<Block>? =
    when(this) {
        is KtWhenExpression -> asWhenBlock()
        is KtIfExpression -> asIfBlock()
        is KtForExpression -> asEachBlock()
        is KtBlockStringTemplateEntry -> asStringTemplateLiteral()
        else -> null
    }

private fun KtWhenExpression.asWhenBlock(): Sequence<Block> {
    val valueExpression = subjectExpression?.toTemplateExpression() ?: return emptySequence()

    return sequence {
        val whenBlock = WhenBlock(
            expression = valueExpression,
            position = blockPosition()
        )
        yield(whenBlock)
        for (child in childrenOfType<KtWhenEntry>()) {
            yield(
                // TODO
                if (child.isElse) {
                    ElseBlock(
                        position = child.blockPosition(
                            body = child.children[1],
                        )
                    )
                }
                else WhenClauseBlock(
                    value = child.conditions.map {
                        (it.children.single() as? KtExpression)?.toTemplateExpression()
                            ?: throw IllegalArgumentException("Unsupported when condition: $it")
                    },
                    position = child.blockPosition(
                        body = child.children[1],
                    )
                )
            )
        }
        // include references to subject variable if present
        this@asWhenBlock.subjectVariable?.let { subjectVariable ->
            val subjectName = subjectVariable.name!!
            this@asWhenBlock.findReferencesTo(subjectName).forEach { subjectReference ->
                yieldAll(subjectReference.readReferenceBlocks())
            }
        }
    }
}

// TODO handle else-if
private fun KtIfExpression.asIfBlock(): Sequence<Block> {
    if (then == null)
        return emptySequence()
    return sequenceOf(
        // wrapper element
        ConditionalBlock(position = blockPosition()),
        // first clause
        IfBlock(
            expression = condition.toTemplateExpression(),
            // if expression includes else, so we trim to that
            position = then!!.blockPosition(also = this)
        ),
        // else
        `else`?.let { elseElem ->
            ElseBlock(position = elseElem.blockPosition(start = then!!.endOffset))
        }
    ).filterNotNull()
}

private fun KtForExpression.asEachBlock(): Sequence<Block> = sequence {
    val entryName = this@asEachBlock.loopParameter?.name ?: "it"
    yield(
        ForEachBlock(
            expression = this@asEachBlock.loopRange.toTemplateExpression(),
            position = this@asEachBlock.blockPosition(),
            variable = entryName,
        )
    )
    // include references to the element variable
    val bodyNode = childrenOfType<KtContainerNodeForControlStructureBody>().first()
    for (entryReference in bodyNode.findReferencesTo(entryName)) {
        yieldAll(entryReference.readReferenceBlocks())
    }
}

private fun KtBlockStringTemplateEntry.asStringTemplateLiteral(): Sequence<Block> = sequence {
    // Check for unsafe call on string template
    val grandParent = parent.parent
    if (grandParent is KtDotQualifiedExpression && grandParent.selectorExpression is KtCallExpression) {
        val selectorExpression = grandParent.selectorExpression as? KtCallExpression
        val callReference = selectorExpression?.calleeExpression as? KtNameReferenceExpression
        if (callReference?.text == UNSAFE) {
            yield(
                UnsafeBlock(
                    grandParent.blockPosition().copy(
                        // remove quotes from template
                        inner = parent.textIntRange().let {
                            it.copy(
                                start = it.start + 1,
                                end = it.endInclusive - 1
                            )
                        }
                    )
                )
            )
        }
    }
    yield(
        InlineValue(
            expression = this@asStringTemplateLiteral.expression.toTemplateExpression(),
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
    val startOfFirstLine = fileText.startOfLine(startOffset) ?: startOffset
    val endOfLastLine = fileText.endOfLine(endOffset) ?: endOffset

    assert(startOfFirstLine <= endOfLastLine && startOfFirstLine <= startOffset && endOfLastLine >= endOffset) {
        "Bad outer range ($startOffset, $endOffset) -> ($startOfFirstLine, $endOfLastLine)"
    }

    return startOfFirstLine.. endOfLastLine
}

// TODO validation, unchecked casts
sealed interface TemplateParentReference {
    companion object {
        fun classify(reference: KtNameReferenceExpression): TemplateParentReference =
            when (reference.text) {
                SLOT, SLOTS -> Slot(reference.parent as KtCallExpression)
                UNSAFE -> Unsafe(reference.parent as KtCallExpression)
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
    data class Unsafe(val expression: KtCallExpression): TemplateParentReference
}