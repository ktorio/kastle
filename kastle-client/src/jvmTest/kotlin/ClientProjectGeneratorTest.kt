package org.jetbrains.kastle.client

import io.kotest.core.spec.style.StringSpec
import io.ktor.client.HttpClient
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.runTestApplication
import kotlinx.coroutines.*
import kotlinx.io.files.Path
import org.jetbrains.kastle.*
import org.jetbrains.kastle.server.*

val testServer by lazy { TestServer() }

@OptIn(DelicateCoroutinesApi::class)
class ClientProjectGeneratorTest : StringSpec(
    // TODO
//    ProjectGeneratorTest(
//        tearDown = { testServer.stop() }
//    ) {
//        if (!testServer.is
//        Running()) {
//            testServer.start(GlobalScope)
//        }
//        testServer.deferredClient.await().asRepository()
//    }
)

class TestServer {
    val deferredClient = CompletableDeferred<HttpClient>()

    var serverJob: Job? = null

    fun isRunning() = serverJob != null

    fun start(coroutineScope: CoroutineScope) {
        if (isRunning()) return
        serverJob = coroutineScope.launch {
            runTestApplication {
                application {
                    dependencies {
                        provide<PackRepository> { LocalPackRepository(Path("../repository")) }
                        provide<ProjectGenerator> { ProjectGenerator.fromRepository(resolve()) }
                    }
                    json()
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