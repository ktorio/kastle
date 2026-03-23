package org.jetbrains.kastle.server.ui.wizard

import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.PackId

/**
 * Plugin type selection for the wizard.
 */
enum class PluginType(val displayName: String, val basePack: PackId) {
    PLUGIN("Plugin", PackId("org.jetbrains.intellij.platform", "plugin")),
    THEME("Theme", PackId("org.jetbrains.intellij.platform", "theme"));

    companion object {
        fun fromString(value: String?): PluginType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PLUGIN
    }
}

/**
 * View state for the wizard UI.
 */
data class WizardView(
    val pluginType: PluginType = PluginType.PLUGIN,
    val selectedFile: String? = null,
    val selectedPacks: Set<PackId> = emptySet(),
    val groupId: String = "com.example",
    val artifactId: String = "awesome-plugin",
    val addSampleCode: Boolean = true,
)

/**
 * Pack IDs that should always be included in generated projects.
 */
object WizardDefaults {
    val GIT_PACK = PackId("org.jetbrains.intellij.platform.vcs", "git")
}

/**
 * Extension to check if a pack should be hidden in the wizard.
 */
fun PackDescriptor.isHiddenInWizard(): Boolean {
    return properties.any { it.key == "hiddenInWizard" && it.default?.toString() == "true" }
}

/**
 * Extension to filter packs for the wizard based on plugin type.
 */
fun List<PackDescriptor>.filterForWizard(pluginType: PluginType): List<PackDescriptor> {
    return when (pluginType) {
        PluginType.PLUGIN -> filter { pack -> !pack.isHiddenInWizard() }
        PluginType.THEME -> emptyList() // No additional packs for themes
    }
}
