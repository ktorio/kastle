package org.jetbrains.kastle.ui

import kotlinx.html.*

fun HTML.fileContentsHtml(name: String, contents: String) {
    head {
        title(name)
    }
    body {
        pre {
            code(languageString(name)) {
                +contents
            }
        }
    }
}

private fun languageString(fileName: String): String? =
    when (fileName.substringAfterLast('.')) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "yaml" -> "yaml"
        else -> null
    }?.let { "language-$it" }