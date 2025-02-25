package org.jetbrains.kastle

import io.ktor.htmx.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.io.asSink
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.io.JsonFilePackRepository
import org.jetbrains.kastle.ui.fileContentsHtml
import org.jetbrains.kastle.ui.indexHtml
import org.jetbrains.kastle.ui.packDetailsHtml
import org.jetbrains.kastle.ui.fileTreeHtml
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalHtmxApi::class)
fun Application.endpoints() {
    val root = Path(environment.config.property("repository.dir").getString())
    val repository = JsonFilePackRepository(root)
    val generator = ProjectGenerator.fromRepository(repository)
    val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    routing {
        // front end
        get {
            val packs = repository.all().toList()

            call.respondHtml {
                indexHtml(packs)
            }
        }
        route("/pack/{group}/{id}") {
            get("docs") {
                val pack = repository.get(PackId(
                    call.parameters["group"]!!,
                    call.parameters["id"]!!
                ))
                call.respondHtml {
                    packDetailsHtml(pack)
                }
            }
        }
        route("/preview") {
            get("listing") {
                val descriptor = call.projectDescriptorFromQuery()
                val files = generator.generate(descriptor)
                    .map { it.path }
                    .toList()
                call.respondHtml {
                    fileTreeHtml(files)
                }
            }
            get("file/{path...}") {
                val path = call.pathParameters.getAll("path").orEmpty().joinToString("/")
                val descriptor = call.projectDescriptorFromQuery()
                val fileEntry = generator.generate(descriptor)
                    .filter { it.path == path }
                    .singleOrNull()
                if (fileEntry == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respondHtml {
                        fileContentsHtml(fileEntry.path, fileEntry.content().readText())
                    }
                }
            }
        }

        // files, images, etc.
        staticResources("/assets", "/assets")

        // back end
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
                    val repositories = repository.all()
                    call.respondBytesWriter(ContentType.Application.Json) {
                        var first = true
                        writeByte('['.code.toByte())
                        repositories.map {
                            it.copy(projectSources = ProjectModules.Empty)
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
                    call.respondOutputStream(ContentType.Application.Zip) {
                        ZipOutputStream(this).use { zip ->
                            val outputSink = zip.asSink()
                            result.collect { (path, contents) ->
                                val entry = ZipEntry(path)
                                val source = contents()
                                zip.putNextEntry(entry)
                                outputSink.write(source, source.size)
                                zip.closeEntry()
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun RoutingCall.projectDescriptorFromQuery() =
    ProjectDescriptor(
        name = request.queryParameters["name"]!!,
        group = request.queryParameters["group"]!!,
        properties = emptyMap(),
        packs = request.queryParameters.getAll("pack")!!.map(PackId::parse),
    )
