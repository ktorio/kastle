package org.jetbrains.kastle.server.ui.wizard

import io.ktor.htmx.html.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.html.*
import org.jetbrains.kastle.PackDescriptor

/**
 * Renders the main wizard page.
 */
@OptIn(ExperimentalKtorApi::class)
fun HTML.wizardIndexHtml(
    basePath: String = "/wizard",
    view: WizardView = WizardView(),
    packs: List<PackDescriptor> = emptyList(),
) {
    head {
        title { +"IntelliJ Platform Plugin Generator" }
        meta(charset = "UTF-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1.0")

        // Wizard stylesheet - keep at wizard path
        styleLink("$basePath/assets/wizard-style.css")

        // Common assets - use global /assets path
        styleLink("/assets/a11y-light.min.css")
        script(src = "/assets/htmx.min.js") {}
        script(src = "/assets/highlight.min.js") {}

        // Wizard JavaScript - use existing kastle-server.js bundle
        script {
            unsafe {
                +"""
                window.WIZARD_BASE_PATH = '$basePath';
                """
            }
        }
        script(src = "/assets/js/kastle-server.js") {}
    }

    body {
        // Header
        wizardHeader()

        // Main container
        div("wizard-container") {
            // Title row (side by side: title left, config right)
            div("wizard-title-row") {
                // Title section (left)
                div("wizard-title-section") {
                    h1("wizard-title") {
                        +"New IntelliJ Platform "
                        span("wizard-title-dropdown") {
                            select {
                                id = "wizard-plugin-type"
                                name = "pluginType"
                                onChange = "wizardChangePluginType(this.value)"

                                for (type in PluginType.entries) {
                                    option {
                                        value = type.name
                                        selected = type == view.pluginType
                                        +type.displayName
                                    }
                                }
                            }
                        }
                    }
                    div("wizard-description") {
                        when (view.pluginType) {
                            PluginType.PLUGIN -> {
                                p {
                                    +"Create a new IntelliJ Platform plugin that adds a new functionality to the IDE. "
                                    +"See the "
                                    a(
                                        href = "https://plugins.jetbrains.com/docs/intellij/welcome.html",
                                        target = "_blank"
                                    ) { +"Plugin SDK" }
                                    +" documentation for details."
                                }
                            }

                            PluginType.THEME -> {
                                p {
                                    +"Create a new IntelliJ Platform UI theme for customizing the IDE appearance. "
                                    +"See the "
                                    a(
                                        href = "https://plugins.jetbrains.com/docs/intellij/themes-getting-started.html",
                                        target = "_blank"
                                    ) { +"Themes" }
                                    +" documentation for details."
                                }

                            }
                        }
                    }
                }

                // Config box (right) - with internal 50/50 split
                wizardConfigBox(view)
            }

            // Content row
            val filteredPacks = packs.filterForWizard(view.pluginType)
            val showPacks = view.pluginType == PluginType.PLUGIN && filteredPacks.isNotEmpty()

            div("wizard-content-row${if (!showPacks) " full-width" else ""}") {
                // Preview panel
                wizardPreviewPanel(basePath, view, isFullWidth = !showPacks)

                // Packs panel (only for Plugin type)
                if (showPacks) {
                    wizardPacksPanel(basePath, filteredPacks, view.selectedPacks)
                }
            }
        }

        // Modal overlay
        wizardModalOverlay()

        // Footer
        wizardFooter()
    }
}

/**
 * Resource loader for wizard-specific resources.
 */
object WizardResources {
    val stylesheet: String by lazy { readResourceAsString("/wizard-style.css") }

    private fun readResourceAsString(resourcePath: String): String {
        val resource = this::class.java.getResourceAsStream(resourcePath)
            ?: throw kotlinx.io.IOException("Resource $resourcePath not found")
        return resource.use { it.readAllBytes().decodeToString() }
    }
}
