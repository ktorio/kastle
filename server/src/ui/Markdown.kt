package org.jetbrains.kastle.ui

import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

object Markdown {
    val parser by lazy {
        Parser.builder().build()
    }
    val renderer by lazy {
        HtmlRenderer.builder().build()
    }
}