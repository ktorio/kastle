package org.jetbrains.kastle.utils

interface Queue<E> : Iterable<E> {
    companion object {
        fun <E> of() = ListQueue<E>(mutableListOf())
        fun <E> of(item: E) = ListQueue(mutableListOf(item))
        fun <E> Collection<E>.toQueue() = ListQueue(toMutableList())
    }

    val head: E?
    fun isEmpty(): Boolean = head == null
    fun remove(): E?
    operator fun plusAssign(element: E)
    operator fun dec(): Queue<E> = apply { remove() }
    fun copy(): Queue<E>
}

class ListQueue<E>(private val list: MutableList<E>) : Queue<E> {
    override val head: E? get() = list.firstOrNull()
    override fun isEmpty(): Boolean = list.isEmpty()
    override fun remove(): E? = list.removeFirstOrNull()
    override fun plusAssign(element: E) {
        list.add(element)
    }
    override fun copy(): Queue<E> = ListQueue(ArrayList<E>(list))
    override fun iterator(): Iterator<E> = list.iterator()
}