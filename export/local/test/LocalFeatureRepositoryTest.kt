package org.jetbrains.kastle

import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.io.JsonFileFeatureRepository.Companion.exportToJson
import org.jetbrains.kastle.io.deleteRecursively
import org.jetbrains.kastle.utils.slots
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LocalFeatureRepositoryTest {

    private val root = "example"
    private val exportDir = Path(SystemTemporaryDirectory, "features" + Random.nextInt(9999))
    private val repository = LocalFeatureRepository(root)
    private val json = Json { prettyPrint = true }

    @AfterTest
    fun cleanup() {
        SystemFileSystem.deleteRecursively(exportDir)
    }

    @Test
    fun featureIds() = runTest {
        val listOfFeatures = repository.featureIds()
            .filter { it.group == "acme" }
            .map { it.toString() }
            .toList()
            .sorted()
        assertEquals(
            listOf(
                "acme/basic",
                "acme/child",
                "acme/child2",
                "acme/parent",
                "acme/properties",
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
    fun properties() = runTest {
        checkProperties(repository.get("acme/properties"))
    }

    @Test
    fun exportToJson() = runTest {
        val result = repository.exportToJson(exportDir, json = json)
        checkBasic(result.get("acme/basic"))
        checkParent(result.get("acme/parent"))
        checkChild(result.get("acme/child"))
        checkProperties(result.get("acme/properties"))
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
        val sourceText = Paths.get("$root/acme/parent/src/Source.kt").readText()
        assertEquals(sourceText, sourceTemplate.text)
        assertEquals("file:Source.kt", sourceTemplate.target)
        assertEquals(null, sourceTemplate.imports)
        val slot = sourceTemplate.slots.singleOrNull()
        assertNotNull(slot, "Expected a single slot file")
        assertEquals("install", slot.name)
        assertEquals(SourcePosition.Inline(43..60, "Parent"), slot.position)
    }

    private fun checkChild(descriptor: FeatureDescriptor?) {
        assertNotNull(descriptor, "Missing manifest!")
        assertEquals(1, descriptor.sources.size, "Should be 1 source file")
        val sourceTemplate = descriptor.sources[0]
        val sourceText = Paths.get("$root/acme/child/src/Source.kt").readText()
        val (import, _) = sourceText.split('\n', limit = 2)
        assertEquals("// child source here\nprintln(\"working dir: \" + Paths.get(\"\").toString())", sourceTemplate.text.trim())
        assertEquals("slot://acme/parent/install", sourceTemplate.target)
        assertEquals(listOf(import.replaceFirst("import ", "")), sourceTemplate.imports)
    }

    private fun checkProperties(descriptor: FeatureDescriptor?) {
        assertNotNull(descriptor, "Missing manifest!")
        assertEquals(4, descriptor.sources.size, "Should be 4 source files")
        val (conditional, each, literal, switch) = descriptor.sources.sortedBy { it.target }
        assertEquals(10, conditional.blocks?.size)
        assertEquals(2, each.blocks?.size)
        assertEquals(1, literal.blocks?.size)
        assertEquals(3, switch.blocks?.size)
    }

}