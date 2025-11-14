package org.jetbrains.kastle.server.ui

import org.jetbrains.kastle.PackId

data class View(
    val tab: ViewTab = ViewTab.SETTINGS,
    val selectedPack: PackId? = null,
    val selectedFile: String? = null,
)

enum class ViewTab {
    SETTINGS,
    ABOUT,
    PREVIEW
}