package org.jetbrains.kastle.client

import io.ktor.client.HttpClient
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.runTestApplication
import kotlinx.coroutines.*
import kotlinx.io.files.Path
import org.jetbrains.kastle.*
import org.jetbrains.kastle.server.*

val testServer by lazy { TestServer() }

class RemoteProjectGeneratorTest: ProjectGeneratorTest({
    testServer.deferredClient.await().asRepository()
}, {
    testServer.stop()
})

class TestServer {
    val deferredClient = CompletableDeferred<HttpClient>()
    var serverJob: Result<Job> = runCatching {
        CoroutineScope(Dispatchers.IO).launch {
            runTestApplication(Dispatchers.IO) {
                application {
                    dependencies.provide<PackRepository> {
                        LocalPackRepository(Path("../repository/packs"))
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
        serverJob.getOrThrow().cancelAndJoin()
    }
}