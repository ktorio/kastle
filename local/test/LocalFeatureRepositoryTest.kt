package org.jetbrains.kastle

import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.io.JsonFilePackRepository.Companion.exportToJson
import org.jetbrains.kastle.io.deleteRecursively
import org.junit.Test
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LocalPackRepositoryTest {

    private val root = "example"
    private val exportDir = Path(SystemTemporaryDirectory, "packs" + Random.nextInt(9999))
    private val repository = LocalPackRepository(root)
    private val json = Json { prettyPrint = true }

    @AfterTest
    fun cleanup() {
        SystemFileSystem.deleteRecursively(exportDir)
    }

    @Test
    fun packIds() = runTest {
        val listOfPacks = repository.packIds()
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
            ), listOfPacks
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

    private fun checkBasic(descriptor: PackDescriptor?) {
        assertNotNull(descriptor, "Missing manifest!")
        assertEquals("acme/basic", descriptor.id.toString())
        assertEquals("Basic Feature", descriptor.name)
        assertEquals("1.0.0", descriptor.version.toString())
        assertEquals("acme", descriptor.group?.id)
        assertEquals("ACME", descriptor.group?.name)
    }

    private fun checkParent(descriptor: PackDescriptor?) {
        assertNotNull(descriptor, "Missing manifest!")
    }

    private fun checkChild(descriptor: PackDescriptor?) {
        assertNotNull(descriptor, "Missing manifest!")
    }

    private fun checkProperties(descriptor: PackDescriptor?) {
        assertNotNull(descriptor, "Missing manifest!")
    }

}