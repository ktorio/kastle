package org.jetbrains.kastle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull

fun PackRepositoryTest(repository: PackRepository): StringSpec.() -> Unit = {
    "get versions" {
        val catalog = repository.versions()

        catalog.versions.size shouldBeGreaterThan 10
        catalog.libraries.size shouldBeGreaterThan 10
    }

    "read file" {
        repository.readFile("io.ktor/icon.svg").shouldNotBeNull()
    }
}