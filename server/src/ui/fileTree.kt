package org.jetbrains.kastle.ui

import io.ktor.htmx.ExperimentalHtmxApi
import io.ktor.htmx.html.hx
import kotlinx.html.*

fun HTML.fileTreeHtml(fileNames: List<String>, selectedFile: String? = null) {
    body {
        ul {
            buildTree(
                fileNames.map { it.split("/") },
                selectedFile = selectedFile
            )
        }
    }
}

// Recursively builds the tree structure as UL/LI elements.
@OptIn(ExperimentalHtmxApi::class)
private fun UL.buildTree(
    paths: List<List<String>>,
    prefix: List<String> = emptyList(),
    selectedFile: String? = null
) {
    val grouped = paths
        .filter { it.isNotEmpty() }
        .groupBy { it.first() }

    for ((key, group) in grouped) {
        if (group.all { it.size == 1 }) {
            // If all elements in the group are single length, they represent files
            for (filePath in group) {
                li("preview-file") {
                    val fullPath = (prefix + filePath).joinToString("/")
                    val inputId = "preview-file/$fullPath"
                    val selected = selectedFile == fullPath
                    val updatePreviewTrigger = if (selected) "load change" else "change"
                    input(type = InputType.radio, name = "preview-file") {
                        id = inputId
                        checked = selected
                        attributes.hx {
                            get = "/project/file/$fullPath"
                            target = "#preview-panel-contents"
                            trigger = updatePreviewTrigger
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
                val folderPath = (prefix + key).joinToString("/")
                val inputId = "preview-folder/$key"
                val parentOfSelected = selectedFile?.startsWith(folderPath) == true
                input(type = InputType.checkBox) {
                    id = inputId
                    checked = parentOfSelected
                }
                label {
                    htmlFor = inputId
                    +key
                }
                ul {
                    buildTree(
                        group.map { it.drop(1) },
                        prefix + key,
                        selectedFile
                    )
                }
            }
        }
    }
}