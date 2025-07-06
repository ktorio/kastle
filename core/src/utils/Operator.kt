package org.jetbrains.kastle.utils

enum class Operator(val string: String, val evaluate: (Any?, Any?) -> Any?) {
    // Arithmetic operators
    PLUS("+", { left, right ->
        when {
            left is String || right is String -> left.toString() + right.toString()
            left is Number && right is Number -> {
                when {
                    left is Double || right is Double -> left.toDouble() + right.toDouble()
                    left is Float || right is Float -> left.toFloat() + right.toFloat()
                    left is Long || right is Long -> left.toLong() + right.toLong()
                    else -> left.toInt() + right.toInt()
                }
            }
            else -> throw IllegalArgumentException("Cannot apply + to ${left?.javaClass} and ${right?.javaClass}")
        }
    }),

    MINUS("-", { left, right ->
        if (left is Number && right is Number) {
            when {
                left is Double || right is Double -> left.toDouble() - right.toDouble()
                left is Float || right is Float -> left.toFloat() - right.toFloat()
                left is Long || right is Long -> left.toLong() - right.toLong()
                else -> left.toInt() - right.toInt()
            }
        } else {
            throw IllegalArgumentException("Cannot apply - to ${left?.javaClass} and ${right?.javaClass}")
        }
    }),

    MULTIPLY("*", { left, right ->
        if (left is Number && right is Number) {
            when {
                left is Double || right is Double -> left.toDouble() * right.toDouble()
                left is Float || right is Float -> left.toFloat() * right.toFloat()
                left is Long || right is Long -> left.toLong() * right.toLong()
                else -> left.toInt() * right.toInt()
            }
        } else {
            throw IllegalArgumentException("Cannot apply * to ${left?.javaClass} and ${right?.javaClass}")
        }
    }),

    DIVIDE("/", { left, right ->
        if (left is Number && right is Number) {
            if ((right is Int && right == 0) || (right is Long && right == 0L) ||
                (right is Float && right == 0f) || (right is Double && right == 0.0)) {
                throw ArithmeticException("Division by zero")
            }
            when {
                left is Double || right is Double -> left.toDouble() / right.toDouble()
                left is Float || right is Float -> left.toFloat() / right.toFloat()
                left is Long || right is Long -> left.toLong() / right.toLong()
                else -> left.toInt() / right.toInt()
            }
        } else {
            throw IllegalArgumentException("Cannot apply / to ${left?.javaClass} and ${right?.javaClass}")
        }
    }),

    MODULO("%", { left, right ->
        if (left is Number && right is Number) {
            if ((right is Int && right == 0) || (right is Long && right == 0L) ||
                (right is Float && right == 0f) || (right is Double && right == 0.0)) {
                throw ArithmeticException("Modulo by zero")
            }
            when {
                left is Double || right is Double -> left.toDouble() % right.toDouble()
                left is Float || right is Float -> left.toFloat() % right.toFloat()
                left is Long || right is Long -> left.toLong() % right.toLong()
                else -> left.toInt() % right.toInt()
            }
        } else {
            throw IllegalArgumentException("Cannot apply % to ${left?.javaClass} and ${right?.javaClass}")
        }
    }),

    // Comparison operators
    EQUALS("==", { left, right ->
        left == right
    }),

    NOT_EQUALS("!=", { left, right ->
        left != right
    }),

    GREATER_THAN(">", { left, right ->
        when {
            left is Number && right is Number -> {
                when {
                    left is Double || right is Double -> left.toDouble() > right.toDouble()
                    left is Float || right is Float -> left.toFloat() > right.toFloat()
                    left is Long || right is Long -> left.toLong() > right.toLong()
                    else -> left.toInt() > right.toInt()
                }
            }
            left is String && right is String -> left > right
            else -> throw IllegalArgumentException("Cannot apply > to ${left?.javaClass} and ${right?.javaClass}")
        }
    }),

    LESS_THAN("<", { left, right ->
        when {
            left is Number && right is Number -> {
                when {
                    left is Double || right is Double -> left.toDouble() < right.toDouble()
                    left is Float || right is Float -> left.toFloat() < right.toFloat()
                    left is Long || right is Long -> left.toLong() < right.toLong()
                    else -> left.toInt() < right.toInt()
                }
            }
            left is String && right is String -> left < right
            else -> throw IllegalArgumentException("Cannot apply < to ${left?.javaClass} and ${right?.javaClass}")
        }
    }),

    GREATER_THAN_OR_EQUAL(">=", { left, right ->
        when {
            left is Number && right is Number -> {
                when {
                    left is Double || right is Double -> left.toDouble() >= right.toDouble()
                    left is Float || right is Float -> left.toFloat() >= right.toFloat()
                    left is Long || right is Long -> left.toLong() >= right.toLong()
                    else -> left.toInt() >= right.toInt()
                }
            }
            left is String && right is String -> left >= right
            else -> throw IllegalArgumentException("Cannot apply >= to ${left?.javaClass} and ${right?.javaClass}")
        }
    }),

    LESS_THAN_OR_EQUAL("<=", { left, right ->
        when {
            left is Number && right is Number -> {
                when {
                    left is Double || right is Double -> left.toDouble() <= right.toDouble()
                    left is Float || right is Float -> left.toFloat() <= right.toFloat()
                    left is Long || right is Long -> left.toLong() <= right.toLong()
                    else -> left.toInt() <= right.toInt()
                }
            }
            left is String && right is String -> left <= right
            else -> throw IllegalArgumentException("Cannot apply <= to ${left?.javaClass} and ${right?.javaClass}")
        }
    }),

    // Logical operators
    AND("&&", { left, right ->
        if (left is Boolean && right is Boolean) {
            left && right
        } else {
            throw IllegalArgumentException("Cannot apply && to ${left?.javaClass} and ${right?.javaClass}")
        }
    }),

    OR("||", { left, right ->
        if (left is Boolean && right is Boolean) {
            left || right
        } else {
            throw IllegalArgumentException("Cannot apply || to ${left?.javaClass} and ${right?.javaClass}")
        }
    }),

    // Elvis operator
    ELVIS("?.", { left, right ->
        left ?: right
    });

    override fun toString(): String = string
}