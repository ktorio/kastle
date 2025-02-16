package org.jetbrains.kannotator.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.PackId
import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.Url

fun HttpClient.asRepository(url: String? = null): RemoteRepository =
    RemoteRepository(config {
        url?.let {
            install(DefaultRequest) {
                url(url)
            }
        }
        install(ContentNegotiation) {
            json()
        }
    })

class RemoteRepository constructor(private val client: HttpClient): PackRepository {
    constructor(url: Url) : this(HttpClient {
        install(DefaultRequest) {
            url(url)
        }
        install(ContentNegotiation) {
            json()
        }
    })

    override fun packIds(): Flow<PackId> = flow {
        emitAll(client.get("/api/packIds").body<List<PackId>>().asFlow())
    }

    override suspend fun get(packId: PackId): PackDescriptor? =
        client.get("/api/packs/$packId").body<PackDescriptor>()
}