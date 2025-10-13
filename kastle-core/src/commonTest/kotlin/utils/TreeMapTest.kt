
package org.jetbrains.kastle.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import org.jetbrains.kastle.utils.TreeMap.Companion.toTreeMap

class TreeMapTest : FunSpec({

    test("empty map should have size 0 and be empty") {
        val map = TreeMap<String, Int>()

        map.size shouldBe 0
        map.isEmpty() shouldBe true
    }

    test("put should add new entries and return null") {
        val map = TreeMap<String, Int>()

        val result = map.put("apple", 1)

        result shouldBe null
        map.size shouldBe 1
        map["apple"] shouldBe 1
    }

    test("put should update existing entry and return old value") {
        val map = TreeMap<String, Int>()
        map["apple"] = 1

        val result = map.put("apple", 2)

        result shouldBe 1
        map.size shouldBe 1
        map["apple"] shouldBe 2
    }

    test("get should return value for existing key") {
        val map = TreeMap<String, Int>()
        map["apple"] = 1

        map["apple"] shouldBe 1
    }

    test("get should return null for non-existing key") {
        val map = TreeMap<String, Int>()

        map["nonexistent"] shouldBe null
    }

    test("containsKey should return true for existing keys") {
        val map = TreeMap<String, Int>()
        map["apple"] = 1
        map["banana"] = 2

        map.containsKey("apple") shouldBe true
        map.containsKey("banana") shouldBe true
    }

    test("containsKey should return false for non-existing keys") {
        val map = TreeMap<String, Int>()
        map["apple"] = 1

        map.containsKey("banana") shouldBe false
    }

    test("containsValue should return true for existing values") {
        val map = TreeMap<String, Int>()
        map["apple"] = 1
        map["banana"] = 2

        map.containsValue(1) shouldBe true
        map.containsValue(2) shouldBe true
    }

    test("containsValue should return false for non-existing values") {
        val map = TreeMap<String, Int>()
        map["apple"] = 1

        map.containsValue(2) shouldBe false
    }

    test("remove should delete entry and return old value") {
        val map = TreeMap<String, Int>()
        map["apple"] = 1

        val result = map.remove("apple")

        result shouldBe 1
        map.size shouldBe 0
        map.containsKey("apple") shouldBe false
    }

    test("remove should return null for non-existing key") {
        val map = TreeMap<String, Int>()

        val result = map.remove("nonexistent")

        result shouldBe null
    }

    test("clear should remove all entries") {
        val map = TreeMap<String, Int>()
        map["apple"] = 1
        map["banana"] = 2
        map["cherry"] = 3

        map.clear()

        map.size shouldBe 0
        map.isEmpty() shouldBe true
    }

    test("keys should be iterated in sorted order") {
        val map = TreeMap<String, Int>()
        map["cherry"] = 3
        map["apple"] = 1
        map["banana"] = 2
        map["date"] = 4

        map.keys.toList() shouldContainExactly listOf("apple", "banana", "cherry", "date")
    }

    test("entries should be iterated in sorted order by key") {
        val map = TreeMap<String, Int>()
        map["cherry"] = 3
        map["apple"] = 1
        map["banana"] = 2

        val entries = map.entries.toList()

        entries shouldHaveSize 3
        entries[0].key shouldBe "apple"
        entries[0].value shouldBe 1
        entries[1].key shouldBe "banana"
        entries[1].value shouldBe 2
        entries[2].key shouldBe "cherry"
        entries[2].value shouldBe 3
    }

    test("values should maintain order corresponding to sorted keys") {
        val map = TreeMap<String, Int>()
        map["cherry"] = 3
        map["apple"] = 1
        map["banana"] = 2

        map.values.toList() shouldContainExactly listOf(1, 2, 3)
    }

    test("putAll should add all entries from another map") {
        val map = TreeMap<String, Int>()
        val other = mapOf("apple" to 1, "banana" to 2, "cherry" to 3)

        map.putAll(other)

        map.size shouldBe 3
        map["apple"] shouldBe 1
        map["banana"] shouldBe 2
        map["cherry"] shouldBe 3
    }

    test("should handle large number of insertions") {
        val map = TreeMap<Int, String>()
        val count = 1000

        for (i in 0 until count) {
            map[i] = "value$i"
        }

        map.size shouldBe count
        for (i in 0 until count) {
            map[i] shouldBe "value$i"
        }
    }

    test("should maintain order with large number of random insertions") {
        val map = TreeMap<Int, String>()
        val numbers = (0 until 100).shuffled()

        numbers.forEach { map[it] = "value$it" }

        map.keys.toList() shouldContainExactly (0 until 100).toList()
    }

    test("should handle sequential deletions correctly") {
        val map = TreeMap<Int, String>()
        (0 until 10).forEach { map[it] = "value$it" }

        for (i in 0 until 5) {
            map.remove(i)
        }

        map.size shouldBe 5
        map.keys.toList() shouldContainExactly listOf(5, 6, 7, 8, 9)
    }

    test("should handle alternating insertions and deletions") {
        val map = TreeMap<Int, String>()

        map[5] = "five"
        map[3] = "three"
        map[7] = "seven"
        map.remove(3)
        map[1] = "one"
        map[9] = "nine"
        map.remove(7)

        map.keys.toList() shouldContainExactly listOf(1, 5, 9)
    }

    test("iterator should support hasNext and next") {
        val map = TreeMap<String, Int>()
        map["a"] = 1
        map["b"] = 2
        map["c"] = 3

        val iterator = map.keys.iterator()
        val keys = mutableListOf<String>()

        while (iterator.hasNext()) {
            keys.add(iterator.next())
        }

        keys shouldContainExactly listOf("a", "b", "c")
    }

    test("iterator should throw NoSuchElementException when exhausted") {
        val map = TreeMap<String, Int>()
        map["a"] = 1

        val iterator = map.keys.iterator()
        iterator.next()

        shouldThrow<NoSuchElementException> {
            iterator.next()
        }
    }

    test("iterator remove should delete current element") {
        val map = TreeMap<String, Int>()
        map["a"] = 1
        map["b"] = 2
        map["c"] = 3

        val iterator = map.keys.iterator()
        iterator.next() // "a"
        iterator.next() // "b"
        iterator.remove()

        map shouldNotContainKey "b"
        map.size shouldBe 2
        map.keys.toList() shouldContainExactly listOf("a", "c")
    }

    test("iterator remove should throw IllegalStateException if called before next") {
        val map = TreeMap<String, Int>()
        map["a"] = 1

        val iterator = map.keys.iterator()

        shouldThrow<IllegalStateException> {
            iterator.remove()
        }
    }

    // TODO
    xtest("entry setValue should update the value") {
        val map = TreeMap<String, Int>()
        map["apple"] = 1

        val entry = map.entries.first()
        val oldValue = entry.setValue(10)

        oldValue shouldBe 1
        map["apple"] shouldBe 10
    }

    test("should work with different comparable types") {
        val intMap = TreeMap<Int, String>()
        intMap[3] = "three"
        intMap[1] = "one"
        intMap[2] = "two"

        intMap.keys.toList() shouldContainExactly listOf(1, 2, 3)
    }

    test("toTreeMap extension should create TreeMap from regular map") {
        val regularMap = mapOf("cherry" to 3, "apple" to 1, "banana" to 2)

        val treeMap = regularMap.toTreeMap()

        treeMap.keys.toList() shouldContainExactly listOf("apple", "banana", "cherry")
    }

    test("should handle duplicate puts correctly") {
        val map = TreeMap<String, Int>()

        map["key"] = 1
        map["key"] = 2
        map["key"] = 3

        map.size shouldBe 1
        map["key"] shouldBe 3
    }

    test("should maintain correctness after removing all elements") {
        val map = TreeMap<String, Int>()
        map["a"] = 1
        map["b"] = 2
        map["c"] = 3

        map.remove("a")
        map.remove("b")
        map.remove("c")

        map.isEmpty() shouldBe true
        map.size shouldBe 0
    }

    test("should allow re-adding removed elements") {
        val map = TreeMap<String, Int>()
        map["key"] = 1
        map.remove("key")
        map["key"] = 2

        map["key"] shouldBe 2
        map.size shouldBe 1
    }

    test("entries should be equal to expected entries") {
        val map = TreeMap<String, Int>()
        map["a"] = 1
        map["b"] = 2

        val entries = map.entries.toList()

        entries[0].key shouldBe "a"
        entries[0].value shouldBe 1
        entries[1].key shouldBe "b"
        entries[1].value shouldBe 2
    }

    test("empty map iteration should not execute") {
        val map = TreeMap<String, Int>()
        var count = 0

        for (entry in map) {
            count++
        }

        count shouldBe 0
    }

    test("should handle single element correctly") {
        val map = TreeMap<String, Int>()

        map["only"] = 1

        map.size shouldBe 1
        map["only"] shouldBe 1
        map.keys.single() shouldBe "only"

        map.remove("only")

        map.isEmpty() shouldBe true
    }

    test("should maintain order with negative numbers") {
        val map = TreeMap<Int, String>()

        map[5] = "five"
        map[-3] = "minus three"
        map[0] = "zero"
        map[-10] = "minus ten"
        map[2] = "two"

        map.keys.toList() shouldContainExactly listOf(-10, -3, 0, 2, 5)
    }

    test("values collection should contain all values") {
        val map = TreeMap<String, Int>()
        map["a"] = 1
        map["b"] = 2
        map["c"] = 3

        val values = map.values

        values shouldContain 1
        values shouldContain 2
        values shouldContain 3
        values shouldHaveSize 3
    }
})