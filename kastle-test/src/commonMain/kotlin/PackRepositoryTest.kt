package org.jetbrains.kastle

import io.kotest.core.spec.style.StringSpec

fun PackRepositoryTest(repository: PackRepository): StringSpec.() -> Unit = {
    "get versions" {
        val catalog = repository.versions()

        assert(catalog.versions.size > 10)
        assert(catalog.libraries.size > 10)
    }
}