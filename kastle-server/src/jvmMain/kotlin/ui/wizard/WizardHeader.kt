package org.jetbrains.kastle.server.ui.wizard

import kotlinx.html.*

/**
 * Renders the wizard header with JetBrains logo and navigation.
 */
fun FlowContent.wizardHeader(basePath: String) {
    header("wizard-header") {
        div("wizard-header-content") {
            div("wizard-header-left") {
                a(href = "$basePath/wizard", classes = "wizard-header-logo") {
                    img(
                        src = "$basePath/assets/intellij-plugins/intellij-platform-icon.svg",
                        alt = "IntelliJ Platform"
                    )
                    h1("wizard-header-title") {
                        +"IntelliJ Platform Plugin Generator"
                    }
                }
            }
            nav("wizard-header-nav") {
                a(href = "https://plugins.jetbrains.com/", target="_blank") { +"Plugins" }
                a(href = "https://plugins.jetbrains.com/search?tags=Theme", target="_blank") { +"Themes" }
                a(href = "https://plugins.jetbrains.com/plugin-ideas", target="_blank") { +"Plugin Ideas" }
                a(href = "https://jb.gg/ipe", target="_blank") { +"IntelliJ Platform Explorer" }
            }
        }
    }
}
