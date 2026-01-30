package org.jetbrains.kastle.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.io.Source
import org.jetbrains.kastle.*
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

class RemoteRepository(private val client: HttpClient): PackRepository {
    constructor(url: Url) : this(HttpClient {
        install(DefaultRequest) {
            url(url)
        }
        install(ContentNegotiation) {
            json()
        }
    })

    override fun ids(): Flow<PackId> = flow {
        emitAll(client.get("/api/packIds").body<List<PackId>>().asFlow())
    }

    override suspend fun get(packId: PackId): PackMetadata? {
        val response = client.get("/api/packs/$packId")
        if (!response.status.isSuccess())
            return null
        return response.body()
    }

    override suspend fun read(packId: PackId): PackDescriptor? {
        val response = client.get("/api/packs/$packId")
        if (!response.status.isSuccess())
            return null
        return response.body()
    }

    override suspend fun readDocs(packId: PackId): String? {
        val response = client.get("/api/packs/$packId/docs")
        if (!response.status.isSuccess())
            return null
        return response.bodyAsText()
    }

    override suspend fun readFile(path: String): Source? {
        val response = client.get("/api/files/$path")
        if (!response.status.isSuccess())
            return null
        return response.body()
    }

    override suspend fun versions(): VersionsCatalog {
        val response = client.get("/api/versions")
        if (!response.status.isSuccess())
            throw RuntimeException("${response.status}: ${response.bodyAsText()}")
        return response.body()
    }

}