package org.jetbrains.kastle.server.ui.wizard

import kotlinx.html.*

/**
 * Renders the modal overlay container (initially hidden).
 * Content is populated client-side by wizardMain.kt.
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
