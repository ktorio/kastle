package org.jetbrains.kastle

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import org.jetbrains.kastle.gen.ProjectResolver
import org.jetbrains.kastle.gen.plus
import org.jetbrains.kastle.gradle.GradleTransformation
import org.jetbrains.kastle.io.export
import org.jetbrains.kastle.logging.ConsoleLogger
import org.jetbrains.kastle.logging.LogLevel
import kotlin.random.Random
import kotlin.test.Test

private const val defaultName = "sample"
private const val defaultGroup = "com.acme"
private const val replaceSnapshot = true

abstract class ProjectGeneratorTest(val snapshots: Path = Path("../testSnapshots")) {
    private lateinit var repository: PackRepository
    private suspend fun getRepository(): PackRepository {
        if (!this::repository.isInitialized)
            repository = createRepository()
        return repository
    }

    abstract suspend fun createRepository(): PackRepository

    private fun randomString() =
        Random(System.currentTimeMillis()).nextLong(111, 999).toString(36)

    @Test
    fun `empty project`() = runTest {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "empty", randomString())
        generateWithPacks(outputDir, "com.acme/empty")
        assertFilesAreEqualWithSnapshot( "$snapshots/empty", outputDir.toString())
    }

    @Test
    fun `with slot`() = runTest {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "parent-child", randomString())
        generateWithPacks(
            outputDir,
            "com.acme/parent",
            "com.acme/child",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/parent-child",
            outputDir.toString(),
        )
    }

    @Test
    fun `with slot and two children`() = runTest {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "parent-child2", randomString())
        generateWithPacks(
            outputDir,
            "com.acme/parent",
            "com.acme/child",
            "com.acme/child2",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/parent-child2",
            outputDir.toString(),
        )
    }

    @Test
    fun `with properties`() = runTest {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "properties", randomString())
        generate(outputDir, packs = listOf("com.acme/properties"), properties = mapOf(
            "numberProperty" to "1",
            "booleanProperty" to "true",
            "nullProperty" to "null",
            "collection" to "1,2,3",
            "whenProperty" to "yes",
            "literal" to "literal",
        ).mapKeys { (key) -> VariableId.Companion.parse("com.acme/properties/$key") })
        assertFilesAreEqualWithSnapshot(
            "$snapshots/properties",
            outputDir.toString(),
            replace = replaceSnapshot,
        )
    }

    @Test
    fun `ktor server gradle`() = runTest {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "ktor-server", randomString())
        generateWithPacks(
            outputDir,
            "org.gradle/gradle",
            "io.ktor/server-core",
            "io.ktor/server-cio",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/ktor-server",
            outputDir.toString(),
            replace = replaceSnapshot,
        )
    }

    @Test
    open fun `ktor server gradle with catalog`() = runTest {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "ktor-server-catalog", randomString())
        generate(
            outputDir,
            packs = listOf(
                "org.gradle/gradle",
                "io.ktor/server-core",
                "io.ktor/server-cio",
                "io.ktor/server-content-negotiation",
                "io.ktor/ktor-kotlinx-serialization-json",
            ),
            properties = mapOf(
                VariableId.parse("org.gradle/gradle/versionCatalogEnabled") to "true",
            )
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/ktor-server-catalog",
            outputDir.toString(),
            replace = replaceSnapshot,
        )
    }

    @Test
    open fun `ktor server amper`() = runTest {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "ktor-server-amper", randomString())
        generateWithPacks(
            outputDir,
            "org.jetbrains/amper",
            "io.ktor/server-core",
            "io.ktor/server-cio",
            "io.ktor/server-content-negotiation",
            "io.ktor/ktor-kotlinx-serialization-json",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/ktor-server-amper",
            outputDir.toString(),
            replace = replaceSnapshot,
        )
    }

    @Test
    open fun `compose multiplatform gradle`() = runTest {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "cmp-gradle", randomString())
        generate(
            outputDir,
            packs = listOf(
                "org.gradle/gradle",
                "org.jetbrains/compose-multiplatform",
            ),
            properties = mapOf(
                VariableId.parse("org.gradle/gradle/versionCatalogEnabled") to "true",
            )
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/cmp-gradle",
            outputDir.toString(),
            replace = replaceSnapshot,
        )
    }

    @Test
    open fun `compose multiplatform amper`() = runTest {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "cmp-amper", randomString())
        generateWithPacks(
            outputDir,
            "org.jetbrains/amper",
            "org.jetbrains/compose-multiplatform",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/cmp-amper",
            outputDir.toString(),
            replace = replaceSnapshot,
        )
    }

    private suspend fun generateWithPacks(outputDir: Path, vararg packs: String) =
        generate(outputDir, packs = packs.toList())

    private suspend fun generate(
        outputDir: Path,
        properties: Map<VariableId, String> = emptyMap(),
        packs: List<String>
    ) = ProjectGeneratorImpl(
        repository = getRepository(),
        projectResolver = ProjectResolver.Default + GradleTransformation,
        log = ConsoleLogger(LogLevel.TRACE),
    ).generate(
        ProjectDescriptor(
            name = defaultName,
            group = defaultGroup,
            properties = properties,
            packs = packs.map(PackId.Companion::parse),
        )
    ).export(outputDir)

}