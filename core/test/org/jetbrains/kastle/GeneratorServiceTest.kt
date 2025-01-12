package org.jetbrains.kastle

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import org.jetbrains.kastle.io.FileSystemFeatureRepository
import org.jetbrains.kastle.io.export
import org.jetbrains.kastle.io.resolve
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.BeforeTest
import kotlin.test.Test

class GeneratorServiceTest {

    private val fs = SystemFileSystem
    private val resourceDir = FileSystemFeatureRepository(Path("testResources"))
    private val generator = ProjectGenerator.fromRepository(resourceDir)
    private lateinit var tempDir: Path

    @BeforeTest
    fun setup() {
        val random = Random(1902190)
        val dirName = (0..9).joinToString("") {
            random.nextInt('a'.code..'z'.code).toChar().toString()
        }
        tempDir = SystemTemporaryDirectory.resolve(dirName)

        fs.createDirectories(tempDir)
    }

    @Test
    fun test() = runTest {
        generator.generate(
            ProjectDescriptor(
                name = "test",
                group = "acme",
                properties = emptyMap(),
                features = emptyList()
            )
        ).export(tempDir)
    }

}