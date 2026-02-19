import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

fun main() {
    // Set up appearance (syntax highlighting based on color scheme)
    setupAppearance()

    // Set up HTMX event handlers
    setupHtmxEvents()

    // Set up keyboard navigation
    setupUsability()

    // Export downloadProject to global scope so it can be called from HTML
    val mainScope = MainScope()
    window.asDynamic().downloadProject = {
        mainScope.launch {
            downloadProject()
        }
    }
}
