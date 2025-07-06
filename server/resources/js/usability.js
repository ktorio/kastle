


/**
 * Handle keyboard navigation for labels with hidden inputs.
 */
document.addEventListener('keydown', function(event) {
    // Get currently focused element
    const focusedElement = document.activeElement;

    // Skip if no element is focused or if it's the body
    if (!focusedElement || focusedElement.tagName !== 'LABEL') return;

    // Check for vertical navigation (up/down arrows)
    if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
        // Check if the focused element has a vertical arrow group
        const vGroup = focusedElement.getAttribute('data-vertical-arrow-group');
        if (vGroup) {
            handleVerticalNavigation(vGroup, event.key === 'ArrowUp', focusedElement);
            event.preventDefault(); // Prevent default scrolling behavior
        }
    }

    // Check for horizontal navigation (left/right arrows)
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
        // Check if the focused element has a horizontal arrow group
        const hGroup = focusedElement.getAttribute('data-horizontal-arrow-group');
        if (hGroup) {
            handleHorizontalNavigation(hGroup, event.key === 'ArrowLeft', focusedElement);
            event.preventDefault(); // Prevent default scrolling behavior
        }
        // collapsible folder nav
        else if (focusedElement.parentElement.className.includes('preview-folder')) {
            document.getElementById(focusedElement.getAttribute('for')).checked = event.key === 'ArrowRight'
        }
    }

    // Toggle checked with Enter/Space to simulate input interaction
    if (event.key === 'Enter' || event.key === ' ') {
        let focusedElementInput = document.getElementById(focusedElement.getAttribute('for'));
        focusedElementInput.checked = !focusedElementInput.checked;
        focusedElementInput.dispatchEvent(new Event('change'));
    }
});

function handleVerticalNavigation(groupName, isUpArrow, currentElement) {
    // Get all elements in the same vertical group
    const groupElements = Array.from(
        document.querySelectorAll(`[data-vertical-arrow-group="${groupName}"]`)
    ).sort((a, b) => {
        return a.getBoundingClientRect().top - b.getBoundingClientRect().top;
    });

    // Find current element index in the group
    const currentIndex = groupElements.indexOf(currentElement);

    // Calculate the next index based on direction
    let nextIndex;
    if (isUpArrow) {
        // Go to previous element, or loop to the last element
        nextIndex = currentIndex <= 0 ? groupElements.length - 1 : currentIndex - 1;
    } else {
        // Go to next element, or loop to the first element
        nextIndex = currentIndex >= groupElements.length - 1 ? 0 : currentIndex + 1;
    }

    // Focus on the next element
    const nextElement = groupElements[nextIndex]
    setTimeout(() => {
        nextElement.focus();
        nextElement.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    });
}

function handleHorizontalNavigation(groupName, isLeftArrow, currentElement) {
    // Get all elements in the same horizontal group
    const groupElements = Array.from(
        document.querySelectorAll(`[data-horizontal-arrow-group="${groupName}"]`)
    ).sort((a, b) => {
        return a.getBoundingClientRect().left - b.getBoundingClientRect().left;
    });

    // Find current element index in the group
    const currentIndex = groupElements.indexOf(currentElement);

    // Calculate the next index based on direction
    let nextIndex;
    if (isLeftArrow) {
        // Go to previous element, or loop to the last element
        nextIndex = currentIndex <= 0 ? groupElements.length - 1 : currentIndex - 1;
    } else {
        // Go to next element, or loop to the first element
        nextIndex = currentIndex >= groupElements.length - 1 ? 0 : currentIndex + 1;
    }

    // Focus on the next element
    const nextElement = groupElements[nextIndex]
    setTimeout(() => {
        nextElement.focus();
        nextElement.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    });
}