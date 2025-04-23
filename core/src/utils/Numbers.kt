package org.jetbrains.kastle.utils

fun sumOfNotNull(vararg args: Int?): Int {
    var sum = 0
    for (arg in args)
        sum += arg ?: continue
    return sum
}