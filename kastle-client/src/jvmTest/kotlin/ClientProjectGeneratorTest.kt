package org.jetbrains.kastle.client

import io.ktor.client.HttpClient
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.runTestApplication
import kotlinx.coroutines.*
import kotlinx.io.files.Path
import org.jetbrains.kastle.*
import org.jetbrains.kastle.server.*

val testServer by lazy { TestServer() }

@OptIn(DelicateCoroutinesApi::class)
class ClientProjectGeneratorTest: ProjectGeneratorTest({
    if (testServer.isRunning()) {
        testServer.start(GlobalScope)
    }
    testServer.deferredClient.await().asRepository()
}, {
    testServer.stop()
})

class TestServer {
    val deferredClient = CompletableDeferred<HttpClient>()

    var serverJob: Job? = null

    fun isRunning() = serverJob != null

    fun start(coroutineScope: CoroutineScope) {
        if (isRunning()) return
        serverJob = coroutineScope.launch {
            runTestApplication {
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

    suspend fun stop() {
        serverJob?.cancelAndJoin()
    }
}