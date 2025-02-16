package org.jetbrains.kastle.utils

import kotlin.collections.contains

typealias Variables = Stack<Map<String, Any?>>

operator fun Variables.plusAssign(pair: Pair<String, Any?>) {
    this += mapOf(pair)
}

operator fun Variables.get(key: String): Any? {
    for (map in this) {
        if (key in map)
            return map[key]
    }
    return null
}