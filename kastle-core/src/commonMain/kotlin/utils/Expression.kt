package org.jetbrains.kastle.utils

import kotlinx.serialization.Serializable
import kotlin.toString

@Serializable
sealed interface Expression {
    fun evaluate(variables: Variables): Any?

    sealed interface Literal<E> : Expression {
        val value: E
        override fun evaluate(variables: Variables): Any? = value
    }

    @Serializable
    data class StringLiteral(override val value: String) : Literal<String> {
        override fun toString(): String = "\"$value\""
    }

    @Serializable
    data class DoubleLiteral(override val value: Double) : Literal<Double> {
        override fun toString(): String = value.toString()
    }

    @Serializable
    data class LongLiteral(override val value: Long) : Literal<Long> {
        override fun toString(): String = value.toString()
    }

    @Serializable
    data class CharLiteral(override val value: Char) : Literal<Char> {
        override fun toString(): String = "'$value'"
    }

    @Serializable
    data class BooleanLiteral(override val value: Boolean) : Literal<Boolean> {
        override fun toString(): String = value.toString()
    }

    @Serializable
    data object NullLiteral : Literal<Any?> {
        override val value: Any? = null
        override fun toString(): String = "null"

    }

    @Serializable
    data class VariableRef(val name: String) : Expression {
        override fun evaluate(variables: Variables): Any? = variables[name]
        override fun toString(): String = name
    }

    @Serializable
    data class BinaryOp(val op: BinaryOperator, val left: Expression, val right: Expression) : Expression {
        override fun evaluate(variables: Variables): Any? = op.evaluate(left.evaluate(variables), right.evaluate(variables))
        override fun toString(): String = "$left $op $right"
    }

    @Serializable
    data class PostfixOp(val op: PostfixOperator, val target: Expression) : Expression {
        override fun evaluate(variables: Variables): Any? = op.evaluate(target.evaluate(variables))
        override fun toString(): String = "$target$op"
    }

    @Serializable
    data class Lambda(val paramNames: List<String>, val body: Expression) : Expression {
        override fun evaluate(variables: Variables): Any? {
            // Return a function that can be called later with arguments
            return { args: List<Any?> ->
                if (args.size != paramNames.size) {
                    throw IllegalArgumentException("Lambda expected ${paramNames.size} arguments but got ${args.size}")
                }

                // Create a new variables scope that includes the lambda parameters
                variables += paramNames.zip(args).toMap()

                try {
                    // Evaluate the body with the new scope
                    body.evaluate(variables)
                } finally {
                    variables.pop()
                }
            }
        }

        override fun toString(): String = "{ ${paramNames.joinToString()} -> $body }"
    }

    @Serializable
    data class MethodCall(val receiver: Expression?, val methodName: String, val args: List<Expression>) : Expression {
        override fun evaluate(variables: Variables): Any? {
            val evaluatedArgs = args.map { it.evaluate(variables) }

            // Handle static-like utility functions if no receiver
            if (receiver == null) {
                return evaluateStaticMethod(methodName, evaluatedArgs)
            }

            val receiverValue = receiver.evaluate(variables) ?: error("null receiver $receiver for method call $methodName")

            return when (receiverValue) {
                is String -> evaluateStringMethod(receiverValue, methodName, evaluatedArgs)
                is Collection<*> -> evaluateListMethod(receiverValue, methodName, evaluatedArgs)
                is Map<*, *> -> evaluateMapMethod(receiverValue, methodName, evaluatedArgs)
                is Number -> evaluateNumberMethod(receiverValue, methodName, evaluatedArgs)
                is Boolean -> evaluateBooleanMethod(receiverValue, methodName, evaluatedArgs)
                else -> throw IllegalArgumentException("Unsupported receiver type: ${receiverValue::class}, $receiverValue")
            }
        }

        private fun evaluateStaticMethod(methodName: String, args: List<Any?>): Any? {
            return when (methodName) {
                "listOf" -> args
                "mapOf" -> {
                    if (args.size % 2 != 0) {
                        throw IllegalArgumentException("mapOf requires an even number of arguments")
                    }
                    args.chunked(2).associate { (k, v) -> k to v }
                }
                "setOf" -> args.toSet()
                else -> throw IllegalArgumentException("Unknown static method: $methodName")
            }
        }

        private fun evaluateStringMethod(receiver: String, methodName: String, args: List<Any?>): Any? {
            return when (methodName) {
                "length" -> receiver.length
                "isEmpty" -> receiver.isEmpty()
                "isNotEmpty" -> receiver.isNotEmpty()
                "contains" -> {
                    val arg = args.firstOrNull() ?: throw IllegalArgumentException("contains requires an argument")
                    receiver.contains(arg.toString())
                }
                "startsWith" -> {
                    val arg = args.firstOrNull() ?: throw IllegalArgumentException("startsWith requires an argument")
                    receiver.startsWith(arg.toString())
                }
                "endsWith" -> {
                    val arg = args.firstOrNull() ?: throw IllegalArgumentException("endsWith requires an argument")
                    receiver.endsWith(arg.toString())
                }
                "substring" -> {
                    when (args.size) {
                        1 -> {
                            val start = (args[0] as? Number)?.toInt()
                                ?: throw IllegalArgumentException("substring requires integer argument")
                            receiver.substring(start)
                        }
                        2 -> {
                            val start = (args[0] as? Number)?.toInt()
                                ?: throw IllegalArgumentException("substring requires integer arguments")
                            val end = (args[1] as? Number)?.toInt()
                                ?: throw IllegalArgumentException("substring requires integer arguments")
                            receiver.substring(start, end)
                        }
                        else -> throw IllegalArgumentException("substring requires 1 or 2 arguments")
                    }
                }
                "replace" -> {
                    if (args.size != 2) throw IllegalArgumentException("replace requires 2 arguments")
                    receiver.replace(args[0].toString(), args[1].toString())
                }
                "toUpperCase", "uppercase" -> receiver.uppercase()
                "toLowerCase", "lowercase" -> receiver.lowercase()
                "trim" -> receiver.trim()
                "split" -> {
                    val delimiter = args.firstOrNull()?.toString() ?: throw IllegalArgumentException("split requires a delimiter")
                    receiver.split(delimiter)
                }
                else -> throw IllegalArgumentException("Unsupported String method: $methodName")
            }
        }

        override fun toString(): String =
            "${receiver?.let { "$receiver." } ?: ""}$methodName(${args.joinToString()})"

        private fun evaluateListMethod(receiver: Collection<*>, methodName: String, args: List<Any?>): Any? {
            return when (methodName) {
                "size" -> receiver.size
                "isEmpty" -> receiver.isEmpty()
                "isNotEmpty" -> receiver.isNotEmpty()
                "contains" -> {
                    val element = args.firstOrNull() ?: throw IllegalArgumentException("contains requires an argument")
                    receiver.contains(element)
                }
                "indexOf" -> {
                    val element = args.firstOrNull() ?: throw IllegalArgumentException("indexOf requires an argument")
                    receiver.indexOf(element)
                }
                "first" -> receiver.first()
                "single" -> receiver.single()
                "last" -> {
                    if (receiver.isEmpty()) throw NoSuchElementException("List is empty")
                    receiver.last()
                }
                "take" -> {
                    val n = (args.firstOrNull() as? Number)?.toInt()
                        ?: throw IllegalArgumentException("take requires an integer argument")
                    receiver.take(n)
                }
                "drop" -> {
                    val n = (args.firstOrNull() as? Number)?.toInt()
                        ?: throw IllegalArgumentException("drop requires an integer argument")
                    receiver.drop(n)
                }
                "joinToString" -> {
                    val separator = (args.getOrNull(0) as? String) ?: ", "
                    receiver.joinToString(separator)
                }
                "flatten" -> {
                    @Suppress("UNCHECKED_CAST")
                    receiver as? Collection<Collection<*>> ?: error("flatten requires a List<Collection<*>> receiver")
                    receiver.flatten()
                }
                "map" -> {
                    val mapper = args.firstOrNull()
                        ?: throw IllegalArgumentException("map requires a lambda argument")

                    when (mapper) {
                        is Function1<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            val typedMapper = mapper as (Any?) -> Any?
                            receiver.map { typedMapper(it) }
                        }
                        else -> throw IllegalArgumentException("map requires a lambda function argument, got ${mapper::class.simpleName}")
                    }
                }
                "filter" -> {
                    val predicate = args.firstOrNull()
                        ?: throw IllegalArgumentException("filter requires a lambda argument")

                    when (predicate) {
                        is Function1<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            val typedPredicate = predicate as (Any?) -> Boolean
                            receiver.filter { typedPredicate(it) }
                        }
                        else -> throw IllegalArgumentException("filter requires a lambda function argument, got ${predicate::class.simpleName}")
                    }
                }

                else -> throw IllegalArgumentException("Unsupported List method: $methodName")
            }
        }

        private fun evaluateMapMethod(receiver: Map<*, *>, methodName: String, args: List<Any?>): Any? {
            return when (methodName) {
                "size" -> receiver.size
                "isEmpty" -> receiver.isEmpty()
                "isNotEmpty" -> receiver.isNotEmpty()
                "containsKey" -> {
                    val key = args.firstOrNull() ?: throw IllegalArgumentException("containsKey requires a key argument")
                    receiver.containsKey(key)
                }
                "get" -> {
                    val key = args.firstOrNull() ?: throw IllegalArgumentException("get requires a key argument")
                    receiver[key]
                }
                "keys" -> receiver.keys.toList()
                "values" -> receiver.values.toList()
                "entries" -> receiver.entries.map { mapOf("key" to it.key, "value" to it.value) }
                else -> throw IllegalArgumentException("Unsupported Map method: $methodName")
            }
        }

        private fun evaluateNumberMethod(receiver: Number, methodName: String, args: List<Any?>): Any? {
            return when (methodName) {
                "toInt" -> receiver.toInt()
                "toLong" -> receiver.toLong()
                "toFloat" -> receiver.toFloat()
                "toDouble" -> receiver.toDouble()
                "toString" -> receiver.toString()
                else -> throw IllegalArgumentException("Unsupported Number method: $methodName")
            }
        }

        private fun evaluateBooleanMethod(receiver: Boolean, methodName: String, args: List<Any?>): Any? {
            return when (methodName) {
                "toString" -> receiver.toString()
                "not" -> !receiver
                else -> throw IllegalArgumentException("Unsupported Boolean method: $methodName")
            }
        }
    }
}