package org.jetbrains.kastle.server.ui.wizard

import io.ktor.htmx.html.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.html.*
import org.jetbrains.kastle.kotlin.KT_EXTENSION
import org.jetbrains.kastle.kotlin.KT_SCRIPT_EXTENSION

/**
 * Renders the preview panel with file tree and content.
 */
@OptIn(ExperimentalKtorApi::class)
fun FlowContent.wizardPreviewPanel(
    basePath: String,
    view: WizardView = WizardView(),
    isFullWidth: Boolean = false
) {
    div("wizard-preview-panel${if (isFullWidth) " full-width" else ""}") {
        id = "wizard-preview-panel"

        div("wizard-preview-header") {
            +"Project Preview"
        }

        div("wizard-preview-content") {
            div("wizard-file-tree") {
                id = "wizard-file-tree"
                attributes.hx {
                    get = "$basePath/project/listing"
                    // Note: "load" trigger removed - initial load is triggered from JS after handlers are set up
                    trigger = "wizardRefreshPreview from:body"
                }
                // Loading state
                div("wizard-loading") {
                    div("wizard-spinner") {}
                }
            }

            div("wizard-file-contents") {
                id = "wizard-file-contents"
                // Will be populated when a file is selected
                pre {
                    code {
                        +"Select a file to preview its contents"
                    }
                }
            }
        }
    }
}

/**
 * Renders the file tree HTML for the wizard.
 */
@OptIn(ExperimentalKtorApi::class)
fun HTML.wizardFileTreeHtml(basePath: String, fileNames: List<String>, selectedFile: String? = null) {
    body {
        ul {
            wizardBuildTree(basePath, fileNames.map { it.split("/") }, selectedFile = selectedFile)
        }
    }
}

/**
 * Recursively builds the tree structure as UL/LI elements for wizard.
 */
@OptIn(ExperimentalKtorApi::class)
private fun UL.wizardBuildTree(
    basePath: String,
    paths: List<List<String>>,
    prefix: List<String> = emptyList(),
    selectedFile: String? = null
) {
    val grouped = paths
        .filter { it.isNotEmpty() }
        .groupBy { it.first() }
        .entries
        .sortedWith(compareBy(
            // folders first, then files
            { (_, group) -> group.all { it.size == 1 } },
            // then alphabetically
            { (key, _) -> key }
        ))

    for ((key, group) in grouped) {
        if (group.all { it.size == 1 }) {
            // Files (leaf nodes)
            for (filePath in group) {
                li("tree-file") {
                    val fullPath = (prefix + filePath).joinToString("/")
                    val inputId = "wizard-file/$fullPath"
                    val selected = selectedFile == fullPath
                    val updatePreviewTrigger = if (selected) "load, change" else "change"

                    input(type = InputType.radio, name = "wizard-preview-file") {
                        id = inputId
                        checked = selected
                        attributes["data-file-path"] = fullPath
                        attributes.hx {
                            get = "$basePath/project/file/$fullPath"
                            target = "#wizard-file-contents"
                            trigger = updatePreviewTrigger
                        }
                    }
                    label("tree-file") {
                        htmlFor = inputId
                        fileIcon()
                        +filePath.first()
                    }
                }
            }
        } else {
            // Folders
            li("tree-folder") {
                val folderPath = (prefix + key).joinToString("/")
                val inputId = "wizard-folder/$folderPath"
                val parentOfSelected = selectedFile?.startsWith(folderPath) == true

                input(type = InputType.checkBox) {
                    id = inputId
                    checked = parentOfSelected
                }
                label("tree-folder") {
                    htmlFor = inputId
                    folderIcon()
                    +key
                }
                ul("tree-folder-children") {
                    wizardBuildTree(
                        basePath,
                        group.map { it.drop(1) },
                        prefix + key,
                        selectedFile
                    )
                }
            }
        }
    }
}

/**
 * Renders the file contents HTML for the wizard.
 */
fun HTML.wizardFileContentsHtml(fileName: String, contents: String) {
    body {
        pre {
            code(wizardLanguageClass(fileName)) {
                +contents
            }
        }
    }
}

/**
 * Determines the language class for syntax highlighting.
 */
private fun wizardLanguageClass(fileName: String): String? =
    when (val extension = fileName.substringAfterLast('.')) {
        KT_EXTENSION, KT_SCRIPT_EXTENSION -> "kotlin"
        else -> extension
    }.takeIf {
        it !in setOf("jar", "tar", "exe", "")
    }?.let { "language-$it" }

/**
 * Renders a simple folder icon.
 */
private fun FlowContent.folderIcon() {
    span("tree-icon") {
        unsafe {
            +"""<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
            </svg>"""
        }
    }
}

/**
 * Renders a simple file icon.
 */
private fun FlowContent.fileIcon() {
    span("tree-icon") {
        unsafe {
            +"""<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/>
                <polyline points="13 2 13 9 20 9"/>
            </svg>"""
        }
    }
}
