package org.jetbrains.kastle.utils

import org.jetbrains.kastle.utils.Queue.Companion.toQueue
import kotlin.collections.contains
import kotlin.collections.get

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
            val mapValue = value.asMap() ?: return null
            val key = referenceChain.remove() ?: return null
            value = mapValue.getCustom(key)
        }
        return value
    }
    return null
}

private fun Any?.asMap(): Map<*, *>? =
    when(this) {
        is Map<*, *> -> this
        is Collection<*> -> mapOf<String, Any>("size" to size)
        is String -> mapOf<String, Any>("length" to length)
        // TODO other stuff
        else -> null
    }

private fun Map<*, *>.getCustom(key: String): Any? =
    get(key) ?: when(key) {
        "entries" -> entries.map {
            mapOf(
                "key" to it.key,
                "value" to it.value
            )
        }
        "keys" -> keys
        "values" -> values
        "size" -> size
        else -> get(key)
    }