package org.jetbrains.kastle.templates

import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kastle.utils.Expression
import org.jetbrains.kastle.utils.BinaryOperator
import org.jetbrains.kastle.utils.PostfixOperator
import org.jetbrains.kastle.utils.PrefixOperator
import org.jetbrains.kastle.utils.StringLiteral
import org.jetbrains.kotlin.lexer.KtTokens

fun KtExpression?.toTemplateExpression(): Expression {
    return when (this) {
        null -> Expression.NullLiteral

        // Handle string templates
        is KtStringTemplateExpression -> {
            if (entries.isEmpty()) {
                // Empty string
                StringLiteral("")
            } else if (entries.size == 1 && entries[0] is KtLiteralStringTemplateEntry) {
                // Simple string without interpolation
                StringLiteral((entries[0] as KtLiteralStringTemplateEntry).text)
            } else {
                // Interpolated string template:
                // - Return Expression.StringTemplate
                // - Each entry expression is produced recursively via toTemplateExpression()
                org.jetbrains.kastle.utils.StringTemplate(
                    entries = entries.map { entry ->
                        val entryExpression: Expression = when (entry) {
                            is KtLiteralStringTemplateEntry -> StringLiteral(entry.text)

                            // Escapes like \n, \t, \$, etc.
                            // Keep it conservative: use the entry's text as it appears in the template.
                            is KtEscapeStringTemplateEntry -> StringLiteral(entry.text)

                            // "$name"
                            is KtSimpleNameStringTemplateEntry -> {
                                val embedded = entry.expression
                                embedded?.toTemplateExpression()
                                    ?: Expression.VariableRef(entry.text.removePrefix("$"))
                            }

                            // "${ ... }"
                            is KtBlockStringTemplateEntry -> {
                                val embedded = entry.expression
                                    ?: throw IllegalArgumentException("Missing expression in string template entry: ${entry.text}")
                                embedded.toTemplateExpression()
                            }

                            else -> StringLiteral(entry.text)
                        }

                        entryExpression
                    }
                )
            }
        }

        // Handle numeric literals
        is KtConstantExpression -> {
            when (val text = text) {
                "null" -> Expression.NullLiteral
                "true" -> Expression.BooleanLiteral(true)
                "false" -> Expression.BooleanLiteral(false)
                else -> {
                    // Try parsing as number
                    try {
                        if (text.startsWith('"') && text.endsWith('"'))
                            StringLiteral(text.substring(1, text.length - 1))
                        else if (text.startsWith('\'') && text.endsWith('\''))
                            Expression.CharLiteral(text[1]) // TODO handle escape
                        else if (text.contains('.'))
                            Expression.DoubleLiteral(text.toDouble())
                        else if (text.contains('L') || text.toLong() > Int.MAX_VALUE)
                            Expression.LongLiteral(text.toLong())
                        else
                            Expression.IntLiteral(text.toInt())
                    } catch (e: NumberFormatException) {
                        // Fallback to string if not a valid number
                        StringLiteral(text)
                    }
                }
            }
        }

        // Handle simple name references (variables)
        is KtNameReferenceExpression -> {
            Expression.VariableRef(getReferencedName())
        }

        is KtBinaryExpression -> {
            val left = left?.toTemplateExpression()
                ?: throw IllegalArgumentException("Missing left operand in binary expression")
            val right = right?.toTemplateExpression()
                ?: throw IllegalArgumentException("Missing right operand in binary expression")

            val binaryOperator = when (operationToken) {
                KtTokens.PLUS -> BinaryOperator.PLUS
                KtTokens.MINUS -> BinaryOperator.MINUS
                KtTokens.MUL -> BinaryOperator.MULTIPLY
                KtTokens.DIV -> BinaryOperator.DIVIDE
                KtTokens.PERC -> BinaryOperator.MODULO
                KtTokens.EQEQ -> BinaryOperator.EQUALS
                KtTokens.EXCLEQ -> BinaryOperator.NOT_EQUALS
                KtTokens.GT -> BinaryOperator.GREATER_THAN
                KtTokens.LT -> BinaryOperator.LESS_THAN
                KtTokens.GTEQ -> BinaryOperator.GREATER_THAN_OR_EQUAL
                KtTokens.LTEQ -> BinaryOperator.LESS_THAN_OR_EQUAL
                KtTokens.ANDAND -> BinaryOperator.AND
                KtTokens.OROR -> BinaryOperator.OR
                KtTokens.ELVIS -> BinaryOperator.ELVIS
                else -> throw IllegalArgumentException("Unsupported binary operator: $operationToken")
            }

            Expression.BinaryOp(binaryOperator, left, right)
        }

        is KtPrefixExpression -> {
            val operator = when(operationToken) {
                KtTokens.EXCL -> PrefixOperator.NOT
                else -> throw IllegalArgumentException("Unsupported prefix operator: ${operationReference.text}")
            }
            Expression.PrefixOp(operator, baseExpression?.toTemplateExpression()
                ?: throw IllegalArgumentException("Missing base expression"))
        }

        // Handle method calls and property access
        is KtCallExpression -> {
            val methodName = calleeExpression?.text ?: throw IllegalArgumentException("Missing method name")
            val args = valueArguments.map { it.getArgumentExpression()?.toTemplateExpression()
                ?: throw IllegalArgumentException("Invalid argument expression") }

            // Determine the receiver (if any)
            val receiverExpression = getQualifiedExpressionForSelector()?.receiverExpression
            val receiver = receiverExpression?.toTemplateExpression()

            Expression.MethodCall(receiver, methodName, args)
        }

        // Handle property access
        is KtDotQualifiedExpression, is KtSafeQualifiedExpression -> {
            val receiver = receiverExpression.toTemplateExpression()

            when (val selector = selectorExpression) {
                is KtNameReferenceExpression -> {
                    val receiverVariable = receiver as? Expression.VariableRef
                        ?: throw IllegalArgumentException("Receiver must be a variable")
                    Expression.VariableRef("${receiverVariable.name}.${selector.getReferencedName()}")
                }
                is KtCallExpression -> {
                    // Method call with receiver (like obj.method())
                    val methodName = selector.calleeExpression?.text
                        ?: throw IllegalArgumentException("Missing method name")
                    val args = selector.valueArguments.map {
                        it.getArgumentExpression()?.toTemplateExpression()
                            ?: throw IllegalArgumentException("Invalid argument expression")
                    }
                    Expression.MethodCall(receiver, methodName, args)
                }
                else -> throw IllegalArgumentException("Unsupported selector expression: ${selector?.text}")
            }
        }

        is KtLambdaExpression -> {
            val parameters = functionLiteral.valueParameters.map { it.name ?: "_" }
            val body = functionLiteral.bodyExpression?.statements?.singleOrNull()
                ?: throw IllegalArgumentException("Lambda must have a single expression body")

            Expression.Lambda(parameters, body.toTemplateExpression())
        }

        is KtParenthesizedExpression -> {
            expression?.toTemplateExpression()
                ?: throw IllegalArgumentException("Empty parenthesized expression")
        }

        is KtPostfixExpression -> {
            val operator = when(operationReference.text) {
                "!!" -> PostfixOperator.NOT_NULL
                else -> throw IllegalArgumentException("Unsupported postfix operator: ${operationReference.text}")
            }
            Expression.PostfixOp(operator, baseExpression?.toTemplateExpression()
                ?: throw IllegalArgumentException("Missing base expression"))
        }

        is KtIfExpression -> {
            Expression.IfElse(
                condition.toTemplateExpression(),
                then.toTemplateExpression(),
                `else`.toTemplateExpression()
            )
        }

        // For if expressions and other complex constructs, you might want to add more cases
        else -> throw IllegalArgumentException("Unsupported expression type: ${this::class.simpleName}")
    }
}
