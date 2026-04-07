package org.jetbrains.kastle.utils

import org.jetbrains.kastle.PackId
import org.jetbrains.kastle.VariableId
import org.jetbrains.kastle.utils.Queue.Companion.toQueue
import kotlin.collections.contains
import kotlin.collections.get

interface Variables {
    val stack: Stack<MutableMap<VariableId, Any?>>

    /**
     * Add a single-variable scope to the stack.
     */
    operator fun plusAssign(other: Variables)

    /**
     * Create a new variables instance with the added scopes.
     */
    operator fun plus(other: Variables): Variables =
        Variables(stack.map { it.toMutableMap() } + other.stack.map { it.toMutableMap() })

    /**
     * Get a variable by its fully-qualified ID.
     */
    operator fun get(key: VariableId): Any? = get(key.packId, key.name)

    /**
     * Set a variable by its fully-qualified ID.
     */
    operator fun set(key: VariableId, value: Any?)

    /**
     * Get a variable by its local name.
     *
     * This allows for nested variables, e.g. `foo.bar.baz`.
     */
    operator fun get(packId: PackId, name: String): Any?

    /**
     * Get a copy of this [Variables] scoped to the given [packId].
     */
    fun relativeTo(vararg packIds: PackId): LocalVariables =
        LocalVariables(listOf(*packIds), this)

    /**
     * Remove the top scope from the stack.
     */
    fun pop(): MutableMap<VariableId, Any?>? =
        stack.pop()
}

class LocalVariables(
    val packIds: List<PackId>,
    val variables: Variables
) : Variables by variables {
    constructor(
        packId: PackId,
        map: Map<String, Any?>
    ) : this(
        listOf(packId),
        Variables(map.mapKeys { VariableId(packId, it.key) })
    )
    init {
        require(packIds.isNotEmpty()) { "LocalVariables must have at least one packId" }
    }

    override fun relativeTo(vararg packIds: PackId): LocalVariables {
        return LocalVariables(this.packIds + packIds, this)
    }

    operator fun get(name: String): Any? {
        for (packId in packIds.asReversed()) {
            val value = variables[packId, name]
            if (value != null) return value
        }
        return null
    }

    operator fun plus(other: LocalVariables): LocalVariables =
        LocalVariables(packIds, variables + other.variables)

    operator fun plusAssign(map: Map<String, Any?>) {
        variables += Variables(map.mapKeys { VariableId(packIds.last(), it.key) })
    }

    operator fun plusAssign(pair: Pair<String, Any?>) {
        val (key, value) = pair
        variables += Variables(VariableId(packIds.last(), key) to value)
    }

    operator fun set(key: String, value: Any?) {
        variables[VariableId(packIds.last(), key)] = value
    }
}

internal class VariablesImpl(
    override val stack: Stack<MutableMap<VariableId, Any?>>,
) : Variables {
    override fun plusAssign(other: Variables) {
        stack += other.stack
    }

    override fun set(key: VariableId, value: Any?) {
        stack.last()[key] = value
    }

    override fun get(packId: PackId, name: String): Any? {
        val referenceChain = name.split('.').toQueue()
        if (referenceChain.isEmpty()) return null
        for (map in stack) {
            if (VariableId(packId, referenceChain.head!!) !in map) continue
            var value = map[VariableId(packId, referenceChain.remove()!!)]
            while (!referenceChain.isEmpty()) {
                val mapValue = value.asMap() ?: return null
                val key = referenceChain.remove() ?: return null
                value = mapValue.getCustom(key)
            }
            return value
        }
        return null
    }

    override fun pop(): MutableMap<VariableId, Any?>? {
        return stack.pop()
    }
}

fun Variables(map: Map<VariableId, Any?>): Variables =
    VariablesImpl(ListStack(mutableListOf(map.toMutableMap())))

fun Variables(maps: Iterable<Map<VariableId, Any?>>): Variables =
    VariablesImpl(ListStack(maps.map { it.toMutableMap() }.toMutableList()))

fun Variables(vararg pairs: Pair<VariableId, Any?>): Variables =
    VariablesImpl(ListStack(mutableListOf(mutableMapOf(*pairs))))

// used in loops where handlebars adds element properties to the scope
@Suppress("UNCHECKED_CAST")
fun LocalVariables.addVariableOrScope(key: String?, value: Any?) {
    this += when(key) {
        null -> {
            val elementFields = value as? Map<String, Any> ?: emptyMap()
            mapOf("this" to value, *elementFields.toList().toTypedArray())
        }
        else -> mapOf(key to value)
    }
}

/**
 * Returns a map view of the current object based on what is
 * expected from the scripting environment.
 */
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
