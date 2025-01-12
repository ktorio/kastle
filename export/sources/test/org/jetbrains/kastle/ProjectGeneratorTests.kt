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

private const val defaultName = "sample"
private const val defaultGroup = "com.acme"

class ProjectGeneratorTests {

    private val resources = Path("testResources")
    private val repository = SourceFeatureRepository(Path(resources, "features"))
    private val projectDir = Path(SystemTemporaryDirectory, "features")

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun cleanup() {
        SystemFileSystem.deleteRecursively(projectDir)
    }

    @Test
    fun emptyProject() = runTest {
        generateWithFeatures("acme/basic")
        assertFilesAreEqualWithSnapshot(
            "$resources/projects/empty",
            projectDir.toString(),
        )
    }

    @Test
    fun projectWithSlot() = runTest {
        generateWithFeatures(
            "acme/parent",
            "acme/child"
        )
        assertFilesAreEqualWithSnapshot(
            "$resources/projects/parent-child",
            projectDir.toString(),
        )
    }

    private suspend fun generateWithFeatures(vararg features: String) =
        ProjectGenerator.fromRepository(repository)
            .generate(ProjectDescriptor(
                name = defaultName,
                group = defaultGroup,
                properties = emptyMap(),
                features = features.toList().map(FeatureId::parse),
            )).export(projectDir)

}