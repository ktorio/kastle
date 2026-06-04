package org.jetbrains.kastle

import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull

fun TestSuite.testPackRepository(repository: PackRepository) {
    test("get versions") {
        val catalog = repository.versions()

        catalog.versions.size shouldBeGreaterThan 10
        catalog.libraries.size shouldBeGreaterThan 10
    }

    test("read file") {
        repository.readFile("io.ktor/icon.svg").shouldNotBeNull()
    }
}
