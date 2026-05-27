package org.jetbrains.kastle

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.kastle.io.CborFilePackRepository
import org.jetbrains.kastle.io.FileFormat
import org.jetbrains.kastle.io.FileSystemPackRepository.Companion.export
import org.jetbrains.kastle.io.deleteRecursively
import kotlin.random.Random

@OptIn(ExperimentalSerializationApi::class)
val CborProjectGeneratorTest by testSuite("Project generator (CBOR)") {
    testProjectGenerator {
        val local = LocalPackRepository(Path(TEST_TEMPLATES_ROOT), random = Random(42L))
        val exportDir = Path(SystemTemporaryDirectory, "cbor_export")
        SystemFileSystem.deleteRecursively(exportDir)
        SystemFileSystem.createDirectories(exportDir)
        local.export(exportDir, fileFormat = FileFormat.CBOR)
        CborFilePackRepository(exportDir)
    }
}
