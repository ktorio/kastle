package org.jetbrains.kastle.client

import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.runTestApplication
import kotlinx.coroutines.*
import kotlinx.io.files.Path
import org.jetbrains.kastle.*
import org.jetbrains.kastle.client.asRepository
import org.junit.jupiter.api.*
import java.net.http.HttpClient

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