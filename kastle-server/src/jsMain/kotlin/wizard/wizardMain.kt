package wizard

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.get

private val wizardScope = MainScope()

// Track selected packs globally
private val selectedPacks = mutableSetOf<String>()

/**
 * Sync selected packs from DOM checkboxes
 */
private fun syncSelectedPacks() {
    selectedPacks.clear()
    val checkboxes = document.querySelectorAll("input[name='wizard-pack']:checked")
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes[i].asDynamic()
        val packId = checkbox.dataset?.packId as? String
        if (packId != null) {
            selectedPacks.add(packId)
        }
    }
    console.log("[Wizard] Synced selected packs: ${selectedPacks.toTypedArray()}")
}

/**
 * Initialize wizard functionality - called from main.kt
 */
fun initWizard() {
    // Initialize consent handlers first
    initConsentHandlers()

    // Export functions to global scope
    window.asDynamic().getWizardSelectedPacks = {
        selectedPacks.toTypedArray()
    }
    window.asDynamic().wizardSyncSelectedPacks = {
        syncSelectedPacks()
    }
    window.asDynamic().wizardDownloadProject = {
        wizardScope.launch {
            downloadWizardProject()
        }
    }
    window.asDynamic().wizardChangePluginType = { value: String ->
        changePluginType(value)
    }
    window.asDynamic().wizardTogglePack = { checkbox: dynamic ->
        togglePack(checkbox)
    }
    window.asDynamic().wizardShowPackModal = { packId: String ->
        showPackModal(packId)
    }
    window.asDynamic().wizardCloseModal = { event: dynamic ->
        closeModal(event)
    }
    window.asDynamic().wizardSelectPackFromModal = {
        selectPackFromModal()
    }
    window.asDynamic().wizardRefreshPreview = {
        refreshPreview()
    }

    // Set up DOM-dependent handlers after DOM is ready
    fun setupDomHandlers() {
        setupWizardHtmxEvents()
        setupWizardKeyboard()
        syncSelectedPacks()  // Initialize selected packs from DOM
        // Trigger initial preview load now that handlers are ready
        refreshPreview()
        // Show consent popup if needed
        showConsentPopupIfNeeded()
    }

    // Check if DOM is already loaded
    val readyState = document.asDynamic().readyState as String
    if (readyState == "loading") {
        document.addEventListener("DOMContentLoaded", { setupDomHandlers() })
    } else {
        // DOM already loaded
        setupDomHandlers()
    }
}

/**
 * Get the wizard base path from window global.
 */
internal fun getWizardBasePath(): String =
    window["BASE_PATH"] ?: ""

/**
 * Change plugin type (Plugin vs Theme) using HTMX for partial update
 */
private fun changePluginType(value: String) {
    val basePath = getWizardBasePath()
    val url = "$basePath/type?pluginType=$value"
    val htmx = js("htmx")
    htmx.ajax("GET", url, js("({target: '#wizard-description'})"))
}

/**
 * Toggle pack selection
 */
private fun togglePack(checkbox: dynamic) {
    val card = checkbox.closest(".wizard-pack-card")
    val packId = checkbox.dataset?.packId as? String

    if (checkbox.checked as Boolean) {
        card?.classList?.add("selected")
        if (packId != null) selectedPacks.add(packId)
    } else {
        card?.classList?.remove("selected")
        if (packId != null) selectedPacks.remove(packId)
    }
    // Refresh the preview
    refreshPreview()
}

/**
 * Refresh the preview panel
 */
private fun refreshPreview() {
    js("htmx.trigger(document.body, 'wizardRefreshPreview')")
}

/**
 * Show pack details modal
 */
private fun showPackModal(packId: String) {
    val basePath = getWizardBasePath()
    val overlay = document.getElementById("wizard-modal-overlay")
    val modalBody = document.getElementById("wizard-modal-body")

    if (overlay != null && modalBody != null) {
        overlay.classList.add("active")
        // Load modal content via HTMX - use dynamic to call htmx
        val url = "$basePath/packs/$packId/modal"
        val htmx = js("htmx")
        htmx.ajax("GET", url, js("({target: '#wizard-modal-body'})"))
    }
}

/**
 * Close the modal
 */
private fun closeModal(event: dynamic) {
    // If event is from overlay click, only close if target is the overlay itself
    val eventDefined = event != null && event !== js("undefined")
    if (eventDefined) {
        val target = event.target
        val overlay = document.getElementById("wizard-modal-overlay")
        if (target != overlay) return
    }

    val overlay = document.getElementById("wizard-modal-overlay")
    overlay?.classList?.remove("active")
}

/**
 * Select pack from modal
 */
private fun selectPackFromModal() {
    val packIdInput = document.getElementById("wizard-modal-pack-id")
    val packId = packIdInput?.asDynamic()?.value as? String

    if (packId != null) {
        // Find and check the corresponding checkbox
        val checkbox = document.querySelector("input[data-pack-id='$packId']")?.asDynamic()
        if (checkbox != null) {
            val isChecked = checkbox.checked as? Boolean ?: false
            if (!isChecked) {
                checkbox.checked = true
                togglePack(checkbox)
            }
        }
    }

    closeModal(null)
}
