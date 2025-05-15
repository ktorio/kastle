package org.jetbrains.kastle

import com.akuleshov7.ktoml.Toml
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.serialization.encodeToString
import kotlin.test.Test

class LocalPackRepositoryTest: PackRepositoryTest(
    LocalPackRepository(Path("../repository"))
) {

    @Test
    fun exportVersions() = runTest {
        val result = Toml.encodeToString(repository.versions())
        println(result)
    }

}