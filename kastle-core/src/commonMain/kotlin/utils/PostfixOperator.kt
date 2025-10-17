package org.jetbrains.kastle.utils

enum class PostfixOperator(val string: String, val evaluate: (Any?) -> Any?) {
    INCREMENT("++", { (it as? Number)?.let { num -> num.toInt() + 1 } ?: 1 }),
    DECREMENT("--", { (it as? Number)?.let { num -> num.toInt() - 1 } ?: 1 }),
    NOT_NULL("!!", { it!! });

    override fun toString(): String = string
}