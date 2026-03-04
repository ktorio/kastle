package org.jetbrains.kastle.server.ui.wizard

import kotlinx.html.*

/**
 * Renders the wizard footer with links and copyright.
 */
fun FlowContent.wizardFooter(basePath: String) {
    footer("wizard-footer") {
        div("wizard-footer-content") {
            div("wizard-footer-links") {
                a(href = "https://www.jetbrains.com/company/privacy.html") {
                    target = "_blank"
                    +"Privacy Policy"
                }
                a(href = "https://www.jetbrains.com/legal/docs/terms-of-service/") {
                    target = "_blank"
                    +"Terms of Service"
                }
                a(href = "https://plugins.jetbrains.com/docs/marketplace/about-marketplace.html") {
                    target = "_blank"
                    +"About Marketplace"
                }
                a(href = "https://plugins.jetbrains.com/docs/intellij/welcome.html") {
                    target = "_blank"
                    +"Plugin Development Docs"
                }
                a(href = "https://youtrack.jetbrains.com/newIssue?project=MP") {
                    target = "_blank"
                    +"Report Issue"
                }
            }
            div("wizard-footer-copyright") {
                +"Copyright \u00A9 2000-2025 JetBrains s.r.o."
            }
        }
    }
}
