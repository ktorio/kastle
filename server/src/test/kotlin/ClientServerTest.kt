package org.jetbrains.kastle.server

import io.ktor.server.testing.*
import kotlinx.coroutines.flow.toList
import org.jetbrains.kastle.client.asRepository
import org.jetbrains.kastle.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ClientServerTest {

    @Test
    fun `get pack descriptor`() = testApplication {
        configure("application.conf")

        val repository = client.asRepository()
        val pack = repository.get("com.acme/empty")
        assertNotNull(pack, "Missing pack\nActual list:\n  - ${repository.ids().toList().joinToString("\n  - ")}")
        assertEquals("Empty Feature", pack.name)
        assertEquals("1.0.0", pack.version.toString())
        assertEquals("com.acme", pack.group?.id)
    }

}