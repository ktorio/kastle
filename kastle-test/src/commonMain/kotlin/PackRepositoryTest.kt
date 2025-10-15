package org.jetbrains.kastle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan

fun PackRepositoryTest(repository: PackRepository): StringSpec.() -> Unit = {
    "get versions" {
        val catalog = repository.versions()

        catalog.versions.size shouldBeGreaterThan 10
        catalog.libraries.size shouldBeGreaterThan 10
    }
}