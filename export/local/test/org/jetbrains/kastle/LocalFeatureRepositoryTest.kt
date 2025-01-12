package org.jetbrains.kastle

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.io.JsonFileFeatureRepository.Companion.exportToJson
import org.jetbrains.kastle.io.deleteRecursively
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LocalFeatureRepositoryTest {

    private val root = "testResources/features"
    private val exportDir = Path(SystemTemporaryDirectory, "features")
    private val repository = LocalFeatureRepository(root)
    private val json = Json { prettyPrint = true }

    @AfterTest
    fun cleanup() {
        SystemFileSystem.deleteRecursively(exportDir)
    }

    @Test
    fun featureIds() = runTest {
        val listOfFeatures = repository.featureIds()
            .map { it.toString() }
            .toList()
            .sorted()
        assertEquals(
            listOf(
                "acme/basic",
                "acme/child",
                "acme/parent",
            ), listOfFeatures
        )
    }

    @Test
    fun basic() = runTest {
        checkBasic(repository.get("acme/basic"))
    }

    @Test
    fun parent() = runTest {
        checkParent(repository.get("acme/parent"))
    }

    @Test
    fun child() = runTest {
        checkChild(repository.get("acme/child"))
    }

    @Test
    fun generator() = runTest {
        val projectFiles = ProjectGenerator.fromRepository(repository)
            .generate(ProjectDescriptor(
                name = "test-project",
                group = "org.test",
                properties = emptyMap(),
                features = listOf(
                    FeatureId("acme", "parent"),
                    FeatureId("acme", "child"),
                ),
            ))
            .toList()
        assertEquals(1, projectFiles.size)
    }

    @Test
    fun exportToJson() = runTest {
        val result = repository.exportToJson(exportDir, json = json)
        checkBasic(result.get("acme/basic"))
        checkParent(result.get("acme/parent"))
        checkChild(result.get("acme/child"))
    }

    private fun checkBasic(descriptor: FeatureDescriptor?) {
        assertNotNull(descriptor, "Missing manifest!")
        assertEquals("acme/basic", descriptor.id.toString())
        assertEquals("Basic Feature", descriptor.name)
        assertEquals("1.0.0", descriptor.version.toString())
        assertEquals("acme", descriptor.group?.id)
        assertEquals("ACME", descriptor.group?.name)
    }

    private fun checkParent(descriptor: FeatureDescriptor?) {
        assertNotNull(descriptor, "Missing manifest!")
        assertEquals(1, descriptor.sources.size, "Should be 1 source file")
        val sourceTemplate = descriptor.sources[0]
        val sourceText = Paths.get("$root/acme/parent/Source.kt").readText()
        assertEquals(sourceText, sourceTemplate.text)
        assertEquals("file:Source.kt", sourceTemplate.target)
        assertEquals(null, sourceTemplate.imports)
        val slot = sourceTemplate.slots?.singleOrNull()
        assertNotNull(slot, "Expected a single slot file")
        assertEquals("install", slot.name)
        assertEquals(SlotPosition.Inline(43 until 62, "Source"), slot.position)
    }

    private fun checkChild(descriptor: FeatureDescriptor?) {
        assertNotNull(descriptor, "Missing manifest!")
        assertEquals(1, descriptor.sources.size, "Should be 1 source file")
        val sourceTemplate = descriptor.sources[0]
        val sourceText = Paths.get("$root/acme/child/Source.kt").readText()
        val (import, _) = sourceText.split('\n', limit = 2)
        assertEquals("// child source here\nprintln(\"working dir: \" + Paths.get(\"\").toString())", sourceTemplate.text.trim())
        assertEquals("slot://acme/parent/install", sourceTemplate.target)
        assertEquals(listOf(import.replaceFirst("import ", "")), sourceTemplate.imports)
    }

}