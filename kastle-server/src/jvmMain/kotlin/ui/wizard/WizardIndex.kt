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
    googleTagManagerId: String? = null,
) {
    head {
        title { +"IntelliJ Platform Plugin Generator" }
        meta(charset = "UTF-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
        favicon()
        styleLink("$basePath/assets/wizard-style.css")
        styleLink("$basePath/assets/a11y-light.min.css")
        script(src = "$basePath/assets/htmx.min.js") {}
        script(src = "$basePath/assets/highlight.min.js") {}
        script {
            unsafe {
                if (basePath.isNotEmpty()) {
                    +"window.BASE_PATH = '$basePath';\n"
                }
            }
        }
        script(src = "$basePath/assets/kastle-server.js") {}
    }

    body {
        if (googleTagManagerId != null) {
            noScript {
                unsafe {
                    //language=HTML
                    +"""<iframe src="//www.googletagmanager.com/ns.html?id=$googleTagManagerId" height="0" width="0" style="display:none;visibility:hidden"></iframe>"""
                }
            }
            script {
                unsafe {
                    //language=JavaScript
                    +"""(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0],j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src='//www.googletagmanager.com/gtm.js?id='+i+dl;f.parentNode.insertBefore(j,f);})(window,document,'script','dataLayer','$googleTagManagerId');"""
                }
            }
        }

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

        wizardConsentPopup()

        wizardFooter(basePath)
    }
}

/**
 * Renders the analytics consent popup (initially hidden).
 */
private fun FlowContent.wizardConsentPopup() {
    div("wizard-consent-popup") {
        id = "wizard-consent-popup"

        div("wizard-consent-content") {
            h3("wizard-consent-title") {
                +"Help us improve"
            }
            p("wizard-consent-text") {
                +"We’d like to use analytics to understand how you use this tool and improve it."
                br
                +"This stores a randomly generated, anonymous identifier in your browser to distinguish usage sessions without identifying you."
            }
            div("wizard-consent-buttons") {
                button(classes = "wizard-consent-btn wizard-consent-btn-secondary") {
                    type = ButtonType.button
                    onClick = "wizardDeclineConsent()"
                    +"No thanks"
                }
                button(classes = "wizard-consent-btn wizard-consent-btn-primary") {
                    type = ButtonType.button
                    onClick = "wizardAcceptConsent()"
                    +"Allow analytics"
                }
            }
        }
    }
}

private fun HEAD.favicon() {
    link {
        rel = "shortcut icon"
        href = "https://resources.jetbrains.com/storage/ui/favicons/favicon.ico"
        type = "image/x-icon"
        attributes["sizes"] = "16x16 32x32"
    }
    link {
        rel = "apple-touch-icon"
        attributes["sizes"] = "57x57"
        href = "https://resources.jetbrains.com/storage/ui/favicons/apple-touch-icon-57x57.png"
    }
    link {
        rel = "apple-touch-icon"
        attributes["sizes"] = "60x60"
        href = "https://resources.jetbrains.com/storage/ui/favicons/apple-touch-icon-60x60.png"
    }
    link {
        rel = "apple-touch-icon"
        attributes["sizes"] = "72x72"
        href = "https://resources.jetbrains.com/storage/ui/favicons/apple-touch-icon-72x72.png"
    }
    link {
        rel = "apple-touch-icon"
        attributes["sizes"] = "76x76"
        href = "https://resources.jetbrains.com/storage/ui/favicons/apple-touch-icon-76x76.png"
    }
    link {
        rel = "apple-touch-icon"
        attributes["sizes"] = "114x114"
        href = "https://resources.jetbrains.com/storage/ui/favicons/apple-touch-icon-114x114.png"
    }
    link {
        rel = "apple-touch-icon"
        attributes["sizes"] = "120x120"
        href = "https://resources.jetbrains.com/storage/ui/favicons/apple-touch-icon-120x120.png"
    }
    link {
        rel = "apple-touch-icon"
        attributes["sizes"] = "144x144"
        href = "https://resources.jetbrains.com/storage/ui/favicons/apple-touch-icon-144x144.png"
    }
    link {
        rel = "apple-touch-icon"
        attributes["sizes"] = "152x152"
        href = "https://resources.jetbrains.com/storage/ui/favicons/apple-touch-icon-152x152.png"
    }
    link {
        rel = "apple-touch-icon"
        attributes["sizes"] = "180x180"
        href = "https://resources.jetbrains.com/storage/ui/favicons/apple-touch-icon-180x180.png"
    }
    link {
        rel = "mask-icon"
        href = "https://resources.jetbrains.com/storage/ui/favicons/apple-mask-icon.svg"
        attributes["color"] = "black"
    }
    meta {
        name = "msapplication-TileColor"
        content = "#000000"
    }
    meta {
        name = "msapplication-TileImage"
        content = "https://resources.jetbrains.com/storage/ui/favicons/mstile-144x144.png"
    }
    meta {
        name = "msapplication-square70x70logo"
        content = "https://resources.jetbrains.com/storage/ui/favicons/mstile-70x70.png"
    }
    meta {
        name = "msapplication-square150x150logo"
        content = "https://resources.jetbrains.com/storage/ui/favicons/mstile-150x150.png"
    }
    meta {
        name = "msapplication-wide310x150logo"
        content = "https://resources.jetbrains.com/storage/ui/favicons/mstile-310x150.png"
    }
    meta {
        name = "msapplication-square310x310logo"
        content = "https://resources.jetbrains.com/storage/ui/favicons/mstile-310x310.png"
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
