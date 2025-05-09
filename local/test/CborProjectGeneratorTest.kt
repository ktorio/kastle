package org.jetbrains.kastle

import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.kastle.io.CborFilePackRepository
import org.jetbrains.kastle.io.FileFormat
import org.jetbrains.kastle.io.FileSystemPackRepository.Companion.export
import org.jetbrains.kastle.io.calculateDirectorySize
import org.jetbrains.kastle.io.deleteRecursively
import org.jetbrains.kastle.io.formatToByteSize
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.Test

class CborProjectGeneratorTest: ProjectGeneratorTest() {

    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
    override suspend fun createRepository(): PackRepository {
        val local = LocalPackRepository(Path("../repository"))
        val exportDir = Path(SystemTemporaryDirectory, "cbor_export")
        SystemFileSystem.deleteRecursively(exportDir)
        SystemFileSystem.createDirectories(exportDir)
        runBlocking {
            local.export(exportDir, fileFormat = FileFormat.CBOR)
        }
        val byteSize = SystemFileSystem.calculateDirectorySize(exportDir).formatToByteSize()
        println("Exported $byteSize to $exportDir")
        return CborFilePackRepository(exportDir)
    }

    // Needed for Intellij
    @Test
    fun test() {}
}