package org.jetbrains.kastle.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.PackId
import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.ProjectDescriptor
import org.jetbrains.kastle.ProjectGenerator
import org.jetbrains.kastle.ProjectModules
import org.jetbrains.kastle.SourceFileEntry

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
                            modules = ProjectModules.Empty,
                            commonSources = emptyList(),
                            rootSources = emptyList()
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