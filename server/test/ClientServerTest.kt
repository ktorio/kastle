package org.jetbrains.kastle.server

import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.flow.toList
import kotlinx.io.files.Path
import org.jetbrains.kastle.client.asRepository
import org.jetbrains.kastle.*
import org.jetbrains.kastle.io.JsonFilePackRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientServerTest {

    private val backingRepository by lazy {
        JsonFilePackRepository(Path("./json"))
    }

    @Test
    fun `get pack IDs`() = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }
        val repository = client.asRepository()

        val expected = backingRepository.packIds().toList()
            .sortedBy { it.toString() }
            .joinToString("\n")

        val actual = repository.packIds().toList()
            .sortedBy { it.toString() }
            .joinToString("\n")

        assertEquals(expected, actual)
    }

    @Test
    fun `get pack descriptor`() = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }
        val repository = client.asRepository()
        val pack = repository.get("com.acme/empty")
        assertEquals("Empty Feature", pack?.name)
        assertEquals("1.0.0", pack?.version?.toString())
        assertEquals("acme", pack?.group?.id)
        assertEquals("ACME", pack?.group?.name)
    }

}