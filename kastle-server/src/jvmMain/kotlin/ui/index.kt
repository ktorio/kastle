package org.jetbrains.kastle.server.ui

import io.ktor.htmx.html.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.html.*
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.ProjectDescriptor
import org.jetbrains.kastle.ProjectGenerator

@OptIn(ExperimentalKtorApi::class)
fun HTML.indexHtml(
    basePath: String = "",
    view: View = View(),
    packs: List<PackDescriptor> = emptyList(),
    project: ProjectDescriptor? = null,
    previewContents: FlowContent.() -> Unit = {},
) {
    head {
        title { +"K A S T L E" }
        style { unsafe { +Resources.stylesheet } }
        styleLink("$basePath/assets/a11y-light.min.css")
        styleLink("$basePath/assets/a11y-dark.min.css")
        link(rel = "stylesheet") { id = "highlight-style" }
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
                        get = "$basePath/packs"
                        trigger = "changed, keyup[key=='Enter']"
                        target = "#packs-list"
                        vals = "js:{search: event.target.value}"
                    }
                }
            }

            ul {
                id = "packs-list"
                for (pack in packs)
                    packListItem(basePath, pack)
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
                tab(
                    id = "form-panel",
                    icon = "&#9881;",
                    title = "Settings",
                    checked = view.tab == ViewTab.SETTINGS
                ) {
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
                            div("field") {
                                label {
                                    htmlFor = "packaging"
                                    +"Packaging"
                                }
                                select {
                                    name = "packaging"
                                    option {
                                        selected = true
                                        +"Flat"
                                    }
                                    option {
                                        +"Nested"
                                    }
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
                tab(
                    id = "pack-details",
                    icon = "&#8505;",
                    title = "About",
                    checked = view.tab == ViewTab.ABOUT,
                ) {
                    id = "pack-details-docs"

                    attributes["data-tab"] = "pack-details-tab"
                    attributes.hx {
                        get = "$basePath/packs/docs"
                        trigger = "load"
                    }

                    +"No details available."
                }
                tab(
                    id = "preview-panel",
                    icon = "&#9778;",
                    title = "Preview",
                    checked = view.tab == ViewTab.PREVIEW,
                ) {
                    div {
                        id = "preview-panel-container"
                        div {
                            id = "preview-panel-tree"
                            attributes.hx {
                                get = "$basePath/project/listing"
                                trigger = "refreshPreview, load, change from:#project-form input"
                            }
                        }
                        div {
                            id = "preview-panel-contents"
                            previewContents()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalKtorApi::class)
fun UL.packListItem(basePath: String, pack: PackDescriptor) {
    li {
        // TODO role is tab?
        val inputId = "toggle/${pack.id}"
        input(type = InputType.radio, name = "selected-pack") {
            attributes.hx {
                get = "$basePath/packs/${pack.id}/docs"
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
                get = "$basePath/packs/${pack.id}/properties"
                target = "#dynamic-properties"
                trigger = "change, load"
                swap = "afterend"
            }
        }
    }
}

