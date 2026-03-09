package wizard

import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.kastle.utils.generateRandomHash

/**
 * localStorage key for analytics client ID.
 */
private const val CLIENT_ID_STORAGE_KEY = "kastle_client_id"

/**
 * Checks if analytics consent has been given (client ID exists in localStorage).
 */
fun hasAnalyticsConsent(): Boolean {
    return window.localStorage.getItem(CLIENT_ID_STORAGE_KEY) != null
}

/**
 * Gets the analytics client ID from localStorage.
 * Returns null if no client ID exists (user hasn't consented).
 */
fun getAnalyticsClientId(): String? {
    return window.localStorage.getItem(CLIENT_ID_STORAGE_KEY)
}

/**
 * Sets the analytics client ID in localStorage.
 */
fun setAnalyticsClientId() {
    val clientId = generateRandomHash()
    window.localStorage.setItem(CLIENT_ID_STORAGE_KEY, clientId)
    console.log("[Wizard] Analytics consent granted, client ID set")
}

/**
 * Shows the consent popup if consent hasn't been given yet.
 */
fun showConsentPopupIfNeeded() {
    if (hasAnalyticsConsent()) {
        console.log("[Wizard] Analytics consent already granted")
        return
    }

    val popup = document.getElementById("wizard-consent-popup")
    popup?.classList?.add("active")
    console.log("[Wizard] Showing analytics consent popup")
}

/**
 * Handles user accepting analytics consent.
 */
fun acceptAnalyticsConsent() {
    setAnalyticsClientId()
    hideConsentPopup()
}

/**
 * Handles user declining analytics consent.
 */
fun declineAnalyticsConsent() {
    console.log("[Wizard] Analytics consent declined")
    hideConsentPopup()
}

/**
 * Hides the consent popup.
 */
private fun hideConsentPopup() {
    val popup = document.getElementById("wizard-consent-popup")
    popup?.classList?.remove("active")
}

/**
 * Initializes consent-related functions on window object.
 */
fun initConsentHandlers() {
    window.asDynamic().wizardAcceptConsent = {
        acceptAnalyticsConsent()
    }
    window.asDynamic().wizardDeclineConsent = {
        declineAnalyticsConsent()
    }
}
