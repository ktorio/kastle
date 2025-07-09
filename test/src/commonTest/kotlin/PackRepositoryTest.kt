package org.jetbrains.kastle

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

abstract class PackRepositoryTest(val repository: PackRepository) {

    @Test
    fun versions() = runTest {
        val catalog = repository.versions()
        assertTrue { catalog.versions.size > 10 }
        assertTrue { catalog.libraries.size > 10 }
    }

}