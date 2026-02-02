package org.jetbrains.kastle.templates

import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kastle.utils.Expression
import org.jetbrains.kastle.utils.StringExpression
import org.jetbrains.kastle.utils.wrapQuotes
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtProperty

class KotlinExpressionParser(val psiFileFactory: PsiFileFactory) {

    fun parseTemplate(text: String): StringExpression =
        parse(text.wrapQuotes()) as? StringExpression ?: error("Failed to parse template: $text")

    fun parse(text: String): Expression {
        val psiFile = psiFileFactory.createFileFromText(
            "expression.kt",
            KotlinLanguage.INSTANCE,
            "val expr = $text"
        )
        val property = psiFile.childrenOfType<KtProperty>().firstOrNull()
            ?: throw IllegalArgumentException("Invalid expression: ${psiFile.text}")

        return property.initializer.toTemplateExpression()
    }

}