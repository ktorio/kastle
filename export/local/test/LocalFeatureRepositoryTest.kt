package org.jetbrains.kastle

import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.io.JsonFileKodRepository.Companion.exportToJson
import org.jetbrains.kastle.io.deleteRecursively
import org.jetbrains.kastle.utils.slots
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LocalKodRepositoryTest {

    private val root = "example"
    private val exportDir = Path(SystemTemporaryDirectory, "kods" + Random.nextInt(9999))
    private val repository = LocalKodRepository(root)
    private val json = Json { prettyPrint = true }

    @AfterTest
    fun cleanup() {
        SystemFileSystem.deleteRecursively(exportDir)
    }

    @Test
    fun kodIds() = runTest {
        val listOfKods = repository.kodIds()
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
            ), listOfKods
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

    private fun checkBasic(descriptor: KodDescriptor?) {
        assertNotNull(descriptor, "Missing manifest!")
        assertEquals("acme/basic", descriptor.id.toString())
        assertEquals("Basic Feature", descriptor.name)
        assertEquals("1.0.0", descriptor.version.toString())
        assertEquals("acme", descriptor.group?.id)
        assertEquals("ACME", descriptor.group?.name)
    }

    private fun checkParent(descriptor: KodDescriptor?) {
        assertNotNull(descriptor, "Missing manifest!")
    }

    private fun checkChild(descriptor: KodDescriptor?) {
        assertNotNull(descriptor, "Missing manifest!")
    }

    private fun checkProperties(descriptor: KodDescriptor?) {
        assertNotNull(descriptor, "Missing manifest!")
    }

}