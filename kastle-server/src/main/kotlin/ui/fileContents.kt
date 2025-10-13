package org.jetbrains.kastle.server.ui

import kotlinx.html.*

fun HTML.fileContentsHtml(name: String, contents: String) {
    body {
        pre {
            code(languageString(name)) {
                +contents
            }
        }
    }
}

private fun languageString(fileName: String): String? =
    when (val extension = fileName.substringAfterLast('.')) {
        "kt", "kts" -> "kotlin"
        else -> extension
    }.takeIf {
        it !in setOf("jar", "tar", "exe", "")
    }?.let { "language-$it" }