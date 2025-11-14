package org.jetbrains.kastle.server.ui

import kotlinx.html.*
import org.jetbrains.kastle.PackDescriptor

fun HTML.packDetailsHtml(pack: PackDescriptor?) {
    body {
        pack?.documentation?.let { documentation ->
            val document = Markdown.parser.parse(documentation)
            unsafe {
                +Markdown.renderer.render(document)
            }
        } ?: p { +"No documentation available." }
    }
}

