package org.jetbrains.kastle.server

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.di.annotations.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.*
import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.PackSources
import org.jetbrains.kastle.ProjectDescriptor
import org.jetbrains.kastle.ProjectGenerator
import org.jetbrains.kastle.io.deleteRecursively
import org.jetbrains.kastle.io.resolve

val projectDescriptorSchema by lazy {
    buildJsonObject {
        putJsonObject("name") {
            put("type", "string")
            put("description", "The name of the project to generate")
            put("pattern", "^[a-zA-Z0-9]+$")
        }
        putJsonObject("group") {
            put("type", "string")
            put("description", "The group name used for package declarations (e.g. com.example)")
            put("pattern", "^[a-zA-Z0-9\\.]+$")
        }
        putJsonObject("packs") {
            put("type", "array")
            putJsonObject("items") {
                put("type", "string")
            }
            put("description", "The list of PAC IDs to include in the project")
        }
        putJsonObject("properties") {
            put("type", "object")
            put("description", "The assigned values of properties for the included PACs")
        }
    }
}

//fun Application.agentAPI(
//    @Property("mcp.implementation") implementation: Implementation,
//    @Property("mcp.options")        options: ServerOptions,
//) {
//    val repository: PackRepository by dependencies
//    val generator: ProjectGenerator by dependencies
//    val json: Json by dependencies
//    val fs = SystemFileSystem
//    val projectDir = Path(SystemTemporaryDirectory, "project")
//
//    routing {
//        mcp("mcp") {
//            Server(implementation, options).apply {
//                addResource(
//                    name = "list-packs",
//                    description = """
//                    Get a list of available "Project Architecture Components" (PACs).
//
//                    Each component provides a unit of functionality for building a project.  When generating a project,
//                    you can choose which PACs to include by referencing their IDs.  When a PAC is included, you must
//                    include its "properties" as referenced by "{PAC id}/{key}" under the "properties" map in the project
//                    descriptor payload.
//                """.trimIndent(),
//                    uri = "data://packs.json",
//                    mimeType = "application/json",
//                ) { request ->
//                    val packListContent = repository.all().map { pack ->
//                        val json = json.encodeToString(pack.copy(
//                            sources = PackSources.Empty,
//                        ))
//                        TextResourceContents(
//                            json,
//                            "data://packs/${pack.id}.json",
//                            "application/json"
//                        )
//                    }
//
//                    ReadResourceResult(packListContent.toList())
//                }
//                addTool(
//                    name = "generate-project",
//                    description = """
//                    Generate a new project with the supplied options.
//
//                    Returns a file listing for all sources, which can be read individually.
//                """.trimIndent(),
//                    inputSchema = Tool.Input(
//                        properties = projectDescriptorSchema
//                    )
//                ) { request ->
//                    // TODO remove deleted resources
//                    fs.deleteRecursively(projectDir)
//                    fs.createDirectories(projectDir)
//
//                    val descriptor: ProjectDescriptor = json.decodeFromJsonElement(request.arguments)
//                    val fileList = generator.generate(descriptor).map { (path, content) ->
//                        val filePath = projectDir.resolve(path)
//                        val uri = "file:$path"
//
//                        // write the new project file
//                        fs.sink(filePath).use { sink -> content().transferTo(sink) }
//
//                        // add the file resource
//                        addResource(uri, "read-${path.replace('/', '-')}", "Read file $path") {
//                            ReadResourceResult(listOf(TextResourceContents(
//                                text = fs.source(filePath).buffered().readText(),
//                                uri = uri,
//                                mimeType = "text/plain" // TODO mimetype
//                            )))
//                        }
//
//                        TextContent(uri)
//                    }.toList()
//
//                    // let the client know resources are changed
//                    sendResourceListChanged()
//
//                    CallToolResult(content = fileList)
//                }
//            }
//        }
//    }
//}