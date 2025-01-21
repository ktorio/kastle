package org.jetbrains.kastle.utils

fun Any?.isTruthy() = when(this) {
    is Int -> this != 0
    is Boolean -> this
    is String -> this.isNotEmpty()
    else -> this != null
}