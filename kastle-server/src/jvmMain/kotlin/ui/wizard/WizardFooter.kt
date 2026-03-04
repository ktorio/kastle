package org.jetbrains.kastle.server.ui.wizard

import kotlinx.html.*

/**
 * Renders the wizard footer with social links, legal links, and copyright.
 */
fun FlowContent.wizardFooter(basePath: String) {
    footer("wizard-footer") {
        div("wizard-footer-content") {
            // Top row: Social icons on left, JetBrains logo on right
            div("wizard-footer-top") {
                div("wizard-footer-social") {
                socialIconLink(
                    href = "https://twitter.com/JBPlatform",
                    title = "JetBrains Marketplace on X (Twitter)",
                    iconPath = "M17.09 4h2.715l-5.93 6.777L20.851 20H15.39l-4.278-5.593L6.216 20H3.5l6.342-7.25L3.15 4h5.601l3.867 5.113L17.091 4zm-.952 14.375h1.504L7.934 5.54H6.32l9.818 12.836z"
                )
                socialIconLink(
                    href = "https://bsky.app/profile/platform.jetbrains.com",
                    title = "JetBrains Marketplace on Bluesky",
                    iconPath = "M6.90178 5.0702C8.96531 6.62467 11.1852 9.77608 12 11.4672V15.934C12 15.839 11.9634 15.9464 11.8847 16.1779C11.4594 17.4311 9.79837 22.3224 6.00009 18.4121C4.00012 16.3534 4.926 14.2946 8.5665 13.6731C6.48384 14.0286 4.14244 13.441 3.50006 11.1376C3.315 10.475 3 6.39348 3 5.84223C3 3.08092 5.41284 3.94886 6.90178 5.0702ZM17.0982 5.0702C15.0347 6.62467 12.8148 9.77608 12 11.4672V15.934C12 15.839 12.0366 15.9464 12.1153 16.1779C12.5406 17.4311 14.2016 22.3224 17.9999 18.4121C19.9999 16.3534 19.074 14.2946 15.4335 13.6731C17.5162 14.0286 19.8576 13.441 20.4999 11.1376C20.685 10.475 21 6.39348 21 5.84223C21 3.08092 18.5874 3.94886 17.0982 5.0702Z"
                )
                socialIconLink(
                    href = "https://blog.jetbrains.com/platform",
                    title = "JetBrains Marketplace blog",
                    iconPath = "M13.999 5a2 2 0 11-4 0 2 2 0 014 0zm4.348 3h2.656l.033 9.967h-2.988A6.624 6.624 0 0012.02 22a6.913 6.913 0 00-6.25-4H3.036V8H5.77a6.912 6.912 0 016.25 4 7.025 7.025 0 016.327-4z"
                )
                socialIconLink(
                    href = "https://youtrack.jetbrains.com/issues/MP",
                    title = "JetBrains Marketplace Issue Tracker",
                    iconPath = "M5 5h5a2.003 2.003 0 00-2-2H5v2zm9 0h5V3h-3a2.003 2.003 0 00-2 2zM6 15v6H4v-4a2.003 2.003 0 012-2zm14 2a2.002 2.002 0 00-2-2v6h2v-4zM7 10H4V8h3.426a4.986 4.986 0 019.147 0H20v2h-3v4a5 5 0 01-10 0v-4z"
                )
                a(href = "https://platform.jetbrains.com/", classes = "wizard-footer-social-link") {
                    target = "_blank"
                    title = "JetBrains Marketplace Community"
                    unsafe {
                        +"""<svg viewBox="-6.24 -6.24 36.48 36.48" class="wizard-footer-social-icon">
                            <path d="M12.103 0C18.666 0 24 5.485 24 11.997c0 6.51-5.33 11.99-11.9 11.99L0 24V11.79C0 5.28 5.532 0 12.103 0zm.116 4.563a7.395 7.395 0 0 0-6.337 3.57 7.247 7.247 0 0 0-.148 7.22L4.4 19.61l4.794-1.074a7.424 7.424 0 0 0 8.136-1.39 7.256 7.256 0 0 0 1.737-7.997 7.375 7.375 0 0 0-6.84-4.585h-.008z"></path>
                        </svg>"""
                    }
                }
                socialIconLink(
                    href = "https://www.youtube.com/playlist?list=PLQ176FUIyIUZRWGCFY7G9V5zaM00THymY",
                    title = "JetBrains Marketplace on YouTube",
                    iconPath = "M3.917 17.765a2.94 2.94 0 001.98.82c1.437.146 6.107.191 6.107.191s3.775-.006 6.289-.199a2.486 2.486 0 001.799-.812c.386-.568.63-1.22.714-1.901.112-1.03.172-2.065.18-3.101v-1.454a30.817 30.817 0 00-.18-3.1 4.32 4.32 0 00-.714-1.903 2.473 2.473 0 00-1.8-.81c-2.513-.195-6.284-.195-6.284-.195H12s-3.77 0-6.284.195a2.476 2.476 0 00-1.799.81 4.318 4.318 0 00-.714 1.903 30.782 30.782 0 00-.18 3.1v1.454c.008 1.036.068 2.07.18 3.1a4.31 4.31 0 00.714 1.902zM9.761 8.67l5.615 3.369-5.615 3.369V8.67z"
                )
                socialIconLink(
                    href = "https://www.linkedin.com/showcase/jetbrains-marketplace",
                    title = "JetBrains Marketplace on LinkedIn",
                    iconPath = "M4.84 4h14.487a1.241 1.241 0 011.258 1.228v14.544A1.24 1.24 0 0119.327 21H4.84a1.24 1.24 0 01-1.255-1.228V5.228A1.238 1.238 0 014.84 4zm1.264 14.488h2.524v-8.113H6.104v8.113zM7.367 9.26a1.46 1.46 0 10-1.351-.898 1.442 1.442 0 001.351.898zm8.184 9.227h2.521v-4.449c0-2.19-.472-3.862-3.025-3.862a2.644 2.644 0 00-2.385 1.303h-.035v-1.105H10.21v8.113h2.518v-4.014c0-1.058.2-2.087 1.512-2.087 1.294 0 1.311 1.208 1.311 2.153v3.948z"
                )
                }
                div("wizard-footer-logo") {
                    a(href = "https://www.jetbrains.com") {
                        target = "_blank"
                        img(src = "$basePath/assets/intellij-plugins/jetbrains-simple.svg", alt = "JetBrains logo") {
                            width = "60"
                            height = "60"
                        }
                    }
                }
            }

            // Legal links section
            div("wizard-footer-legal") {
                a(href = "mailto:marketplace@jetbrains.com") {
                    target = "_blank"
                    +"Feedback"
                }
                a(href = "https://plugins.jetbrains.com/legal/terms-of-use") {
                    +"Terms of Use"
                }
                a(href = "https://plugins.jetbrains.com/legal") {
                    +"Legal, Privacy and Security"
                }
            }

            // Copyright and Motto row
            div("wizard-footer-bottom") {
                div("wizard-footer-copyright") {
                    +"Copyright \u00A9 2000-2026 JetBrains s.r.o."
                }
                div("wizard-footer-motto") {
                    +"Developed with drive and "
                    a(href = "https://jetbrains.com/idea") {
                        target = "_blank"
                        +"IntelliJ IDEA"
                    }
                }
            }
        }
    }
}

private fun FlowContent.socialIconLink(href: String, title: String, iconPath: String) {
    a(href = href, classes = "wizard-footer-social-link") {
        target = "_blank"
        this.title = title
        unsafe {
            +"""<svg viewBox="0 0 24 24" class="wizard-footer-social-icon"><path d="$iconPath"></path></svg>"""
        }
    }
}
