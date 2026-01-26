package org.jetbrains.kastle.templates

import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kastle.utils.Expression
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtProperty

class KotlinExpressionParser(val psiFileFactory: PsiFileFactory) {

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