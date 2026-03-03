import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.get
import org.w3c.dom.url.URL

/**
 * Build the URL for either project generation or preview, based on inputs found in the form.
 */
internal fun buildProjectGenerationUrl(requestPath: String): String {
    val url = URL(requestPath, window.location.origin)
    val form = document.getElementById("form-panel-contents") as? HTMLElement ?: return url.toString()

    // Process input elements
    val inputs = form.getElementsByTagName("input")
    for (i in 0 until inputs.length) {
        val input = inputs[i] as? HTMLInputElement ?: continue
        val key = removeUpToFirstSlash(input.name)

        when (input.type) {
            "text", "number", "password", "email", "url", "search", "hidden" -> {
                url.searchParams.append(key, input.value)
            }
            "checkbox" -> {
                url.searchParams.append(key, input.checked.toString())
            }
            "radio" -> {
                if (input.checked) {
                    url.searchParams.append(key, input.value)
                }
            }
        }
    }

    // Process select elements
    val selects = form.getElementsByTagName("select")
    for (i in 0 until selects.length) {
        val select = selects[i] as? HTMLSelectElement ?: continue
        val key = removeUpToFirstSlash(select.name)
        url.searchParams.append(key, select.value)
    }

    // Process pack toggles
    val packIds = mutableSetOf<String>()
    val packToggles = document.getElementsByClassName("include-pack-toggle")
    for (i in 0 until packToggles.length) {
        val el = packToggles[i] as? HTMLInputElement ?: continue
        if (el.checked) {
            val packId = el.dataset.get("packId")
            if (packId != null) {
                packIds.add(packId)
            }
        }
    }
    packIds.forEach { id -> url.searchParams.append("pack", id) }

    includeViewState(url)

    return url.toString()
}

private fun includeViewState(url: URL) {
    val selectedTabElement = document.querySelector("input[name=\"main-tabs\"]:checked") as? HTMLInputElement
    if (selectedTabElement != null) {
        val selectedTab = selectedTabElement.dataset.get("tabTitle")
        if (selectedTab != null) {
            url.searchParams.append("tab", selectedTab)
        }
    }

    val selectedPackElement = document.querySelector("input[name=\"selected-pack\"]:checked") as? HTMLInputElement
    if (selectedPackElement != null) {
        val selectedPack = selectedPackElement.value
        url.searchParams.append("selectedPack", selectedPack)
    }

    val selectedFileElement = document.querySelector("input[name=\"preview-file\"]:checked") as? HTMLInputElement
    if (selectedFileElement != null) {
        val selectedFile = selectedFileElement.dataset.get("filePath")
        if (selectedFile != null) {
            url.searchParams.append("selectedFile", selectedFile)
        }
    }
}

private fun removeUpToFirstSlash(str: String): String {
    val indexOfSlash = str.indexOf('/')
    return if (indexOfSlash != -1) {
        str.substring(indexOfSlash + 1) // Remove up to the first '/'
    } else {
        str // Return the original string if no '/' is found
    }
}
