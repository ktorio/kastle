package org.jetbrains.kastle.utils

/**
 * A Red-Black tree based implementation of a sorted map for Kotlin common code.
 * Provides O(log n) time cost for containsKey, get, put and remove operations.
 */
class TreeMap<K : Comparable<K>, V> : MutableMap<K, V> {
    companion object {
        fun <K: Comparable<K>, V> Map<K, V>.toTreeMap(): TreeMap<K, V> =
            TreeMap<K, V>().apply { putAll(this@toTreeMap) }
    }

    private var root: Node<K, V>? = null
    override var size: Int = 0
        private set

    private enum class Color { RED, BLACK }

    private class Node<K : Comparable<K>, V>(
        var key: K,
        var value: V,
        var color: Color = Color.RED,
        var left: Node<K, V>? = null,
        var right: Node<K, V>? = null,
        var parent: Node<K, V>? = null
    )

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = EntrySet()

    override val keys: MutableSet<K>
        get() = KeySet()

    override val values: MutableCollection<V>
        get() = ValueCollection()

    override fun clear() {
        root = null
        size = 0
    }

    override fun isEmpty(): Boolean = size == 0

    override fun containsKey(key: K): Boolean = getNode(key) != null

    override fun containsValue(value: V): Boolean {
        return entries.any { it.value == value }
    }

    override fun get(key: K): V? = getNode(key)?.value

    override fun put(key: K, value: V): V? {
        var node = root
        var parent: Node<K, V>? = null

        while (node != null) {
            parent = node
            val cmp = key.compareTo(node.key)
            node = when {
                cmp < 0 -> node.left
                cmp > 0 -> node.right
                else -> {
                    val oldValue = node.value
                    node.value = value
                    return oldValue
                }
            }
        }

        val newNode = Node(key, value, Color.RED, parent = parent)
        if (parent == null) {
            root = newNode
        } else {
            if (key < parent.key) {
                parent.left = newNode
            } else {
                parent.right = newNode
            }
        }

        fixAfterInsertion(newNode)
        size++
        return null
    }

    override fun putAll(from: Map<out K, V>) {
        from.forEach { (key, value) -> put(key, value) }
    }

    override fun remove(key: K): V? {
        val node = getNode(key) ?: return null
        val oldValue = node.value
        deleteNode(node)
        size--
        return oldValue
    }

    private fun getNode(key: K): Node<K, V>? {
        var node = root
        while (node != null) {
            val cmp = key.compareTo(node.key)
            node = when {
                cmp < 0 -> node.left
                cmp > 0 -> node.right
                else -> return node
            }
        }
        return null
    }

    private fun fixAfterInsertion(node: Node<K, V>) {
        var x = node
        x.color = Color.RED

        while (x != root && x.parent?.color == Color.RED) {
            val parent = x.parent ?: break
            val grandparent = parent.parent ?: break

            if (parent == grandparent.left) {
                val uncle = grandparent.right
                if (uncle?.color == Color.RED) {
                    parent.color = Color.BLACK
                    uncle.color = Color.BLACK
                    grandparent.color = Color.RED
                    x = grandparent
                } else {
                    if (x == parent.right) {
                        x = parent
                        rotateLeft(x)
                    }
                    x.parent?.color = Color.BLACK
                    x.parent?.parent?.color = Color.RED
                    x.parent?.parent?.let { rotateRight(it) }
                }
            } else {
                val uncle = grandparent.left
                if (uncle?.color == Color.RED) {
                    parent.color = Color.BLACK
                    uncle.color = Color.BLACK
                    grandparent.color = Color.RED
                    x = grandparent
                } else {
                    if (x == parent.left) {
                        x = parent
                        rotateRight(x)
                    }
                    x.parent?.color = Color.BLACK
                    x.parent?.parent?.color = Color.RED
                    x.parent?.parent?.let { rotateLeft(it) }
                }
            }
        }
        root?.color = Color.BLACK
    }

    private fun deleteNode(node: Node<K, V>) {
        var nodeToDelete = node

        if (node.left != null && node.right != null) {
            val successor = successor(node)!!
            node.key = successor.key
            node.value = successor.value
            nodeToDelete = successor
        }

        val replacement = nodeToDelete.left ?: nodeToDelete.right

        if (replacement != null) {
            replacement.parent = nodeToDelete.parent
            if (nodeToDelete.parent == null) {
                root = replacement
            } else if (nodeToDelete == nodeToDelete.parent?.left) {
                nodeToDelete.parent?.left = replacement
            } else {
                nodeToDelete.parent?.right = replacement
            }

            nodeToDelete.left = null
            nodeToDelete.right = null
            nodeToDelete.parent = null

            if (nodeToDelete.color == Color.BLACK) {
                fixAfterDeletion(replacement)
            }
        } else if (nodeToDelete.parent == null) {
            root = null
        } else {
            if (nodeToDelete.color == Color.BLACK) {
                fixAfterDeletion(nodeToDelete)
            }

            if (nodeToDelete.parent != null) {
                if (nodeToDelete == nodeToDelete.parent?.left) {
                    nodeToDelete.parent?.left = null
                } else if (nodeToDelete == nodeToDelete.parent?.right) {
                    nodeToDelete.parent?.right = null
                }
                nodeToDelete.parent = null
            }
        }
    }

    private fun fixAfterDeletion(node: Node<K, V>) {
        var x = node

        while (x != root && x.color == Color.BLACK) {
            val parent = x.parent ?: break

            if (x == parent.left) {
                var sibling = parent.right

                if (sibling?.color == Color.RED) {
                    sibling.color = Color.BLACK
                    parent.color = Color.RED
                    rotateLeft(parent)
                    sibling = parent.right
                }

                if ((sibling?.left?.color ?: Color.BLACK) == Color.BLACK &&
                    (sibling?.right?.color ?: Color.BLACK) == Color.BLACK
                ) {
                    sibling?.color = Color.RED
                    x = parent
                } else {
                    if ((sibling?.right?.color ?: Color.BLACK) == Color.BLACK) {
                        sibling?.left?.color = Color.BLACK
                        sibling?.color = Color.RED
                        sibling?.let { rotateRight(it) }
                        sibling = parent.right
                    }
                    sibling?.color = parent.color
                    parent.color = Color.BLACK
                    sibling?.right?.color = Color.BLACK
                    rotateLeft(parent)
                    x = root!!
                }
            } else {
                var sibling = parent.left

                if (sibling?.color == Color.RED) {
                    sibling.color = Color.BLACK
                    parent.color = Color.RED
                    rotateRight(parent)
                    sibling = parent.left
                }

                if ((sibling?.right?.color ?: Color.BLACK) == Color.BLACK &&
                    (sibling?.left?.color ?: Color.BLACK) == Color.BLACK
                ) {
                    sibling?.color = Color.RED
                    x = parent
                } else {
                    if ((sibling?.left?.color ?: Color.BLACK) == Color.BLACK) {
                        sibling?.right?.color = Color.BLACK
                        sibling?.color = Color.RED
                        sibling?.let { rotateLeft(it) }
                        sibling = parent.left
                    }
                    sibling?.color = parent.color
                    parent.color = Color.BLACK
                    sibling?.left?.color = Color.BLACK
                    rotateRight(parent)
                    x = root!!
                }
            }
        }

        x.color = Color.BLACK
    }

    private fun rotateLeft(node: Node<K, V>) {
        val right = node.right ?: return
        node.right = right.left
        if (right.left != null) {
            right.left?.parent = node
        }
        right.parent = node.parent
        if (node.parent == null) {
            root = right
        } else if (node == node.parent?.left) {
            node.parent?.left = right
        } else {
            node.parent?.right = right
        }
        right.left = node
        node.parent = right
    }

    private fun rotateRight(node: Node<K, V>) {
        val left = node.left ?: return
        node.left = left.right
        if (left.right != null) {
            left.right?.parent = node
        }
        left.parent = node.parent
        if (node.parent == null) {
            root = left
        } else if (node == node.parent?.right) {
            node.parent?.right = left
        } else {
            node.parent?.left = left
        }
        left.right = node
        node.parent = left
    }

    private fun successor(node: Node<K, V>): Node<K, V>? {
        node.right?.let {
            var current = it
            while (current.left != null) {
                current = current.left!!
            }
            return current
        }

        var current = node
        var parent = node.parent
        while (parent != null && current == parent.right) {
            current = parent
            parent = parent.parent
        }
        return parent
    }

    private inner class EntrySet : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
        override val size: Int get() = this@TreeMap.size

        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
            throw UnsupportedOperationException()
        }

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
            return EntryIterator()
        }
    }

    private inner class KeySet : AbstractMutableSet<K>() {
        override val size: Int get() = this@TreeMap.size

        override fun add(element: K): Boolean {
            throw UnsupportedOperationException()
        }

        override fun iterator(): MutableIterator<K> = KeyIterator()
    }

    private inner class ValueCollection : AbstractMutableCollection<V>() {
        override val size: Int get() = this@TreeMap.size

        override fun add(element: V): Boolean {
            throw UnsupportedOperationException()
        }

        override fun iterator(): MutableIterator<V> = ValueIterator()
    }

    private inner class EntryIterator : MutableIterator<MutableMap.MutableEntry<K, V>> {
        private var next: Node<K, V>? = firstNode()
        private var lastReturned: Node<K, V>? = null

        override fun hasNext(): Boolean = next != null

        override fun next(): MutableMap.MutableEntry<K, V> {
            val node = next ?: throw NoSuchElementException()
            lastReturned = node
            next = successor(node)
            return Entry(node.key, node.value)
        }

        override fun remove() {
            val node = lastReturned ?: throw IllegalStateException()
            remove(node.key)
            lastReturned = null
        }

        private fun firstNode(): Node<K, V>? {
            var node = root
            if (node != null) {
                while (node?.left != null) {
                    node = node.left
                }
            }
            return node
        }
    }

    private inner class KeyIterator : MutableIterator<K> {
        private val entryIterator = EntryIterator()

        override fun hasNext(): Boolean = entryIterator.hasNext()
        override fun next(): K = entryIterator.next().key
        override fun remove() = entryIterator.remove()
    }

    private inner class ValueIterator : MutableIterator<V> {
        private val entryIterator = EntryIterator()

        override fun hasNext(): Boolean = entryIterator.hasNext()
        override fun next(): V = entryIterator.next().value
        override fun remove() = entryIterator.remove()
    }

    private class Entry<K, V>(
        override val key: K,
        override var value: V
    ) : MutableMap.MutableEntry<K, V> {
        override fun setValue(newValue: V): V {
            val oldValue = value
            value = newValue
            return oldValue
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Map.Entry<*, *>) return false
            return key == other.key && value == other.value
        }

        override fun hashCode(): Int {
            return (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)
        }

        override fun toString(): String = "$key=$value"
    }
}