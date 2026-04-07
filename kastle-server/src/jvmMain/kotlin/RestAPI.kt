package org.jetbrains.kastle.server

import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.encodeToSink
import org.jetbrains.kastle.*
import org.jetbrains.kastle.analytics.AnalyticsRepository
import org.jetbrains.kastle.analytics.NoOpAnalyticsRepository
import org.jetbrains.kastle.utils.requireValidRelativePath

/**
 * Server API called from clients.
 */
fun Routing.backEnd(
    repository: PackRepository,
    generator: ProjectGenerator,
    analyticsRepository: AnalyticsRepository = NoOpAnalyticsRepository,
    json: Json,
) {
    @OptIn(ExperimentalKtorApi::class)
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
        }.describe {
            okResponseSchema<List<String>>("List of IDs")
        }
        /**
         * Get the version catalogs present in the repository.
         */
        get("/catalogs") {
            call.respond(repository.catalogs())
        }
        /**
         * List all groups present in the repository.
         */
        get("/groups") {
            val groups = repository.groups()
            call.respondBytesWriter(ContentType.Application.Json) {
                writeJsonFlow(groups, json)
            }
        }.describe {
            okResponseSchema<List<Group>>("List of groups")
        }
        route("/packs") {
            /**
             * List all packs in the repository.
             */
            get {
                val packs = repository.getAll()
                call.respondBytesWriter(ContentType.Application.Json) {
                    writeJsonFlow(packs, json)
                }
            }
            route("/{group}/{id}") {
                /**
                 * Get details for the provided pack ID.
                 */
                get {
                    val id = readPackId() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val packDescriptor = repository.read(id) ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.respondText(ContentType.Application.Json) {
                        json.encodeToString(packDescriptor)
                    }
                }
            }
        }
        route("/files") {
            /**
             * List all supplementary files in the repository.
             */
            get {
                val files = repository.files()
                call.respondBytesWriter(ContentType.Application.Json) {
                    writeJsonFlow(files, json)
                }
            }.describe {
                okResponseSchema<List<String>>("List of files")
            }
            /**
             * Get the contents of a file.
             */
            get("/{path...}") {
                val path = call.parameters.getAll("path")?.joinToString("/")
                try {
                    requireNotNull(path) { "Path is required" }
                    requireValidRelativePath(path)
                } catch (e: IllegalArgumentException) {
                    return@get call.respondText(
                        text = e.message ?: "Invalid path",
                        status = HttpStatusCode.BadRequest
                    )
                }
                val contents = repository.readFile(path)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                val contentType = ContentType.fromFileExtension(path.substringAfterLast('.')).firstOrNull()
                    ?: ContentType.Application.OctetStream

                call.respondSource(contents, contentType)
            }
        }
        route("/generate") {
            /**
             * Generate a JSON preview of the project using a map of path-content pairs.
             */
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
            }.describe {
                okResponseSchema<Map<String, String>>("Map of file paths to file contents")
            }
            /**
             * Generate a ZIP file containing the project files.
             *
             * Response: 200 application/zip A ZIP file containing the project files.
             */
            post("/download") {
                val settings: ProjectDescriptor = call.receive()
                val result: Flow<SourceFileEntry> = generator.generate(settings)
                call.respondProjectDownload(settings.name, result)
                call.recordAnalyticsEvent(analyticsRepository, settings)
            }
        }
    }.also { apiRoute ->
        swaggerUI("/docs") {
            info = OpenApiInfo(
                "Kastle API",
                "1.0.0",
                "A back-end for generating Kotlin projects"
            )
            source = OpenApiDocSource.Routing(ContentType.Application.Yaml) {
                apiRoute.descendants()
            }
        }
    }
}

context(inference: JsonSchemaInference)
private inline fun <reified E: Any> Operation.Builder.okResponseSchema(description: String) {
    responses {
        HttpStatusCode.OK {
            this.schema = inference.jsonSchema<E>().copy(
                description = description
            )
            this.description = description
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
    val buffer = writeBuffer
    buffer.writeByte('['.code.toByte())
    flow.collectIndexed { i, descriptor ->
        if (i != 0) buffer.writeByte(','.code.toByte())
        json.encodeToSink(descriptor, buffer)
    }
    buffer.writeByte(']'.code.toByte())
}
