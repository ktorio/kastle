package org.jetbrains.kastle.ui

import io.ktor.htmx.ExperimentalHtmxApi
import io.ktor.htmx.html.hx
import kotlinx.html.*
import org.jetbrains.kastle.PackDescriptor

fun HTML.indexHtml(packs: List<PackDescriptor>) {
    head {
        title = "Kastle"
        style {
            unsafe {
                +Resources.css
            }
        }
        styleLink("/assets/a11y-light.min.css")
        styleLink("/assets/a11y-dark.min.css")
        link(rel = "stylesheet") {
            id = "highlight-style"
        }
        script(src = "/assets/htmx.min.js") {}
        script(src = "/assets/highlight.min.js") {}
        script {
            unsafe {
                +Resources.js
            }
        }
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
                    label {
                        htmlFor = "artifact-name"
                        +"Artifact"
                    }
                    input(type = InputType.text) {
                        id = "artifact-name"
                        placeholder = "org.jetbrains.kotlin-stdlib"
                    }
                    div {
                        id = "properties"
                    }
                    div {
                        id = "selected-packs"
                    }
                }
            }
            collapsibleSection(id = "pack-details", title = "Module Details") {
                id = "pack-details-docs"

                +"No details available."
            }
            collapsibleSection(id = "preview-panel", title = "Preview") {
                div {
                    id = "preview-panel-tree"
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
        val inputId = "pack-toggle-${pack.id.toString().replace("/", "-")}"
        input(type = InputType.radio, name = "selected-pack") {
            attributes.hx {
                get = "/pack/${pack.id}/docs"
                target = "#pack-details-docs"
                trigger = "change"
            }
            this.id = inputId
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
            attributes["data-id"] = pack.id.toString()
            attributes.hx {
                get = "/preview/listing"
                target = "#preview-panel-tree"
                trigger = "change"
            }
        }
    }
}

object Resources {
    val css: String by lazy {
        this::class.java.getResourceAsStream("/style.css")!!.readAllBytes().decodeToString()
    }
    val js: String by lazy {
        this::class.java.getResourceAsStream("/script.js")!!.readAllBytes().decodeToString()
    }
}