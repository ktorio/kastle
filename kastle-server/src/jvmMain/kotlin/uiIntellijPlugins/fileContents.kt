package org.jetbrains.kastle.server.`intellij-plugins-ui`

import io.ktor.utils.io.readText
import kotlinx.html.*
import org.jetbrains.kastle.SourceFileEntry
import org.jetbrains.kastle.kotlin.KT_EXTENSION
import org.jetbrains.kastle.kotlin.KT_SCRIPT_EXTENSION

fun HTML.fileContentsHtml(name: String, contents: String) {
    body {
        fileBodyContentsHtml(name, contents)
    }
}

val SourceFileEntry.htmlContent: FlowContent.() -> Unit get() = {
    fileBodyContentsHtml(path, content().readText())
}

fun FlowContent.fileBodyContentsHtml(name: String, contents: String) {
    pre {
        code(languageString(name)) {
            +contents
        }
    }
}

private fun languageString(fileName: String): String? =
    when (val extension = fileName.substringAfterLast('.')) {
        KT_EXTENSION, KT_SCRIPT_EXTENSION -> "kotlin"
        else -> extension
    }.takeIf {
        it !in setOf("jar", "tar", "exe", "")
    }?.let { "language-$it" }