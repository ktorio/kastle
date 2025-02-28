package org.jetbrains.kastle.ui

import io.ktor.htmx.ExperimentalHtmxApi
import io.ktor.htmx.html.hx
import kotlinx.html.*
import org.jetbrains.kastle.PackDescriptor

@OptIn(ExperimentalHtmxApi::class)
fun HTML.indexHtml(packs: List<PackDescriptor>) {
    head {
        title = "Kastle"
        style { unsafe { +Resources.css } }
        styleLink("/assets/a11y-light.min.css")
        styleLink("/assets/a11y-dark.min.css")
        link(rel = "stylesheet") { id = "highlight-style" }
        script(src = "/assets/htmx.min.js") {}
        script(src = "/assets/highlight.min.js") {}
        script { unsafe { +Resources.js } }
    }
    body {
        div {
            id = "header"

            h1 {
                +"♜"
            }
            span("secondary small-caps spaced") {
                +"Kotlin All-purpose Sourcecode Templating and Layout Engine"
            }
            // header navigation
        }
        nav {
            id = "packs"

            form {
                id = "pack-search"
                input(type = InputType.text) {
                    id = "pack-search-input"
                    placeholder = "Search"
                }
            }

            ul {
                for (pack in packs)
                    packListItem(pack)
            }
        }
        main {
            collapsibleSection(id = "form-panel", title = "Settings", checked = true) {
                id = "form-panel-contents"

                form {
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
            collapsibleSection(id = "pack-details", title = "Module Details") {
                id = "pack-details-docs"
                attributes.hx {
                    get = "/pack/docs"
                    trigger = "load"
                }

                +"No details available."
            }
            collapsibleSection(id = "preview-panel", title = "Preview") {
                div {
                    id = "preview-panel-controls"
                    button {
                        attributes.hx {
                            get = "/project/listing"
                            target = "#preview-panel-tree"
                            trigger = "click"
                        }
                        +"Refresh"
                    }
                }
                div {
                    id = "preview-panel-tree"
                    attributes.hx {
                        get = "/project/listing"
                        trigger = "load"
                    }
                }
                div {
                    id = "preview-panel-contents"
                }
            }
        }
    }
}

private fun FlowContent.collapsibleSection(id: String, title: String, checked: Boolean = false, contents: DIV.() -> Unit) {
    section("collapsible") {
        this.id = id
        input(type = InputType.checkBox) {
            this.id = "$id-checkbox"
            this.checked = checked
        }
        label {
            htmlFor = "$id-checkbox"
            div {
                +title
            }
            div("icon") {}
        }
        div("contents") {
            contents()
        }
    }
}

@OptIn(ExperimentalHtmxApi::class)
private fun UL.packListItem(pack: PackDescriptor) {
    li {
        // TODO role is tab?
        val inputId = "toggle/${pack.id}"
        input(type = InputType.radio, name = "selected-pack") {
            attributes.hx {
                get = "/pack/${pack.id}/docs"
                target = "#pack-details-docs"
                trigger = "change"
            }
            this.id = inputId
            this.value = pack.id.toString()
        }
        label {
            htmlFor = inputId
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
                get = "/pack/${pack.id}/properties"
                target = "#dynamic-properties"
                trigger = "change"
                swap = "afterend"
            }
        }
    }
}

