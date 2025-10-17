package org.jetbrains.kastle.templates

import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kastle.utils.Expression
import org.jetbrains.kastle.utils.BinaryOperator
import org.jetbrains.kastle.utils.PostfixOperator
import org.jetbrains.kotlin.lexer.KtTokens

fun KtExpression?.toTemplateExpression(): Expression {
    return when (this) {
        null -> Expression.NullLiteral

        // Handle string literals
        // TODO this is not ok
        is KtStringTemplateExpression -> {
            if (entries.isEmpty()) {
                // Empty string
                Expression.StringLiteral("")
            } else if (entries.size == 1 && entries[0] is KtLiteralStringTemplateEntry) {
                // Simple string without interpolation
                Expression.StringLiteral((entries[0] as KtLiteralStringTemplateEntry).text)
            } else {
                // More complex string template - we could process interpolation here
                // For now, just concatenate text parts
                val text = entries.joinToString("") {
                    when (it) {
                        is KtLiteralStringTemplateEntry -> it.text
                        is KtSimpleNameStringTemplateEntry -> "\${${it.expression?.text ?: ""}}"
                        else -> it.text
                    }
                }
                Expression.StringLiteral(text)
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
                            Expression.StringLiteral(text.substring(1, text.length - 1))
                        else if (text.startsWith('\'') && text.endsWith('\''))
                            Expression.CharLiteral(text[1]) // TODO handle escape
                        else if (text.contains('.'))
                            Expression.DoubleLiteral(text.toDouble())
                        else
                            Expression.LongLiteral(text.toLong())
                    } catch (e: NumberFormatException) {
                        // Fallback to string if not a valid number
                        Expression.StringLiteral(text)
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
        is KtDotQualifiedExpression -> {
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

        // For if expressions and other complex constructs, you might want to add more cases
        else -> throw IllegalArgumentException("Unsupported expression type: ${this::class.simpleName}")
    }
}