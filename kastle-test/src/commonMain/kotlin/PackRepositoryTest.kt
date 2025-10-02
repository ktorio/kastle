package org.jetbrains.kastle

import io.kotest.core.spec.style.FunSpec

abstract class PackRepositoryTest(val repository: PackRepository): FunSpec({

    test("get versions") {
        val catalog = repository.versions()

        assert(catalog.versions.size > 10)
        assert(catalog.libraries.size > 10)
    }

})