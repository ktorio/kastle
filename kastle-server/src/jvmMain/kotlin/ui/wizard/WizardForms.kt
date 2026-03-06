package org.jetbrains.kastle.server.ui.wizard

import io.ktor.htmx.html.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.html.*

/**
 * Renders the config box (right side of title row) with internal 50/50 split.
 */
@OptIn(ExperimentalKtorApi::class)
fun FlowContent.wizardConfigBox(view: WizardView, oobSwap: Boolean = false) {
    div("wizard-config-box") {
        id = "wizard-config-box"
        if (oobSwap) {
            attributes["hx-swap-oob"] = "true"
        }
        wizardConfigBoxContent(view)
    }
}

/**
 * Inner content of the config box (form fields + download section).
 */
@OptIn(ExperimentalKtorApi::class)
private fun FlowContent.wizardConfigBoxContent(view: WizardView) {
    // Left side - form fields
    div("wizard-config-fields") {
        form {
            id = "wizard-form"
            onSubmit = "event.preventDefault();"

            when (view.pluginType) {
                PluginType.PLUGIN -> pluginConfigFields(view)
                PluginType.THEME -> themeConfigFields(view)
            }
        }
    }

    div("wizard-config-separator") {}

    // Right side - download section
    div("wizard-download-section") {
        button(classes = "wizard-download-btn") {
            id = "wizard-download-button"
            type = ButtonType.button
            onClick = "wizardDownloadProject()"

            downloadIcon()
            span { +"Download" }
        }

        // Generated filename
        div("wizard-download-filename") {
            id = "wizard-download-filename"
            +"${view.artifactId}.zip"
        }

        div("wizard-download-progress") {
            id = "wizard-download-progress"
        }
    }
}

/**
 * Plugin-specific configuration fields.
 */
private fun FORM.pluginConfigFields(view: WizardView) {
    div("wizard-form-group") {
        label("wizard-form-label") {
            htmlFor = "wizard-group-id"
            +"Group ID"
        }
        input(type = InputType.text, classes = "wizard-form-input") {
            id = "wizard-group-id"
            name = "group"
            placeholder = "com.example"
            value = view.groupId
        }
    }

    div("wizard-form-group") {
        label("wizard-form-label") {
            htmlFor = "wizard-artifact-id"
            +"Artifact ID"
        }
        input(type = InputType.text, classes = "wizard-form-input") {
            id = "wizard-artifact-id"
            name = "name"
            placeholder = "awesome-plugin"
            value = view.artifactId
        }
    }

    div("wizard-checkbox-group") {
        input(type = InputType.checkBox) {
            id = "wizard-add-sample-code"
            name = "org.jetbrains.intellij.platform/plugin/addSampleCode"
            checked = view.addSampleCode
        }
        label {
            htmlFor = "wizard-add-sample-code"
            +"Add sample code"
        }
    }
}

/**
 * Theme-specific configuration fields.
 */
private fun FORM.themeConfigFields(view: WizardView) {
    // Hidden group field (required by framework but unused for themes)
    input(type = InputType.hidden) {
        name = "group"
        value = "dummy"
    }

    div("wizard-form-group") {
        label("wizard-form-label") {
            htmlFor = "wizard-theme-name"
            +"Theme Name"
        }
        input(type = InputType.text, classes = "wizard-form-input") {
            id = "wizard-theme-name"
            name = "name"
            placeholder = "My Theme"
            value = view.artifactId
        }
    }
}

/**
 * Renders the download icon SVG.
 */
private fun FlowContent.downloadIcon() {
    span {
        unsafe {
            +"""<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="7 10 12 15 17 10"/>
                <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>"""
        }
    }
}

/**
 * Renders a partial config box update (for HTMX).
 */
@OptIn(ExperimentalKtorApi::class)
fun HTML.wizardConfigBoxHtml(view: WizardView) {
    body {
        wizardConfigBox(view)
    }
}

/**
 * Renders all elements that change when plugin type changes (for HTMX OOB swap).
 */
@OptIn(ExperimentalKtorApi::class)
fun HTML.wizardTypeChangeHtml(
    basePath: String,
    view: WizardView,
    packs: List<org.jetbrains.kastle.PackDescriptor>
) {
    body {
        // Primary target: description
        wizardDescriptionContent(view)

        // OOB swap: config box
        wizardConfigBox(view, oobSwap = true)

        // OOB swap: content row
        val filteredPacks = packs.filterForWizard(view.pluginType)
        val showPacks = view.pluginType == PluginType.PLUGIN && filteredPacks.isNotEmpty()
        div("wizard-content-row${if (!showPacks) " full-width" else ""}") {
            id = "wizard-content-row"
            attributes["hx-swap-oob"] = "true"
            wizardPreviewPanel(basePath, view, isFullWidth = !showPacks)
            if (showPacks) {
                wizardPacksPanel(basePath, filteredPacks, view.selectedPacks)
            }
        }
    }
}

/**
 * Renders description content based on plugin type.
 */
@OptIn(ExperimentalKtorApi::class)
fun FlowContent.wizardDescriptionContent(view: WizardView) {
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

