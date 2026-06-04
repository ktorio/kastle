package org.jetbrains.kastle.utils

enum class PrefixOperator(val string: String, val evaluate: (Any?) -> Any?) {
    NOT("!", { !it.isTruthy() });
    // implement if needed: -expr, +expr, --expr, ++expr

    override fun toString(): String = string
}
