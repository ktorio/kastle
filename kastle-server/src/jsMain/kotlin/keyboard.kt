import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLLabelElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/**
 * Handle keyboard navigation for labels with hidden inputs.
 */
internal fun setupUsability() {
    document.addEventListener("keydown", { event ->
        val keyEvent = event as KeyboardEvent
        // Get currently focused element
        val focusedElement = document.activeElement
        
        // Skip if no element is focused or if it's not a label
        if (focusedElement == null || focusedElement.tagName != "LABEL") return@addEventListener
        
        val focusedLabel = focusedElement as HTMLLabelElement
        
        // Check for vertical navigation (up/down arrows)
        if (keyEvent.key == "ArrowUp" || keyEvent.key == "ArrowDown") {
            // Check if the focused element has a vertical arrow group
            val vGroup = focusedLabel.getAttribute("data-vertical-arrow-group")
            if (vGroup != null) {
                handleVerticalNavigation(vGroup, keyEvent.key == "ArrowUp", focusedLabel)
                keyEvent.preventDefault() // Prevent default scrolling behavior
            }
        }
        
        // Check for horizontal navigation (left/right arrows)
        if (keyEvent.key == "ArrowLeft" || keyEvent.key == "ArrowRight") {
            // Check if the focused element has a horizontal arrow group
            val hGroup = focusedLabel.getAttribute("data-horizontal-arrow-group")
            if (hGroup != null) {
                handleHorizontalNavigation(hGroup, keyEvent.key == "ArrowLeft", focusedLabel)
                keyEvent.preventDefault() // Prevent default scrolling behavior
            }
            // collapsible folder nav
            else if (focusedLabel.parentElement?.className?.contains("preview-folder") == true) {
                val forAttr = focusedLabel.getAttribute("for")
                if (forAttr != null) {
                    val input = document.getElementById(forAttr) as? HTMLInputElement
                    if (input != null) {
                        input.checked = keyEvent.key == "ArrowRight"
                        input.dispatchEvent(Event("change"))
                    }
                }
            }
        }
        
        // Toggle checked with Enter/Space to simulate input interaction
        if (keyEvent.key == "Enter" || keyEvent.key == " ") {
            val forAttr = focusedLabel.getAttribute("for")
            if (forAttr != null) {
                val focusedElementInput = document.getElementById(forAttr) as? HTMLInputElement
                if (focusedElementInput != null) {
                    focusedElementInput.checked = !focusedElementInput.checked
                    focusedElementInput.dispatchEvent(Event("change"))
                }
            }
        }
    })
}

private fun handleVerticalNavigation(groupName: String, isUpArrow: Boolean, currentElement: HTMLLabelElement) {
    // Get all elements in the same vertical group
    val groupElements = document.querySelectorAll("[data-vertical-arrow-group=\"$groupName\"]")
    val elementsList = mutableListOf<HTMLLabelElement>()
    
    for (i in 0 until groupElements.length) {
        val el = groupElements.item(i) as? HTMLLabelElement
        if (el != null) {
            elementsList.add(el)
        }
    }
    
    // Sort by vertical position
    elementsList.sortBy { it.getBoundingClientRect().top }
    
    // Find current element index in the group
    val currentIndex = elementsList.indexOf(currentElement)
    if (currentIndex == -1) return
    
    // Calculate the next index based on direction
    val nextIndex = if (isUpArrow) {
        // Go to previous element, or loop to the last element
        if (currentIndex <= 0) elementsList.size - 1 else currentIndex - 1
    } else {
        // Go to next element, or loop to the first element
        if (currentIndex >= elementsList.size - 1) 0 else currentIndex + 1
    }
    
    // Focus on the next element
    val nextElement = elementsList[nextIndex]
    window.setTimeout({
        nextElement.focus()
        nextElement.asDynamic().scrollIntoView(js("{ behavior: 'smooth', block: 'nearest' }"))
    }, 0)
}

private fun handleHorizontalNavigation(groupName: String, isLeftArrow: Boolean, currentElement: HTMLLabelElement) {
    // Get all elements in the same horizontal group
    val groupElements = document.querySelectorAll("[data-horizontal-arrow-group=\"$groupName\"]")
    val elementsList = mutableListOf<HTMLLabelElement>()
    
    for (i in 0 until groupElements.length) {
        val el = groupElements.item(i) as? HTMLLabelElement
        if (el != null) {
            elementsList.add(el)
        }
    }
    
    // Sort by horizontal position
    elementsList.sortBy { it.getBoundingClientRect().left }
    
    // Find current element index in the group
    val currentIndex = elementsList.indexOf(currentElement)
    if (currentIndex == -1) return
    
    // Calculate the next index based on direction
    val nextIndex = if (isLeftArrow) {
        // Go to previous element, or loop to the last element
        if (currentIndex <= 0) elementsList.size - 1 else currentIndex - 1
    } else {
        // Go to next element, or loop to the first element
        if (currentIndex >= elementsList.size - 1) 0 else currentIndex + 1
    }
    
    // Focus on the next element
    val nextElement = elementsList[nextIndex]
    window.setTimeout({
        nextElement.focus()
        nextElement.asDynamic().scrollIntoView(js("{ behavior: 'smooth', block: 'nearest' }"))
    }, 0)
}
