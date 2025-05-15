package org.jetbrains.kastle.server

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
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
        get("/packIds") {
            val repositoryIds = repository.all().map { it.id }
            call.respondBytesWriter(ContentType.Application.Json) {
                var first = true
                writeByte('['.code.toByte())
                repositoryIds.collect { id ->
                    if (!first) writeByte(','.code.toByte())
                    else first = false
                    writeString(json.encodeToString(id))
                }
                writeByte(']'.code.toByte())
            }
        }
        route("/packs") {
            get {
                val packs = repository.all()
                call.respondBytesWriter(ContentType.Application.Json) {
                    var first = true
                    writeByte('['.code.toByte())
                    packs.map { pack ->
                        pack.copy(
                            sources = PackSources.Empty
                        )
                    }.collect { descriptor ->
                        if (!first) writeByte(','.code.toByte())
                        else first = false
                        writeString(json.encodeToString(descriptor))
                    }
                    writeByte(']'.code.toByte())
                }
            }
            get("/{group}/{id}") {
                val group = call.parameters["group"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val pack = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val id = PackId(group, pack)
                val repository = repository.get(id) ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respondText(ContentType.Application.Json) {
                    json.encodeToString(repository)
                }
            }
        }
        route("/generate") {
            post("/preview") {
                val settings: ProjectDescriptor = call.receive()
                val result: Flow<SourceFileEntry> = generator.generate(settings)
                call.respondBytesWriter(ContentType.Application.Json) {
                    var first = true
                    writeByte('{'.code.toByte())
                    result.collect { (path, contents) ->
                        if (!first) writeByte(','.code.toByte())
                        else first = false
                        writeString("\"$path\":")
                        writeJsonString(contents())
                    }
                    writeByte('}'.code.toByte())
                }
            }
            post("/download") {
                val settings: ProjectDescriptor = call.receive()
                val result: Flow<SourceFileEntry> = generator.generate(settings)
                call.respondProjectDownload(result)
            }
        }
    }
}