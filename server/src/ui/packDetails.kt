package org.jetbrains.kastle.ui

import kotlinx.html.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jetbrains.kastle.PackDescriptor

fun HTML.packDetailsHtml(pack: PackDescriptor?) {
    head {
        title(pack?.name ?: "Not found")
    }
    body {
        pack?.documentation?.let { documentation ->
            val document = Markdown.parser.parse(documentation)
            unsafe {
                +Markdown.renderer.render(document)
            }
        } ?: p { +"No documentation available." }
    }
}

object Markdown {
    val parser by lazy {
        Parser.builder().build()
    }
    val renderer by lazy {
        HtmlRenderer.builder().build()
    }
}