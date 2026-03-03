package org.jetbrains.kastle.server.ui.wizard

import kotlinx.html.*

/**
 * Renders the wizard header with JetBrains logo and navigation.
 */
fun FlowContent.wizardHeader() {
    header("wizard-header") {
        div("wizard-header-left") {
            a(href = "/wizard", classes = "wizard-header-logo") {
                img(
                    src = "https://plugins.jetbrains.com/docs/intellij/images/intellij-platform-icon.svg",
                    alt = "IntelliJ Platform"
                )
                h1("wizard-header-title") {
                    +"Plugin Generator"
                }
            }
        }
        nav("wizard-header-nav") {
            a(href = "https://plugins.jetbrains.com/") {
                target = "_blank"
                +"Plugins"
            }
            a(href = "https://plugins.jetbrains.com/organizations") {
                target = "_blank"
                +"Teams"
            }
            a(href = "https://plugins.jetbrains.com/docs/intellij/welcome.html") {
                target = "_blank"
                +"For Authors"
            }
            a(href = "https://plugins.jetbrains.com/docs/marketplace/about-marketplace.html") {
                target = "_blank"
                +"Knowledge Base"
            }
            a(href = "https://blog.jetbrains.com/platform/") {
                target = "_blank"
                +"Blog"
            }
        }
    }
}
