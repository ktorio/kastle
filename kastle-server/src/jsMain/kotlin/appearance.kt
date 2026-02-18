import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLLinkElement

/**
 * Load the correct syntax highlighting stylesheet based on the user's preference.
 */
internal fun setupAppearance() {
    val darkModeQuery = window.matchMedia("(prefers-color-scheme: dark)")
    val highlightStyle = document.getElementById("highlight-style") as? HTMLLinkElement

    if (highlightStyle != null) {
        highlightStyle.href =
            if (darkModeQuery.matches) "assets/a11y-dark.min.css"
            else "assets/a11y-light.min.css"
    }
}
