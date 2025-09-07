package org.jetbrains.kastle.server.ui

import io.ktor.htmx.html.*
import io.ktor.utils.io.*
import kotlinx.html.*
import org.jetbrains.kastle.PackDescriptor

@OptIn(ExperimentalKtorApi::class)
fun HTML.indexHtml(packs: List<PackDescriptor>) {
    head {
        title { +"K A S T L E" }
        style { unsafe { +Resources.stylesheet } }
        styleLink("/assets/a11y-light.min.css")
        styleLink("/assets/a11y-dark.min.css")
        link(rel = "stylesheet") { id = "highlight-style" }
        script(src = "/assets/htmx.min.js") {}
        script(src = "/assets/highlight.min.js") {}
        script { unsafe { +Resources.script } }
    }
    body {
        div {
            id = "header"

            h1 {
                +"♜"
            }
            span("secondary small-caps spaced") {
                +"Kotlin Application Sourcecode Templating and Layout Engine"
            }
            // header navigation
        }
        nav {
            id = "packs"

            form {
                id = "pack-search"
                onSubmit = "event.preventDefault();"

                input(type = InputType.text) {
                    id = "pack-search-input"
                    placeholder = "Search"

                    attributes.hx {
                        get = "/packs"
                        trigger = "changed, keyup[key=='Enter']"
                        target = "#packs-list"
                        vals = "js:{search: event.target.value}"
                    }
                }
            }

            ul {
                id = "packs-list"
                for (pack in packs)
                    packListItem(pack)
            }
        }
        main {
            div {
                id = "download-form"
                div { id = "download-button-loader" }
                div { id = "download-button-progress" }
                button {
                    id = "download-button"
                    onClick = "downloadProject()"
                    +"Download ⤓"
                }
            }

            tabList("main-tabs") {
                tab(id = "form-panel", icon = "&#9881;", title = "Settings", checked = true) {
                    id = "form-panel-contents"

                    form {
                        id = "project-form"

                        div("properties") {
                            h3 {
                                +"Project"
                            }
                            div("field") {
                                label {
                                    htmlFor = "group-name"
                                    +"Group"
                                }
                                input(type = InputType.text) {
                                    id = "group-name"
                                    name = "group"
                                    placeholder = "com.example"
                                    value = "com.example"
                                }
                            }
                            div("field") {
                                label {
                                    htmlFor = "project-name"
                                    +"Artifact"
                                }
                                input(type = InputType.text) {
                                    id = "project-name"
                                    name = "name"
                                    placeholder = "generated"
                                    value = "generated"
                                }
                            }
                        }
                        div {
                            id = "dynamic-properties"
                        }
                        div {
                            id = "selected-packs-config"
                        }
                    }
                }
                tab(id = "pack-details", icon = "&#8505;", title = "About") {
                    id = "pack-details-docs"

                    attributes["data-tab"] = "pack-details-tab"
                    attributes.hx {
                        get = "/packs/docs"
                        trigger = "load"
                    }

                    +"No details available."
                }
                tab(id = "preview-panel", icon = "&#9778;", title = "Preview") {
                    div {
                        id = "preview-panel-container"
                        div {
                            id = "preview-panel-tree"

                            attributes.hx {
                                get = "/project/listing"
                                trigger = "refreshPreview, load, change from:#project-form input"
                            }
                        }
                        div {
                            id = "preview-panel-contents"
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalKtorApi::class)
fun UL.packListItem(pack: PackDescriptor) {
    li {
        // TODO role is tab?
        val inputId = "toggle/${pack.id}"
        input(type = InputType.radio, name = "selected-pack") {
            attributes.hx {
                get = "/packs/${pack.id}/docs"
                target = "#pack-details-docs"
                trigger = "change"
            }
            this.id = inputId
            this.value = pack.id.toString()
        }
        verticalNavLabel(inputId, "pack-list") {
            div {
                +pack.name
            }
            pack.description?.let { description ->
                div("secondary") {
                    +description
                }
            }
        }
        input(type = InputType.checkBox, classes = "include-pack-toggle") {
            attributes["data-pack-id"] = pack.id.toString()
            attributes["data-swap-id"] = "properties/${pack.id}"
            attributes.hx {
                get = "/packs/${pack.id}/properties"
                target = "#dynamic-properties"
                trigger = "change, load"
                swap = "afterend"
            }
        }
    }
}

