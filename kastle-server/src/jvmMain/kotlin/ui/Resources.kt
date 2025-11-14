package org.jetbrains.kastle.server.ui

object Resources {
    val stylesheet: String by lazy { readResourceAsString("/style.css") }
    val script by lazy { readResourceAsString("/js/kastle-server.js") }

    private fun readResourceAsString(resourcePath: String): String {
        val resource = this::class.java.getResourceAsStream("/js/generate.js")
            ?: throw kotlinx.io.IOException("Resource $resourcePath not found")
        return resource.readAllBytes().decodeToString()
    }
}