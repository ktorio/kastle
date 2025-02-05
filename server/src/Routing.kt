package org.jetbrains.kastle

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.io.asSink
import kotlinx.io.files.Path
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.io.JsonFileKodRepository
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun Application.routing() {
    val root = Path(environment.config.property("repository.dir").getString())
    val repository = JsonFileKodRepository(root)
    val generator = ProjectGenerator.fromRepository(repository)
    val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    routing {
        route("/api") {
            get("/modules") {
                val repositories = repository.all()
                call.respondBytesWriter(ContentType.Application.Json) {
                    var first = true
                    writeByte('['.code.toByte())
                    repositories.map {
                        it.copy(structure = ProjectStructure.Empty)
                    }.collect { descriptor ->
                        if (!first) writeByte(','.code.toByte())
                        else first = false
                        writeString(json.encodeToString(descriptor))
                    }
                    writeByte(']'.code.toByte())
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