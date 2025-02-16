package org.jetbrains.kastle.utils

interface Stack<E>: Iterable<E> {
    companion object {
        fun <E> of() = of<E>(emptyList())
        fun <E> of(item: E) = of(listOf(item))
        fun <E> of(list: List<E>) = ListStack(list.toMutableList())
    }

    val top: E?
    fun pop(): E?
    operator fun plusAssign(element: E)
    operator fun dec(): Stack<E> = apply { pop() }
    fun copy(): Stack<E>
}

class ListStack<E>(private val list: MutableList<E>) : Stack<E> {
    override val top: E? get() = list.lastOrNull()
    override fun pop(): E? =
        list.removeLastOrNull()
    override fun plusAssign(element: E) {
        list.add(element)
    }
    override fun iterator(): Iterator<E> =
        list.asReversed().iterator()
    override fun copy(): Stack<E> =
        Stack.of<E>(ArrayList<E>(list))
}

inline fun <E> Stack<E>.popUntil(
    predicate: (E) -> Boolean,
    onPop: (E) -> Unit = {},
) {
    while (true) {
        val current = top ?: return
        if (predicate(current))
            return
        pop()?.also(onPop)
    }
}
fun <E> Stack<E>.popSequence(): Sequence<E> =
    sequence {
        while (true)
            yield(pop() ?: break)
    }