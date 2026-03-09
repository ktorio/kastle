package wizard

import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.kastle.utils.generateRandomHash

/**
 * Cookie name for analytics client ID.
 */
private const val CLIENT_ID_COOKIE_NAME = "kastle_client_id"

/**
 * localStorage key for analytics client ID.
 */
private const val CLIENT_ID_STORAGE_KEY = "kastle_client_id"

/**
 * Checks if analytics consent has been given (client ID exists in localStorage or cookie).
 */
fun hasAnalyticsConsent(): Boolean {
    // Check localStorage first
    val storedId = window.localStorage.getItem(CLIENT_ID_STORAGE_KEY)
    if (storedId != null) return true

    // Fall back to cookie check
    val cookies = document.cookie
    return cookies.split(";").any { cookie ->
        cookie.trim().startsWith("$CLIENT_ID_COOKIE_NAME=")
    }
}

/**
 * Gets the analytics client ID from localStorage or cookie.
 * Returns null if no client ID exists (user hasn't consented).
 */
fun getAnalyticsClientId(): String? {
    // Check localStorage first
    val storedId = window.localStorage.getItem(CLIENT_ID_STORAGE_KEY)
    if (storedId != null) return storedId

    // Fall back to cookie
    val cookies = document.cookie
    return cookies.split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("$CLIENT_ID_COOKIE_NAME=") }
        ?.substringAfter("=")
}

/**
 * Sets the analytics client ID in both localStorage and cookie with 400-day expiration.
 * localStorage is used for sending headers (since AWS doesn't pass cookies),
 * while the cookie is kept for backward compatibility with local development.
 */
fun setAnalyticsClientIdCookie() {
    val clientId = generateRandomHash()
    val maxAgeSeconds = 400 * 24 * 60 * 60 // 400 days

    // Store in localStorage for header-based tracking
    window.localStorage.setItem(CLIENT_ID_STORAGE_KEY, clientId)

    // Also set cookie for backward compatibility
    document.cookie = "$CLIENT_ID_COOKIE_NAME=$clientId; max-age=$maxAgeSeconds; path=/; SameSite=Lax"

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
    setAnalyticsClientIdCookie()
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
