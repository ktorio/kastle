import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLLinkElement
import org.w3c.dom.get

/**
 * Load the correct syntax highlighting stylesheet based on the user's preference.
 */
internal fun setupAppearance() {
    val darkModeQuery = window.matchMedia("(prefers-color-scheme: dark)")
    val highlightStyle = document.getElementById("highlight-style") as? HTMLLinkElement

    if (highlightStyle != null) {
        val basePath = window["BASE_PATH"] ?: ""
        highlightStyle.href =
            if (darkModeQuery.matches) "$basePath/assets/a11y-dark.min.css"
            else "$basePath/assets/a11y-light.min.css"
    }
}
