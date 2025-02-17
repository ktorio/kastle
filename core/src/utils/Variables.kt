package org.jetbrains.kastle.utils

import org.jetbrains.kastle.utils.Queue.Companion.toQueue
import kotlin.collections.contains

typealias Variables = Stack<Map<String, Any?>>

operator fun Variables.plusAssign(pair: Pair<String, Any?>) {
    this += mapOf(pair)
}

operator fun Variables.plus(pair: Pair<String, Any?>): Variables = apply {
    this += mapOf(pair)
}

operator fun Variables.get(key: String): Any? {
    val referenceChain = key.split('.').toQueue()
    for (map in this) {
        if (referenceChain.head !in map) continue
        var value = map[referenceChain.remove()]
        while (!referenceChain.isEmpty()) {
            val mapValue = value as? Map<*, *> ?: return null
            value = mapValue[referenceChain.remove()]
        }
        return value
    }
    return null
}