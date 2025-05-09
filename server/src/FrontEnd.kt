package org.jetbrains.kastle.server

import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.*
import org.jetbrains.kastle.*
import org.jetbrains.kastle.server.ui.*

fun Routing.frontEnd(
    repository: PackRepository,
    generator: ProjectGenerator,
) {
    // main page
    get {
        val packs = repository.all()
            .toList()
            .sortedBy { it.name }

        call.respondHtml {
            indexHtml(packs)
        }
    }
    // pack details
    route("/pack/{group}/{id}") {
        suspend fun RoutingCall.readPack(): PackDescriptor? =
            repository.get(
                PackId(
                    parameters["group"]!!,
                    parameters["id"]!!
                )
            )

        get("docs") {
            val pack = call.readPack()
            call.respondHtml {
                packDetailsHtml(pack)
            }
        }
        get("properties") {
            val pack = call.readPack()
            if (pack == null) {
                call.respond(HttpStatusCode.NotFound)
            } else call.respondHtml {
                packPropertiesHtml(pack)
            }
        }
    }
    // project preview and download
    route("/project") {
        get("listing") {
            val descriptor = call.readProjectDescriptor()
            val selectedFile = call.request.queryParameters["selected"]
            val files = generator.generate(descriptor)
                .map { it.path }
                .toList()
            call.respondHtml {
                fileTreeHtml(files, selectedFile)
            }
        }
        get("file/{path...}") {
            val path = call.pathParameters.getAll("path").orEmpty().joinToString("/")
            val descriptor = call.readProjectDescriptor()
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
        get("download") {
            val descriptor = call.readProjectDescriptor()
            val result: Flow<SourceFileEntry> = generator.generate(descriptor)
            call.respondProjectDownload(result)
        }
    }
}


private fun RoutingCall.readProjectDescriptor() =
    ProjectDescriptor(
        name = request.queryParameters["name"]!!,
        group = request.queryParameters["group"]!!,
        properties = request.queryParameters.entries()
            .asSequence()
            .filter { runCatching { VariableId.parse(it.key) }.isSuccess }
            .associate { (key, value) ->
                VariableId.parse(key) to value.first()
            },
        packs = request.queryParameters.getAll("pack").orEmpty().map(PackId::parse),
    )