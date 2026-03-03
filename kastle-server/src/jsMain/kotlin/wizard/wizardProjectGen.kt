package wizard

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.get
import org.w3c.dom.url.URL

/**
 * Build the URL for wizard project generation or preview.
 */
internal fun buildWizardProjectUrl(requestPath: String): String {
    val url = URL(requestPath, window.location.origin)
    val form = document.getElementById("wizard-form") ?: return url.toString()

    // Get plugin type
    val pluginTypeSelect = document.getElementById("wizard-plugin-type") as? HTMLSelectElement
    val pluginType = pluginTypeSelect?.value ?: "PLUGIN"

    // Process form inputs
    val inputs = form.getElementsByTagName("input")
    for (i in 0 until inputs.length) {
        val input = inputs[i] as? HTMLInputElement ?: continue
        val key = input.name

        if (key.isBlank()) continue

        when (input.type) {
            "text", "hidden" -> {
                if (input.value.isNotBlank()) {
                    url.searchParams.append(key, input.value)
                }
            }
            "checkbox" -> {
                url.searchParams.append(key, input.checked.toString())
            }
        }
    }

    // Add packaging style
    url.searchParams.append("packaging", "FLAT")

    // Add base pack based on plugin type
    when (pluginType.uppercase()) {
        "PLUGIN" -> {
            url.searchParams.append("pack", "org.jetbrains.intellij.platform/plugin")
        }
        "THEME" -> {
            url.searchParams.append("pack", "org.jetbrains.intellij.platform/theme")
        }
    }

    // Always add git pack
    url.searchParams.append("pack", "org.jetbrains.intellij.platform.vcs/git")

    // Add selected packs
    val packCheckboxes = document.querySelectorAll("input[name='wizard-pack']:checked")
    for (i in 0 until packCheckboxes.length) {
        val checkbox = packCheckboxes[i] as? HTMLInputElement ?: continue
        val packId = checkbox.dataset["packId"]
        if (packId != null) {
            url.searchParams.append("pack", packId)
        }
    }

    // Return just the path and query string (not the full URL with origin)
    return url.pathname + url.search
}
