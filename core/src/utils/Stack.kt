package org.jetbrains.kastle.utils

import kotlin.collections.toMutableList

interface Stack<E>: Iterable<E> {
    companion object {
        fun <E> of() = ListStack<E>(mutableListOf())
        fun <E> of(item: E) = ListStack(mutableListOf(item))
        fun <E> Collection<E>.toStack() = ListStack(toMutableList())
    }

    val top: E?
    fun isEmpty(): Boolean = top == null
    fun pop(): E?
    operator fun plusAssign(element: E)
    operator fun dec(): Stack<E> = apply { pop() }
    fun copy(): Stack<E>
}

class ListStack<E>(private val list: MutableList<E> = mutableListOf()) : Stack<E> {
    override val top: E? get() = list.lastOrNull()
    override fun isEmpty(): Boolean = list.isEmpty()
    override fun pop(): E? = list.removeLastOrNull()

    override fun plusAssign(element: E) {
        list.add(element)
    }
    override fun iterator(): Iterator<E> =
        list.asReversed().iterator()
    override fun copy(): Stack<E> =
        ListStack(ArrayList(list))
}

inline fun <E> Stack<E>.popUntil(
    predicate: (E) -> Boolean,
    onPop: (E) -> Unit = {},
): E? {
    while (true) {
        val current = top ?: return null
        if (predicate(current))
            return current
        pop()?.also(onPop)
    }
}
fun <E> Stack<E>.popSequence(): Sequence<E> =
    sequence {
        while (true)
            yield(pop() ?: break)
    }