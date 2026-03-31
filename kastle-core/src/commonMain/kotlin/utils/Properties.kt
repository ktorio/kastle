package org.jetbrains.kastle.utils

fun Any?.isTruthy() = when(this) {
    is Int -> this != 0
    is Boolean -> this
    is String -> this.isNotEmpty()
    is Collection<*> -> this.isNotEmpty()
    is Map<*, *> -> this.isNotEmpty()
    else -> this != null
}

/**
 * Combines list values for two maps.
 */
fun <K, E> Map<K, List<E>>.merge(other: Map<K, List<E>>): Map<K, List<E>> =
    (keys + other.keys).associateWith { key ->
        this[key].orEmpty() + other[key].orEmpty()
    }
