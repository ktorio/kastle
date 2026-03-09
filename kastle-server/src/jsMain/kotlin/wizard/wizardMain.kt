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
    window.asDynamic().wizardFilterPacks = { query: String ->
        filterPacks(query)
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
 * Filter packs client-side based on search query
 */
private fun filterPacks(query: String) {
    val searchTerm = query.trim().lowercase()
    val packCards = document.querySelectorAll(".wizard-pack-card")
    val groupHeaders = document.querySelectorAll(".wizard-pack-group-header")

    // Track which groups have visible packs
    val visibleGroups = mutableSetOf<String>()

    // Filter pack cards
    for (i in 0 until packCards.length) {
        val card = packCards[i].asDynamic()
        val packId = (card.dataset?.packId as? String) ?: ""
        val packName = (card.dataset?.packName as? String) ?: ""
        val packDescription = (card.dataset?.packDescription as? String) ?: ""
        val packGroup = (card.dataset?.packGroup as? String) ?: ""
        val packGroupId = (card.dataset?.packGroupId as? String) ?: ""

        val matches = searchTerm.isEmpty() ||
            packId.lowercase().contains(searchTerm) ||
            packName.lowercase().contains(searchTerm) ||
            packDescription.lowercase().contains(searchTerm) ||
            packGroup.lowercase().contains(searchTerm)

        if (matches) {
            card.style.display = ""
            if (packGroupId.isNotEmpty()) {
                visibleGroups.add(packGroupId)
            }
        } else {
            card.style.display = "none"
        }
    }

    // Show/hide group headers based on whether they have visible packs
    for (i in 0 until groupHeaders.length) {
        val header = groupHeaders[i].asDynamic()
        val groupId = header.dataset?.groupId as? String
        if (groupId != null) {
            header.style.display = if (groupId in visibleGroups || searchTerm.isEmpty()) "" else "none"
        }
    }
}

/**
 * Show pack details modal (client-side rendering)
 */
private fun showPackModal(packId: String) {
    val overlay = document.getElementById("wizard-modal-overlay")
    val modalBody = document.getElementById("wizard-modal-body")
    val modalTitle = document.getElementById("wizard-modal-title")

    if (overlay == null || modalBody == null || modalTitle == null) return

    // Find the pack card to read data attributes
    val packCard = document.querySelector(".wizard-pack-card[data-pack-id='$packId']")?.asDynamic()
    if (packCard == null) {
        modalTitle.textContent = "Pack Not Found"
        modalBody.innerHTML = "<p>Pack not found.</p>"
        overlay.classList.add("active")
        return
    }

    // Read pack data from attributes
    val packName = (packCard.dataset?.packName as? String) ?: packId
    val packDescription = packCard.dataset?.packDescription as? String
    val linkHome = packCard.dataset?.packLinkHome as? String
    val linkDocs = packCard.dataset?.packLinkDocs as? String
    val linkVcs = packCard.dataset?.packLinkVcs as? String
    val linkGuide = packCard.dataset?.packLinkGuide as? String

    // Update modal title
    modalTitle.textContent = packName

    // Build modal body content
    val contentParts = mutableListOf<String>()

    // Pack description
    contentParts.add("<div class=\"wizard-modal-pack-header\">")
    if (!packDescription.isNullOrBlank()) {
        contentParts.add("<p>$packDescription</p>")
    }
    contentParts.add("</div>")

    // Links section
    val links = mutableListOf<Pair<String, String>>()
    if (!linkHome.isNullOrBlank()) links.add("Homepage" to linkHome)
    if (!linkDocs.isNullOrBlank()) links.add("Documentation" to linkDocs)
    if (!linkVcs.isNullOrBlank()) links.add("Source Code" to linkVcs)
    if (!linkGuide.isNullOrBlank()) links.add("Guide" to linkGuide)

    if (links.isNotEmpty()) {
        contentParts.add("<div class=\"wizard-modal-links\">")
        links.forEachIndexed { index, (label, url) ->
            if (index > 0) {
                contentParts.add("<span class=\"wizard-modal-link-separator\">·</span>")
            }
            contentParts.add("<a href=\"$url\" target=\"_blank\">$label</a>")
        }
        contentParts.add("</div>")
    }

    // Hidden pack ID input
    contentParts.add("<input type=\"hidden\" id=\"wizard-modal-pack-id\" value=\"$packId\">")

    modalBody.innerHTML = contentParts.joinToString("")
    overlay.classList.add("active")
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
