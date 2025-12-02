package org.jetbrains.kastle.server.ui

import java.io.InputStream

object Resources {
    val stylesheet: String by lazy {
        listOf(
            "/css/variables.css",
            "/css/reset.css",
            "/css/global.css",
            "/css/utilities.css",
            "/css/forms.css",
            "/css/layout.css",
            "/css/components.css",
            "/css/tabs.css",
        ).joinToString("\n\n") {
            readResourceAsString(it)
        }
    }
    val script by lazy { readResourceAsString("/js/kastle-server.js") }

    private fun readResourceAsString(resourcePath: String): String {
        val resource = this::class.java.getResourceAsStream(resourcePath)
            ?: throw kotlinx.io.IOException("Resource $resourcePath not found")
        return resource.use(InputStream::readAllBytes).decodeToString()
    }
}