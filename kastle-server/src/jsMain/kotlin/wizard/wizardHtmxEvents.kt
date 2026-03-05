package wizard

import kotlinx.browser.document
import org.w3c.dom.get
import kotlin.js.json

private fun encodeURIComponent(str: String): String = js("encodeURIComponent(str)") as String

/**
 * Set up HTMX event handlers for wizard functionality.
 */
fun setupWizardHtmxEvents() {
    val basePath = getWizardBasePath()

    // Configure requests to include form data
    document.addEventListener("htmx:configRequest", { event ->
        val detail = event.asDynamic().detail
        val requestPath = detail.path as? String ?: return@addEventListener

        // Extract pathname from full URL if needed
        val pathname = if (requestPath.startsWith("/")) {
            requestPath
        } else {
            // full URL is passed if the page is at domain root, so extract the path
            js("new URL(requestPath).pathname") as String
        }

        console.log("[Wizard] htmx:configRequest - pathname: $pathname, basePath: '$basePath'")

        // Add selected packs to pack search requests
        if (pathname.startsWith("$basePath/packs")) {
            val selectedPacks = js("window.getWizardSelectedPacks ? window.getWizardSelectedPacks() : []")
            val packParams = mutableListOf<String>()
            for (i in 0 until (selectedPacks.length as Int)) {
                val packId = selectedPacks[i] as String
                packParams.add("selectedPack=${encodeURIComponent(packId)}")
            }
            if (packParams.isNotEmpty()) {
                val separator = if (requestPath.contains("?")) "&" else "?"
                detail.path = requestPath + separator + packParams.joinToString("&")
            }
            console.log("[Wizard] Pack search with selections: ${detail.path}")
        }

        // Populate preview params from wizard form
        if (pathname.startsWith("$basePath/project")) {
            val params = detail.parameters

            // Add form values to HTMX parameters
            val form = document.getElementById("wizard-form")
            if (form != null) {
                val inputs = form.asDynamic().getElementsByTagName("input")
                for (i in 0 until (inputs.length as Int)) {
                    val input = inputs[i]
                    val key = input.name as? String ?: continue
                    if (key.isBlank()) continue

                    when (input.type as String) {
                        "text", "hidden" -> {
                            val value = input.value as? String ?: ""
                            if (value.isNotBlank()) {
                                params[key] = value
                            }
                        }
                        "checkbox" -> {
                            params[key] = (input.checked as Boolean).toString()
                        }
                    }
                }
            }

            // Include currently selected file so it stays selected after refresh
            val selectedFileInput = document.querySelector("input[name='wizard-preview-file']:checked")
            val selectedFilePath = selectedFileInput?.asDynamic()?.dataset?.filePath as? String
            if (selectedFilePath != null) {
                params["selectedFile"] = selectedFilePath
            }

            // Add plugin type
            val pluginTypeSelect = document.getElementById("wizard-plugin-type")
            val pluginType = (pluginTypeSelect?.asDynamic()?.value as? String)?.uppercase() ?: "PLUGIN"

            // Add packaging style
            params["packaging"] = "FLAT"

            // Add base pack based on plugin type
            when (pluginType) {
                "PLUGIN" -> params["pack"] = "org.jetbrains.intellij.platform/plugin"
                "THEME" -> params["pack"] = "org.jetbrains.intellij.platform/theme"
            }

            // Note: For multiple packs, we need to handle this differently
            // HTMX parameters don't support multiple values with same key easily
            // So we'll append them to the path instead
            val packCheckboxes = document.querySelectorAll("input[name='wizard-pack']:checked")
            val packParams = mutableListOf<String>()
            packParams.add("pack=org.jetbrains.intellij.platform.vcs/git") // Always add git
            for (i in 0 until packCheckboxes.length) {
                val checkbox = packCheckboxes[i].asDynamic()
                val packId = checkbox.dataset?.packId as? String
                if (packId != null) {
                    packParams.add("pack=${encodeURIComponent(packId)}")
                }
            }

            // Append pack parameters to path since HTMX params don't handle duplicates well
            if (packParams.isNotEmpty()) {
                val separator = if (requestPath.contains("?")) "&" else "?"
                detail.path = requestPath + separator + packParams.joinToString("&")
            }

            console.log("[Wizard] Modified params, path: ${detail.path}")
        }
    })

    // Update filename display when artifact ID changes
    setupFilenameUpdater()

    // Debug: log when request is sent
    document.addEventListener("htmx:beforeRequest", { event ->
        val detail = event.asDynamic().detail
        console.log("[Wizard] htmx:beforeRequest - sending to: ${detail.path}")
    })

    // Debug: log response
    document.addEventListener("htmx:afterRequest", { event ->
        val detail = event.asDynamic().detail
        console.log("[Wizard] htmx:afterRequest - status: ${detail.xhr?.status}, success: ${detail.successful}")
    })

    // Handle content swap for syntax highlighting
    document.addEventListener("htmx:afterSwap", { event ->
        val customEvent = event.asDynamic()
        val target = customEvent.target

        console.log("[Wizard] htmx:afterSwap - target id: ${target.id}")

        // Highlight code blocks in swapped content
        val codeBlocks = target.querySelectorAll("pre code")
        for (i in 0 until (codeBlocks.length as Int)) {
            val block = codeBlocks[i]
            js("hljs.highlightElement(block)")
        }

        // Re-sync selected packs after grid swap
        if (target.id == "wizard-packs-grid") {
            js("window.wizardSyncSelectedPacks && window.wizardSyncSelectedPacks()")
        }

        // Re-setup handlers after plugin type change (OOB swaps description, config box, content row)
        if (target.id == "wizard-config-box") {
            console.log("[Wizard] Config box swapped, re-initializing form handlers")
            setupFilenameUpdater()
        }
        if (target.id == "wizard-content-row") {
            console.log("[Wizard] Content row swapped, syncing packs and refreshing preview")
            js("window.wizardSyncSelectedPacks && window.wizardSyncSelectedPacks()")
            // Use htmx.process to ensure new elements are initialized, then trigger refresh
            js("htmx.process(document.getElementById('wizard-content-row'))")
            js("htmx.trigger(document.body, 'wizardRefreshPreview')")
        }
    })
}

/**
 * Set up keyboard shortcuts for wizard.
 */
fun setupWizardKeyboard() {
    document.addEventListener("keydown", { event ->
        val keyEvent = event.asDynamic()
        val key = keyEvent.key as String

        // Close modal on Escape
        if (key == "Escape") {
            val overlay = document.getElementById("wizard-modal-overlay")
            if (overlay?.classList?.contains("active") == true) {
                overlay.classList.remove("active")
                event.preventDefault()
            }
        }
    })
}

/**
 * Set up listener to update filename display and refresh preview when form fields change.
 */
private fun setupFilenameUpdater() {
    // Listen for input changes on artifact ID field
    val artifactIdInput = document.getElementById("wizard-artifact-id")
    val themeNameInput = document.getElementById("wizard-theme-name")
    val groupIdInput = document.getElementById("wizard-group-id")
    val filenameDiv = document.getElementById("wizard-download-filename")

    // Set up debounced preview refresh using a global JS variable
    js("""
        window._wizardPreviewTimer = null;
        window._wizardSchedulePreviewRefresh = function() {
            console.log('[Wizard] Scheduling preview refresh...');
            if (window._wizardPreviewTimer) {
                clearTimeout(window._wizardPreviewTimer);
            }
            window._wizardPreviewTimer = setTimeout(function() {
                console.log('[Wizard] Triggering wizardRefreshPreview event');
                htmx.trigger(document.body, 'wizardRefreshPreview');
            }, 400);
        };
    """)

    artifactIdInput?.addEventListener("input", { event ->
        val input = event.asDynamic().target
        val value = input.value as String
        val filename = if (value.isNotBlank()) "$value.zip" else "my-plugin.zip"
        filenameDiv?.textContent = filename
        js("window._wizardSchedulePreviewRefresh()")
    })

    themeNameInput?.addEventListener("input", { event ->
        val input = event.asDynamic().target
        val value = input.value as String
        val filename = if (value.isNotBlank()) "$value.zip" else "my-theme.zip"
        filenameDiv?.textContent = filename
        js("window._wizardSchedulePreviewRefresh()")
    })

    groupIdInput?.addEventListener("input", {
        js("window._wizardSchedulePreviewRefresh()")
    })

    // Also refresh preview when "Add sample code" checkbox changes
    val addSampleCodeCheckbox = document.getElementById("wizard-add-sample-code")
    addSampleCodeCheckbox?.addEventListener("change", {
        js("window._wizardSchedulePreviewRefresh()")
    })
}
