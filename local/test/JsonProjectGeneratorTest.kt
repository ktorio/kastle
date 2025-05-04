package org.jetbrains.kastle

import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import org.jetbrains.kastle.io.JsonFilePackRepository
import org.jetbrains.kastle.io.JsonFilePackRepository.Companion.exportToJson
import org.jetbrains.kastle.io.deleteRecursively
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.Test

class JsonProjectGeneratorTest: ProjectGeneratorTest() {

    @OptIn(ExperimentalPathApi::class)
    override suspend fun createRepository(): PackRepository {
        val local = LocalPackRepository(Path("../repository"))
        val exportDir = Path(SystemTemporaryDirectory, "json_export")
        SystemFileSystem.deleteRecursively(exportDir)
        SystemFileSystem.createDirectories(exportDir)
        runBlocking {
            local.exportToJson(exportDir)
        }
        return JsonFilePackRepository(exportDir)
    }

    // Needed for Intellij
    @Test
    fun test() {}
}