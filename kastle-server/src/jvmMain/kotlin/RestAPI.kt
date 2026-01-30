package org.jetbrains.kastle.server

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.encodeToSink
import org.jetbrains.kastle.*

/**
 * Server API called from clients.
 */
fun Routing.backEnd(
    repository: PackRepository,
    generator: ProjectGenerator,
    json: Json,
) {

    route("/api") {
        /**
         * Get a list of all pack ids.
         *
         * Response: 200 application/json A list of all pack IDs.
         */
        get("/packIds") {
            val packIds = repository.ids()
            call.respondBytesWriter(ContentType.Application.Json) {
                writeJsonFlow(packIds, json)
            }
        }
        /**
         * Get the catalog of versions for all artifacts.
         */
        get("/versions") {
            call.respond(repository.versions())
        }
        route("/packs") {
            get {
                val packs = repository.getAll()
                call.respondBytesWriter(ContentType.Application.Json) {
                    writeJsonFlow(packs, json)
                }
            }
            route("/{group}/{id}") {
                get {
                    val id = readPackId() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val repository = repository.read(id) ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.respondText(ContentType.Application.Json) {
                        json.encodeToString(repository)
                    }
                }
                get("/docs") {
                    val id = readPackId() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val docs = repository.readDocs(id) ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.respondText(docs)
                }
            }
        }
        route("/generate") {
            post("/preview") {
                val settings: ProjectDescriptor = call.receive()
                val result: Flow<SourceFileEntry> = generator.generate(settings)
                call.respondBytesWriter(ContentType.Application.Json) {
                    writeByte('{'.code.toByte())
                    result.collectIndexed { i, (path, contents) ->
                        if (i != 0) writeByte(','.code.toByte())
                        writeString("\"$path\":")
                        writeJsonString(contents())
                    }
                    writeByte('}'.code.toByte())
                }
            }
            post("/download") {
                val settings: ProjectDescriptor = call.receive()
                val result: Flow<SourceFileEntry> = generator.generate(settings)
                call.respondProjectDownload(settings.name, result)
            }
        }
    }
}

private fun RoutingContext.readPackId(): PackId? {
    val group = call.parameters["group"] ?: return null
    val pack = call.parameters["id"] ?: return null
    return PackId(group, pack)
}

@OptIn(InternalAPI::class, ExperimentalSerializationApi::class)
private suspend inline fun <reified E> ByteWriteChannel.writeJsonFlow(flow: Flow<E>, json: Json) {
    writeByte('['.code.toByte())
    flow.collectIndexed { i, descriptor ->
        if (i != 0) writeByte(','.code.toByte())
        json.encodeToSink(descriptor, writeBuffer)
        writeString(json.encodeToString(descriptor))
    }
    writeByte(']'.code.toByte())
}