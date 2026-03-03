package org.jetbrains.kastle.server.`intellij-plugins-ui`

import java.io.InputStream

object Resources {
    val stylesheet: String by lazy { readResourceAsString("/style-plugins.css") }
    val script by lazy { readResourceAsString("/js/kastle-server.js") }

    private fun readResourceAsString(resourcePath: String): String {
        val resource = this::class.java.getResourceAsStream(resourcePath)
            ?: throw kotlinx.io.IOException("Resource $resourcePath not found")
        return resource.use(InputStream::readAllBytes).decodeToString()
    }
}