package org.jetbrains.kastle.server.ui.wizard

import io.ktor.htmx.html.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.html.*
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.PackId

/**
 * Renders the packs panel with search and card grid.
 */
@OptIn(ExperimentalKtorApi::class)
fun FlowContent.wizardPacksPanel(
    basePath: String,
    packs: List<PackDescriptor>,
    selectedPacks: Set<PackId>
) {
    div("wizard-packs-panel") {
        id = "wizard-packs-panel"

        div("wizard-packs-header") {
            h2("wizard-packs-title") {
                +"Features"
            }
            input(type = InputType.text, classes = "wizard-packs-search") {
                id = "wizard-packs-search"
                name = "search"
                placeholder = "Search..."
                attributes.hx {
                    get = "$basePath/packs"
                    trigger = "keyup changed delay:300ms"
                    target = "#wizard-packs-grid"
                    include = "#wizard-plugin-type"
                }
            }
        }

        div("wizard-packs-grid") {
            id = "wizard-packs-grid"
            wizardPacksGrid(basePath, packs, selectedPacks)
        }
    }
}

/**
 * Renders the grid of pack cards.
 */
@OptIn(ExperimentalKtorApi::class)
fun FlowContent.wizardPacksGrid(
    basePath: String,
    packs: List<PackDescriptor>,
    selectedPacks: Set<PackId>
) {
    for (pack in packs) {
        wizardPackCard(basePath, pack, pack.id in selectedPacks)
    }
}

/**
 * Renders a single pack card.
 */
@OptIn(ExperimentalKtorApi::class)
fun FlowContent.wizardPackCard(
    basePath: String,
    pack: PackDescriptor,
    selected: Boolean
) {
    val cardId = "wizard-pack-${pack.id.group}-${pack.id.id}"
    val inputId = "wizard-pack-toggle-${pack.id.group}-${pack.id.id}"

    div("wizard-pack-card${if (selected) " selected" else ""}") {
        id = cardId
        attributes["data-pack-id"] = pack.id.toString()

        // Hidden checkbox for selection state
        input(type = InputType.checkBox, classes = "wizard-pack-checkbox") {
            this.id = inputId
            name = "wizard-pack"
            value = pack.id.toString()
            checked = selected
            attributes["data-pack-id"] = pack.id.toString()
            onChange = "wizardTogglePack(this)"
        }

        label("wizard-pack-label") {
            htmlFor = inputId

            div("wizard-pack-card-header") {
                div("wizard-pack-icon") {
                    packIcon(pack)
                }
                h3("wizard-pack-name") {
                    +pack.name
                }
            }

            pack.description?.let { description ->
                p("wizard-pack-description") {
                    +description
                }
            }
        }

        // Info button to show modal
        button(classes = "wizard-pack-info-btn") {
            type = ButtonType.button
            title = "More info"
            onClick = "wizardShowPackModal('${pack.id}')"
            span {
                unsafe { +"&#8505;" }
            }
        }
    }
}

/**
 * Renders a pack icon (placeholder SVG).
 */
private fun FlowContent.packIcon(pack: PackDescriptor) {
    val iconType = when {
        pack.id.id.contains("kotlin", ignoreCase = true) -> "kotlin"
        pack.id.id.contains("java", ignoreCase = true) -> "java"
        pack.id.id.contains("python", ignoreCase = true) -> "python"
        pack.id.id.contains("javascript", ignoreCase = true) -> "javascript"
        pack.id.id.contains("go", ignoreCase = true) -> "go"
        pack.id.id.contains("rust", ignoreCase = true) -> "rust"
        pack.id.id.contains("ruby", ignoreCase = true) -> "ruby"
        pack.id.id.contains("php", ignoreCase = true) -> "php"
        pack.id.id.contains("database", ignoreCase = true) -> "database"
        pack.id.id.contains("compose", ignoreCase = true) -> "compose"
        pack.id.id.contains("lsp", ignoreCase = true) -> "lsp"
        else -> "plugin"
    }

    span {
        unsafe {
            +when (iconType) {
                "kotlin" -> """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M12 2L2 12l10 10L22 12 12 2z"/><path d="M12 2v10l10 10"/></svg>"""
                "java" -> """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M8 3v18"/><path d="M16 3v18"/><path d="M3 8h18"/><path d="M3 16h18"/></svg>"""
                "database" -> """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/></svg>"""
                "compose" -> """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M9 9h6v6H9z"/></svg>"""
                "lsp" -> """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M4 17l6-6-6-6"/><line x1="12" y1="19" x2="20" y2="19"/></svg>"""
                else -> """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>"""
            }
        }
    }
}

/**
 * Renders the packs grid for HTMX partial update.
 */
fun HTML.wizardPacksGridHtml(
    basePath: String,
    packs: List<PackDescriptor>,
    selectedPacks: Set<PackId>
) {
    body {
        wizardPacksGrid(basePath, packs, selectedPacks)
    }
}
