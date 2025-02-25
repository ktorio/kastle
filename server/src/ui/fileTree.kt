package org.jetbrains.kastle.ui

import io.ktor.htmx.ExperimentalHtmxApi
import io.ktor.htmx.html.hx
import kotlinx.html.*

fun HTML.fileTreeHtml(fileNames: List<String>) {
    head {
        title("Preview")
    }
    body {
        ul {
            buildTree(fileNames.map { it.split("/") })
        }
    }
}

// Recursively builds the tree structure as UL/LI elements.
@OptIn(ExperimentalHtmxApi::class)
private fun UL.buildTree(paths: List<List<String>>, prefix: List<String> = emptyList()) {
    val grouped = paths
        .filter { it.isNotEmpty() }
        .groupBy { it.first() }

    for ((key, group) in grouped) {
        if (group.all { it.size == 1 }) {
            // If all elements in the group are single length, they represent files
            for (filePath in group) {
                li("preview-file") {
                    val fullPath = prefix + filePath
                    val inputId = "preview-file-${fullPath.joinToString("-")}"
                    input(type = InputType.radio, name = "preview-file") {
                        id = inputId
                        attributes.hx {
                            get = "/preview/file/${fullPath.joinToString("/")}"
                            target = "#preview-panel-contents"
                            trigger = "change"
                        }
                    }
                    label {
                        htmlFor = inputId
                        +filePath.first()
                    }
                }
            }
        } else {
            // Handle folders with nested contents
            li("preview-folder") {
                val inputId = "preview-folder-$key"
                input(type = InputType.checkBox) {
                    id = inputId
                }
                label {
                    htmlFor = inputId
                    +key
                }
                ul {
                    buildTree(group.map { it.drop(1) }, prefix + key)
                }
            }
        }
    }
}