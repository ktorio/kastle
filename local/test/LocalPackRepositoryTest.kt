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
            .filter { it.group == "com.acme" }
            .map { it.toString() }
            .toList()
            .sorted()
        assertEquals(
            listOf(
                "com.acme/child",
                "com.acme/child2",
                "com.acme/empty",
                "com.acme/parent",
                "com.acme/properties",
            ), listOfPacks
        )
    }

    @Test
    fun empty() = runTest {
        checkEmpty(repository.get("com.acme/empty"))
    }

    @Test
    fun parent() = runTest {
        checkParent(repository.get("com.acme/parent"))
    }

    @Test
    fun child() = runTest {
        checkChild(repository.get("com.acme/child"))
    }

    @Test
    fun properties() = runTest {
        checkProperties(repository.get("com.acme/properties"))
    }

    @Test
    fun exportToJson() = runTest {
        val result = repository.exportToJson(exportDir, json = json)
        checkEmpty(result.get("com.acme/empty"))
        checkParent(result.get("com.acme/parent"))
        checkChild(result.get("com.acme/child"))
        checkProperties(result.get("com.acme/properties"))
    }

    private fun checkEmpty(descriptor: PackDescriptor?) {
        assertNotNull(descriptor, "Missing manifest!")
        assertEquals("com.acme/empty", descriptor.id.toString())
        assertEquals("Empty Feature", descriptor.name)
        assertEquals("1.0.0", descriptor.version.toString())
        assertEquals("acme", descriptor.group?.id)
        assertEquals("ACME", descriptor.group?.name)
    }

    private fun checkParent(descriptor: PackDescriptor?) {
        assertNotNull(descriptor, "Missing manifest!")
        assertNotNull(descriptor.documentation, "Missing documentation!")
    }

    private fun checkChild(descriptor: PackDescriptor?) {
        assertNotNull(descriptor, "Missing manifest!")
        assertNotNull(descriptor.documentation, "Missing documentation!")
    }

    private fun checkProperties(descriptor: PackDescriptor?) {
        assertNotNull(descriptor, "Missing manifest!")
        assertNotNull(descriptor.documentation, "Missing documentation!")
    }

}