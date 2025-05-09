package org.jetbrains.kastle.client

import io.ktor.client.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.io.files.Path
import org.jetbrains.kastle.LocalPackRepository
import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.ProjectGeneratorTest
import org.jetbrains.kastle.server.routing
import org.jetbrains.kastle.server.errorHandling
import org.jetbrains.kastle.server.monitoring
import org.jetbrains.kastle.server.serialization
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

class RemoteProjectGeneratorTest: ProjectGeneratorTest() {
    companion object {
        val deferredClient = CompletableDeferred<HttpClient>()
        var serverJob: Job? = null

        @BeforeAll
        @JvmStatic
        fun startServer() {
            serverJob = CoroutineScope(Dispatchers.IO).launch {
                runTestApplication(Dispatchers.IO) {
                    application {
                        dependencies.provide<PackRepository> {
                            LocalPackRepository(Path("../repository"))
                        }
                        routing()
                        serialization()
                        monitoring()
                        errorHandling()
                    }
                    deferredClient.complete(client)
                    awaitCancellation()
                }
            }
        }

        @AfterAll
        @JvmStatic
        fun stopServer() {
            runBlocking {
                serverJob?.cancelAndJoin()
            }
        }
    }

    override suspend fun createRepository(): PackRepository =
        deferredClient.await().asRepository()

}