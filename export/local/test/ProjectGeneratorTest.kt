package org.jetbrains.kastle

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import org.jetbrains.kastle.io.deleteRecursively
import org.jetbrains.kastle.io.export
import org.junit.Test
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

private const val defaultName = "sample"
private const val defaultGroup = "com.acme"
private const val replaceSnapshot = false

abstract class ProjectGeneratorTest {
    companion object {
        val resources = Path("testResources")
    }

    private val projectDir = Path(SystemTemporaryDirectory, "features")
    private val repository by lazy { createRepository() }

    abstract fun createRepository(): FeatureRepository

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun cleanup() {
        SystemFileSystem.deleteRecursively(projectDir)
    }

    @Test
    fun `empty project`() = runTest {
        generateWithFeatures("acme/basic")
        assertFilesAreEqualWithSnapshot(
            "$resources/projects/empty",
            projectDir.toString(),
        )
    }

    @Test
    fun `with slot`() = runTest {
        generateWithFeatures(
            "acme/parent",
            "acme/child",
        )
        assertFilesAreEqualWithSnapshot(
            "$resources/projects/parent-child",
            projectDir.toString(),
        )
    }

    @Test
    fun `with slot and two children`() = runTest {
        generateWithFeatures(
            "acme/parent",
            "acme/child",
            "acme/child2",
        )
        assertFilesAreEqualWithSnapshot(
            "$resources/projects/parent-child2",
            projectDir.toString(),
        )
    }

    @Test
    fun `with properties`() = runTest {
        generate(features = listOf("acme/properties"), properties = mapOf(
            "true-condition" to "true",
            "false-condition" to "false",
            "collection" to listOf("1", "2", "3"),
            "when-property" to "yes",
        ))
        assertFilesAreEqualWithSnapshot(
            "$resources/projects/properties",
            projectDir.toString(),
            replace = replaceSnapshot,
        )
    }

    private suspend fun generateWithFeatures(vararg features: String) =
        generate(features = features.toList())

    private suspend fun generate(
        properties: Map<String, Any> = emptyMap(),
        features: List<String>
    ) = ProjectGenerator.fromRepository(repository)
        .generate(
            ProjectDescriptor(
                name = defaultName,
                group = defaultGroup,
                properties = properties,
                features = features.map(FeatureId::parse),
            )
        ).export(projectDir)

}