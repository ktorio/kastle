package org.jetbrains.kastle.server

import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.flow.toList
import org.jetbrains.kannotator.client.asRepository
import org.jetbrains.kastle.*
import kotlin.test.Test
import kotlin.test.assertEquals

// TODO replace static expected
private const val expectedPlugins = """
    com.acme/child
    com.acme/child2
    com.acme/empty
    com.acme/parent
    com.acme/properties
    io.ktor/server-cio
    io.ktor/server-core
    std/gradle
"""

class ClientServerTest {

    @Test
    fun `get pack IDs`() = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }
        val repository = client.asRepository()
        val packs = repository.packIds().toList()
            .sortedBy { it.toString() }
            .joinToString("\n")
        assertEquals(expectedPlugins.trimIndent(), packs)
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