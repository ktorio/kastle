package org.jetbrains.kastle.server

import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.*
import kotlinx.html.FlowContent
import kotlinx.html.body
import kotlinx.html.ul
import org.jetbrains.kastle.*
import org.jetbrains.kastle.analytics.AnalyticsRepository
import org.jetbrains.kastle.analytics.NoOpAnalyticsRepository
import org.jetbrains.kastle.server.ui.*

val DEFAULT_PROJECT = ProjectDescriptor(
    name = "project",
    group = "com.example",
)

fun Routing.frontEnd(
    repository: PackRepository,
    generator: ProjectGenerator,
    analyticsRepository: AnalyticsRepository = NoOpAnalyticsRepository,
    basePath: String = "",
) {
    // main page
    get {
        initClientIdCookie()
        val project = call.tryReadProjectDescriptor()
        val view = call.readViewState()
        val packs = repository.readAll()
            .toList()
            .sortedBy { it.name }
        // Currently showing file in the preview
        val previewContents: FlowContent.() -> Unit = view.selectedFile?.let { filePath ->
            generator.generate(project ?: DEFAULT_PROJECT)
                .filter { it.path == filePath }
                .singleOrNull()
        }?.htmlContent ?: {}

        call.respondHtml {
            indexHtml(basePath, view, packs, project, previewContents)
        }
    }
    get("/packs") {
        val search = call.request.queryParameters["search"]
        val packs = repository.readAll()
            .filter {
                search == null || listOfNotNull(
                    it.id.toString(),
                    it.name,
                    it.group?.name,
                    it.description,
                ).any { part ->
                    part.contains(search, ignoreCase = true)
                }
            }
            .toList()
            .sortedBy { it.name }

        call.respondHtml {
            body {
                ul {
                    for (pack in packs)
                        packListItem(basePath, pack)
                }
            }
        }
    }
    route("/packs/{group}/{id}") {
        suspend fun RoutingCall.readPack(): PackDescriptor? =
            repository.read(
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
            val selectedFile = call.readViewState().selectedFile
            val files = generator.generate(descriptor)
                .map { it.path }
                .toList()
            call.respondHtml {
                fileTreeHtml(basePath, files, selectedFile)
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
            call.respondProjectDownload(descriptor.name, result)
            call.recordAnalyticsEvent(analyticsRepository, descriptor)
        }
    }
}

private fun RoutingCall.readProjectDescriptor(): ProjectDescriptor {
    val descriptor = tryReadProjectDescriptor()
    requireNotNull(descriptor) { "Project parameters 'name' and 'group' are required" }
    return descriptor
}

private fun RoutingCall.tryReadProjectDescriptor(): ProjectDescriptor? {
    val name = request.queryParameters["name"] ?: return null
    val group = request.queryParameters["group"] ?: return null
    val packaging = request.queryParameters["packaging"]?.let { packaging ->
        PackagingStyle.entries.firstOrNull { it.name.equals(packaging, ignoreCase = true) }
    } ?: PackagingStyle.NESTED

    return ProjectDescriptor(
        name = name,
        group = group,
        properties = request.queryParameters.entries()
            .asSequence()
            .filter { runCatching { VariableId.parse(it.key) }.isSuccess }
            .toVariableEntries()
            .toMap(),
        packs = request.queryParameters.getAll("pack").orEmpty().map(PackId::parse),
        packaging = packaging
    )
}

private fun RoutingCall.readViewState(): View {
    val tab = request.queryParameters["tab"]?.let { tabName ->
        ViewTab.entries.firstOrNull {
            it.name.equals(tabName, ignoreCase = true)
        }
    } ?: return View()
    val pack = request.queryParameters["selectedPack"]?.let(PackId::parse)
    val file = request.queryParameters["selectedFile"]
    return View(tab, pack, file)
}

/**
 * Handles merging object properties.
 */
private fun Sequence<Map.Entry<String, List<String>>>.toVariableEntries(): Sequence<Pair<VariableId, String>> {
    val iter = iterator()
    if (!iter.hasNext()) return emptySequence()
    var map: ObjectVariableBuilder? = null
    return sequence {
        while(iter.hasNext()) {
            val (parameter, parameterValue) = iter.next()
            val variableId = VariableId.parse(parameter)
            val nameAndKey = if (variableId.name.contains('/'))
                variableId.name.split('/', limit = 2)
            else null
            val parentVariableId = nameAndKey?.firstOrNull()?.let {
                VariableId(variableId.packId, it)
            }
            val obj = map
            if (obj != null) {
                if (obj.variableId == parentVariableId) {
                    obj[nameAndKey.last()] = parameterValue
                } else {
                    yield(obj.variableId to obj.entries.joinToString(", ", "{", "}") { (key, value) -> "$key: $value" })
                    map = null
                }
            } else if (nameAndKey != null && parentVariableId != null) {
                map = ObjectVariableBuilder(parentVariableId, mutableMapOf(nameAndKey[1] to parameterValue))
            } else {
                yield(variableId to parameterValue.joinToString())
            }
        }
    }
}

data class ObjectVariableBuilder(
    val variableId: VariableId,
    val properties: MutableMap<String, List<String>>
): MutableMap<String, List<String>> by properties
