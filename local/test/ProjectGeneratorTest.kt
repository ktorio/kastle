package org.jetbrains.kastle

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import org.jetbrains.kastle.io.deleteRecursively
import org.jetbrains.kastle.io.export
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.AfterTest
import kotlin.test.Test

private const val defaultName = "sample"
private const val defaultGroup = "com.acme"
private const val replaceSnapshot = true

abstract class ProjectGeneratorTest {
    companion object {
        val snapshots = Path("testSnapshots")
    }

    private val projectDir = Path(SystemTemporaryDirectory, "packs")
    private val repository by lazy { createRepository() }

    abstract fun createRepository(): PackRepository

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun cleanup() {
        SystemFileSystem.deleteRecursively(projectDir)
    }

    @Test
    fun `empty project`() = runTest {
        generateWithPacks("com.acme/empty")
        assertFilesAreEqualWithSnapshot(
            "$snapshots/empty",
            projectDir.toString(),
        )
    }

    @Test
    fun `with slot`() = runTest {
        generateWithPacks(
            "com.acme/parent",
            "com.acme/child",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/parent-child",
            projectDir.toString(),
        )
    }

    @Test
    fun `with slot and two children`() = runTest {
        generateWithPacks(
            "com.acme/parent",
            "com.acme/child",
            "com.acme/child2",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/parent-child2",
            projectDir.toString(),
        )
    }

    @Test
    fun `with properties`() = runTest {
        generate(packs = listOf("com.acme/properties"), properties = mapOf(
            "numberProperty" to "1",
            "booleanProperty" to "true",
            "nullProperty" to "null",
            "collection" to "1,2,3",
            "whenProperty" to "yes",
            "literal" to "literal",
        ).mapKeys { (key) -> VariableId.parse("com.acme/properties/$key") })
        assertFilesAreEqualWithSnapshot(
            "$snapshots/properties",
            projectDir.toString(),
            replace = replaceSnapshot,
        )
    }

    @Test
    fun `ktor server gradle`() = runTest {
        generateWithPacks(
            "std/gradle",
            "io.ktor/server-core",
            "io.ktor/server-cio",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/ktor-server",
            projectDir.toString(),
            replace = replaceSnapshot,
        )
    }

    @Test
    fun `ktor server gradle with catalog`() = runTest {
        generate(
            packs = listOf(
                "std/gradle",
                "io.ktor/server-core",
                "io.ktor/server-cio",
            ),
            properties = mapOf(
                VariableId.parse("std/gradle/versionCatalogEnabled") to "true",
            )
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/ktor-server-catalog",
            projectDir.toString(),
            replace = replaceSnapshot,
        )
    }

    @Test
    fun `ktor server amper`() = runTest {
        generateWithPacks(
            "std/amper",
            "io.ktor/server-core",
            "io.ktor/server-cio",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/ktor-server-amper",
            projectDir.toString(),
            replace = replaceSnapshot,
        )
    }

    @Test
    fun `compose multiplatform`() = runTest {
        generateWithPacks(
            "std/amper",
            "org.jetbrains/compose-multiplatform",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/compose-multiplatform",
            projectDir.toString(),
            replace = replaceSnapshot,
        )
    }

    private suspend fun generateWithPacks(vararg packs: String) =
        generate(packs = packs.toList())

    private suspend fun generate(
        properties: Map<VariableId, String> = emptyMap(),
        packs: List<String>
    ) = ProjectGenerator.fromRepository(repository)
        .generate(
            ProjectDescriptor(
                name = defaultName,
                group = defaultGroup,
                properties = properties,
                packs = packs.map(PackId::parse),
            )
        ).export(projectDir)

}