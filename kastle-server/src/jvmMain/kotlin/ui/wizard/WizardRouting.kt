package org.jetbrains.kastle.server.ui.wizard

import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readText
import kotlinx.coroutines.flow.*
import org.jetbrains.kastle.*
import org.jetbrains.kastle.analytics.AnalyticsRepository
import org.jetbrains.kastle.analytics.NoOpAnalyticsRepository
import org.jetbrains.kastle.server.initClientIdCookie
import org.jetbrains.kastle.server.recordAnalyticsEvent
import org.jetbrains.kastle.server.respondProjectDownload

/**
 * Default project descriptor for wizard when no parameters are provided.
 */
private val WIZARD_DEFAULT_PROJECT = ProjectDescriptor(
    name = "my-plugin",
    group = "com.example",
    packs = listOf(
        PluginType.PLUGIN.basePack,
        WizardDefaults.GIT_PACK
    ),
    packaging = PackagingStyle.FLAT
)

/**
 * Registers wizard routes.
 */
fun Routing.wizardFrontEnd(
    repository: PackRepository,
    generator: ProjectGenerator,
    analyticsRepository: AnalyticsRepository = NoOpAnalyticsRepository,
    basePath: String
) {
    // Main wizard page
    get {
        initClientIdCookie()
        val view = call.readWizardViewState()
        val packs = repository.readAll()
            .toList()
            .sortedBy { it.name }

        call.respondHtml {
            wizardIndexHtml(basePath, view, packs)
        }
    }

    // Pack search (reuses existing pack list but returns wizard-styled cards)
    get("/packs") {
        val search = call.request.queryParameters["search"]
        val pluginType = PluginType.fromString(call.request.queryParameters["pluginType"])

        val packs = repository.readAll()
            .filter { pack ->
                // Filter by search term
                val matchesSearch = search.isNullOrBlank() || listOfNotNull(
                    pack.id.toString(),
                    pack.name,
                    pack.group?.name,
                    pack.description,
                ).any { part ->
                    part.contains(search, ignoreCase = true)
                }

                matchesSearch
            }
            .toList()
            .filterForWizard(pluginType)
            .sortedBy { it.name }

        val selectedPacks = call.request.queryParameters.getAll("selectedPack")
            .orEmpty()
            .mapNotNull { runCatching { PackId.parse(it) }.getOrNull() }
            .toSet()

        call.respondHtml {
            wizardPacksGridHtml(basePath, packs, selectedPacks)
        }
    }

    // Pack modal content
    route("/packs/{group}/{id}") {
        suspend fun RoutingCall.readPack(): PackDescriptor? =
            repository.read(
                PackId(
                    parameters["group"]!!,
                    parameters["id"]!!
                )
            )

        get("/modal") {
            val pack = call.readPack()
            call.respondHtml {
                wizardPackModalHtml(pack)
            }
        }

        // Reuse existing docs endpoint
        get("/docs") {
            val pack = call.readPack()
            call.respondHtml {
                wizardPackModalHtml(pack)
            }
        }
    }

    // Project preview and download
    route("/project") {
        get("/listing") {
            val descriptor = call.readWizardProjectDescriptor()
            val selectedFile = call.request.queryParameters["selectedFile"]
            val files = generator.generate(descriptor)
                .map { it.path }
                .toList()
            call.respondHtml {
                wizardFileTreeHtml(basePath, files, selectedFile)
            }
        }

        get("/file/{path...}") {
            val path = call.pathParameters.getAll("path").orEmpty().joinToString("/")
            val descriptor = call.readWizardProjectDescriptor()
            val fileEntry = generator.generate(descriptor)
                .filter { it.path == path }
                .singleOrNull()
            if (fileEntry == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respondHtml {
                    wizardFileContentsHtml(fileEntry.path, fileEntry.content().readText())
                }
            }
        }

        get("/download") {
            val descriptor = call.readWizardProjectDescriptor()
            val result: Flow<SourceFileEntry> = generator.generate(descriptor)
            call.respondProjectDownload(descriptor.name, result)
            call.recordAnalyticsEvent(analyticsRepository, descriptor)
        }
    }

    // Serve wizard-specific CSS
    get("/assets/wizard-style.css") {
        call.respondText(WizardResources.stylesheet, ContentType.Text.CSS)
    }
}

/**
 * Reads wizard view state from request parameters.
 */
private fun RoutingCall.readWizardViewState(): WizardView {
    val pluginType = PluginType.fromString(request.queryParameters["pluginType"])
    val selectedFile = request.queryParameters["selectedFile"]
    val selectedPacks = request.queryParameters.getAll("selectedPack")
        .orEmpty()
        .map(PackId::parse)
        .toSet()
    val groupId = request.queryParameters["group"] ?: "com.example"
    val artifactId = request.queryParameters["name"] ?: "my-plugin"
    val addSampleCode =
        request.queryParameters["org.jetbrains.intellij.platform/plugin/addSampleCode"]?.toBoolean() ?: true

    return WizardView(
        pluginType = pluginType,
        selectedFile = selectedFile,
        selectedPacks = selectedPacks,
        groupId = groupId,
        artifactId = artifactId,
        addSampleCode = addSampleCode
    )
}

/**
 * Reads project descriptor from wizard request parameters.
 */
private fun RoutingCall.readWizardProjectDescriptor(): ProjectDescriptor {
    val name = request.queryParameters["name"] ?: "my-plugin"
    val group = request.queryParameters["group"] ?: "com.example"

    // Build pack list
    val packs = request.queryParameters.getAll("pack").orEmpty().map(PackId::parse)

    // Build properties map
    val properties = request.queryParameters.entries()
        .asSequence()
        .filter { runCatching { VariableId.parse(it.key) }.isSuccess }
        .map { entry ->
            val variableId = VariableId.parse(entry.key)
            variableId to entry.value.joinToString()
        }
        .toMap()

    return ProjectDescriptor(
        name = name,
        group = group,
        packs = packs.ifEmpty {
            listOf(
                PluginType.PLUGIN.basePack,
                WizardDefaults.GIT_PACK
            )
        },
        properties = properties,
        packaging = PackagingStyle.FLAT
    )
}
