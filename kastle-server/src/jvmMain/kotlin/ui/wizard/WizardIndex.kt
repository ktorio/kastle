package org.jetbrains.kastle.server.ui.wizard

import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.html.*
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.server.ui.Resources

/**
 * Renders the main wizard page.
 */
@OptIn(ExperimentalKtorApi::class)
fun HTML.wizardIndexHtml(
    basePath: String,
    view: WizardView = WizardView(),
    packs: List<PackDescriptor> = emptyList(),
) {
    head {
        title { +"IntelliJ Platform Plugin Generator" }
        meta(charset = "UTF-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
        styleLink("$basePath/assets/wizard-style.css")
        styleLink("$basePath/assets/a11y-light.min.css")
        script(src = "$basePath/assets/htmx.min.js") {}
        script(src = "$basePath/assets/highlight.min.js") {}
        script {
            unsafe {
                if (basePath.isNotEmpty()) {
                    +"window.BASE_PATH = '$basePath';\n"
                }
                +Resources.script
            }
        }
    }

    body {
        // Header
        wizardHeader(basePath)

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
                        id = "wizard-description"
                        wizardDescriptionContent(view)
                    }
                }

                // Config box (right) - with internal 50/50 split
                wizardConfigBox(view)
            }

            // Content row
            val filteredPacks = packs.filterForWizard(view.pluginType)
            val showPacks = view.pluginType == PluginType.PLUGIN && filteredPacks.isNotEmpty()

            div("wizard-content-row${if (!showPacks) " full-width" else ""}") {
                id = "wizard-content-row"
                // Preview panel
                wizardPreviewPanel(basePath, view, isFullWidth = !showPacks)

                // Packs panel (only for Plugin type)
                if (showPacks) {
                    wizardPacksPanel(basePath, filteredPacks, view.selectedPacks)
                }
            }
        }

        wizardModalOverlay()

        wizardFooter(basePath)
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
