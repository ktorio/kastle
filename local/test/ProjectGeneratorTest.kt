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
        val resources = Path("testResources")
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
        generateWithPacks("com.acme/basic")
        assertFilesAreEqualWithSnapshot(
            "$resources/projects/empty",
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
            "$resources/projects/parent-child",
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
            "$resources/projects/parent-child2",
            projectDir.toString(),
        )
    }

    @Test
    fun `with properties`() = runTest {
        generate(packs = listOf("com.acme/properties"), properties = mapOf(
            "trueCondition" to "true",
            "falseCondition" to "false",
            "collection" to "1,2,3",
            "whenProperty" to "yes",
            "literal" to "literal",
        ).mapKeys { (key) -> VariableId.parse("com.acme/properties/$key") })
        assertFilesAreEqualWithSnapshot(
            "$resources/projects/properties",
            projectDir.toString(),
            replace = replaceSnapshot,
        )
    }

    @Test
    fun `ktor server`() = runTest {
        generateWithPacks(
            "std/gradle",
            "io.ktor/server-core",
            "io.ktor/server-cio",
        )
        assertFilesAreEqualWithSnapshot(
            "$resources/projects/ktor-server",
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