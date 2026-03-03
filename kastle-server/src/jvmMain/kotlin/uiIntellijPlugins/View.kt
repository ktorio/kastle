package org.jetbrains.kastle.server.`intellij-plugins-ui`

import org.jetbrains.kastle.PackId

data class View(
    val selectedPack: PackId? = null,
    val selectedFile: String? = null,
)
