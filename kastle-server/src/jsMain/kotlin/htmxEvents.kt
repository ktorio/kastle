import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLInputElement

/**
 * Set up custom HTMX event handlers for toggles, request configuration, and content swapping.
 */
fun setupHtmxEvents() {
    // Custom logic for toggles and preventing duplicate content based on `data-swap-id`.
    document.addEventListener("htmx:beforeRequest", { event ->
        val customEvent = event.asDynamic()
        val triggeringElement = customEvent.target
        val swapId = triggeringElement.dataset?.swapId as? String

        if (swapId != null) {
            val existingElement = document.getElementById(swapId)
            if (triggeringElement.tagName == "INPUT" && !(triggeringElement.checked as? Boolean ?: false)) {
                // trigger is turned off, remove the element
                existingElement?.remove()
                event.preventDefault()
                // update the preview
                js("htmx.trigger('#preview-panel-tree', 'refreshPreview')")
            } else if (existingElement != null) {
                // element already exists, prevent request
                event.preventDefault()
            }
        }
    })

    // Populate the preview parameters from the state of the form elements.
    document.addEventListener("htmx:configRequest", { event ->
        val customEvent = event.asDynamic()
        val detail = customEvent.detail
        var requestPath = detail.path as String

        // Always populate preview params from project settings
        if (requestPath.startsWith("project")) {
            val url = buildProjectGenerationUrl(requestPath)
            detail.path = url
        }
        // Include current selected pack for docs request on load
        else if (requestPath == "packs/docs") {
            val packId = (document.querySelector("input[name=\"selected-pack\"]:checked") as? HTMLInputElement)?.value
            if (packId != null) {
                detail.path = "packs/$packId/docs"
            } else {
                // if nothing selected, abort request
                event.preventDefault()
            }
        }
    })

    // Handle HTMX swap events for loading documentation:
    // 1. Expand the collapsible section.
    // 2. Highlight the code in the documentation.
    document.addEventListener("htmx:afterSwap", { event ->
        try {
            val customEvent = event.asDynamic()
            val target = customEvent.target
            val tab = target.dataset?.tab as? String

            if (tab != null) {
                val tabInput = document.getElementById(tab) as? HTMLInputElement
                if (tabInput != null) {
                    tabInput.checked = true
                } else {
                    console.error("Failed to find tab input for tab $tab")
                }
            }
        } catch (e: Throwable) {
            console.error("Failed to select tab")
        }

        // Find any new code blocks inside the swapped content
        val customEvent = event.asDynamic()
        val target = customEvent.target
        val codeBlocks = target.querySelectorAll("pre code")
        for (i in 0 until codeBlocks.length) {
            val block = codeBlocks[i]
            js("hljs.highlightElement(block)")
        }
    })

    // After the request succeeds, update the browser URL
    document.addEventListener("htmx:afterOnLoad", { event ->
        // Only update URL for requests that should push state
        val customEvent = event.asDynamic()
        val requestPath = customEvent.detail.pathInfo.requestPath as String
        if (requestPath.startsWith("project")) {
            val url = buildProjectGenerationUrl(window.location.pathname)
            val newUrl = window.location.pathname + url.substringAfter(window.location.pathname)
            window.history.replaceState(null, "", newUrl)
        }
    })

    // Update preview on new form elements.
    document.addEventListener("htmx:afterSettle", { event ->
        try {
            val customEvent = event.asDynamic()
            val target = customEvent.target
            val detail = customEvent.detail

            if (target.id == "dynamic-properties" && detail.requestConfig?.triggeringEvent?.type == "change") {
                js("htmx.trigger('#preview-panel-tree', 'refreshPreview')")
            }
        } catch (e: Throwable) {
            console.error("Failed to refresh preview panel tree")
        }
    })
}
