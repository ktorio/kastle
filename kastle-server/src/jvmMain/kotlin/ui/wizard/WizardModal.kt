package org.jetbrains.kastle.server.ui.wizard

import kotlinx.html.*
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.server.ui.Markdown

/**
 * Renders the modal overlay container (initially hidden).
 */
fun FlowContent.wizardModalOverlay() {
    div("wizard-modal-overlay") {
        id = "wizard-modal-overlay"
        onClick = "wizardCloseModal(event)"

        div("wizard-modal") {
            id = "wizard-modal"
            onClick = "event.stopPropagation()"

            div("wizard-modal-header") {
                h3("wizard-modal-title") {
                    id = "wizard-modal-title"
                    +"Pack Details"
                }
                button(classes = "wizard-modal-close") {
                    type = ButtonType.button
                    onClick = "wizardCloseModal()"
                    title = "Close"
                    unsafe { +"&times;" }
                }
            }

            div("wizard-modal-body") {
                id = "wizard-modal-body"
                // Content will be loaded via HTMX
            }

            div("wizard-modal-footer") {
                id = "wizard-modal-footer"
                button(classes = "wizard-modal-btn wizard-modal-btn-secondary") {
                    type = ButtonType.button
                    onClick = "wizardCloseModal()"
                    +"Close"
                }
                button(classes = "wizard-modal-btn wizard-modal-btn-primary") {
                    id = "wizard-modal-select-btn"
                    type = ButtonType.button
                    onClick = "wizardSelectPackFromModal()"
                    +"Add to Project"
                }
            }
        }
    }
}

/**
 * Renders pack details content for the modal.
 */
fun FlowContent.wizardPackModalContent(pack: PackDescriptor) {
    // Update modal title via OOB swap
    h3("wizard-modal-title") {
        id = "wizard-modal-title"
        attributes["hx-swap-oob"] = "true"
        +pack.name
    }

    // Pack description
    div("wizard-modal-pack-header") {
        pack.description?.let { description ->
            p { +description }
        }
    }

    // Links section
    pack.links?.let { links ->
        val linkItems = buildList {
            links.home?.let { add("Homepage" to it) }
            links.docs?.let { add("Documentation" to it) }
            links.vcs?.let { add("Source Code" to it) }
            links.guide?.let { add("Guide" to it) }
        }
        if (linkItems.isNotEmpty()) {
            div("wizard-modal-links") {
                linkItems.forEachIndexed { index, (label, url) ->
                    if (index > 0) {
                        span("wizard-modal-link-separator") { +"·" }
                    }
                    a(href = url, target = "_blank") { +label }
                }
            }
        }
    }

    // Hidden data for JS
    input(type = InputType.hidden) {
        id = "wizard-modal-pack-id"
        value = pack.id.toString()
    }
}

/**
 * Renders pack modal content as partial HTML.
 */
fun HTML.wizardPackModalHtml(pack: PackDescriptor?) {
    body {
        if (pack != null) {
            wizardPackModalContent(pack)
        } else {
            // Update title via OOB swap for not found case
            h3("wizard-modal-title") {
                id = "wizard-modal-title"
                attributes["hx-swap-oob"] = "true"
                +"Pack Not Found"
            }
            p { +"Pack not found." }
        }
    }
}
