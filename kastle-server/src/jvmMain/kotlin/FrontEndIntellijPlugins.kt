package org.jetbrains.kastle.server

import io.ktor.server.html.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.html.FlowContent
import org.jetbrains.kastle.*
import org.jetbrains.kastle.server.`intellij-plugins-ui`.pluginsIndexHtml
import org.jetbrains.kastle.server.`intellij-plugins-ui`.View
import org.jetbrains.kastle.server.`intellij-plugins-ui`.ViewTab
import org.jetbrains.kastle.server.`intellij-plugins-ui`.htmlContent

val DEFAULT_PLUGIN_PROJECT = ProjectDescriptor(
    group = "com.example",
    name = "my-plugin",
)

fun Routing.frontEndIntellijPlugins(
    repository: PackRepository,
    generator: ProjectGenerator,
    basePath: String = "",
) {
    // main page - plugins
    get("/generator") {
        initClientIdCookie()
        val project = call.tryReadProjectDescriptor()
        val view = call.readViewState()
        val packs = repository.readAll()
            .toList()
            .sortedBy { it.name }
        // Currently showing file in the preview
        val previewContents: FlowContent.() -> Unit = view.selectedFile?.let { filePath ->
            generator.generate(project ?: DEFAULT_PLUGIN_PROJECT)
                .filter { it.path == filePath }
                .singleOrNull()
        }?.htmlContent ?: {}

        call.respondHtml {
            pluginsIndexHtml(basePath, view, packs, previewContents)
        }
    }
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
