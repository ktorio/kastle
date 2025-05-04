package org.jetbrains.kastle.server.ui

import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

object Markdown {
    val parser: Parser by lazy {
        Parser.builder().build()
    }
    val renderer: HtmlRenderer by lazy {
        HtmlRenderer.builder().build()
    }
}