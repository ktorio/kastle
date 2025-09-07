
/**
 * Custom logic for toggles and preventing duplicate content based on `data-swap-id`.
 */
document.addEventListener('htmx:beforeRequest', (event) => {
    const triggeringElement = event.target;
    const swapId = triggeringElement.dataset.swapId;
    if (swapId) {
        const existingElement = document.getElementById(swapId)
        if (triggeringElement.tagName === 'INPUT' && !triggeringElement.checked) {
            // trigger is turned off, remove the element
            existingElement?.remove()
            event.preventDefault();
            // update the preview
            htmx.trigger('#preview-panel-tree', 'refreshPreview');
        } else if (existingElement) {
            // element already exists, prevent request
            event.preventDefault();
        }
    }
});

/**
 * Populate the preview parameters from the state of the form elements.
 */
document.addEventListener('htmx:configRequest', (event) => {
    let requestPath = event.detail.path;

    // Always populate preview params from project settings
    if (requestPath.startsWith('/project')) {
        const url = buildProjectGenerationUrl(requestPath);
        event.detail.path = url.pathname + url.search;
    }
    // Include current selected pack for docs request on load
    else if (requestPath === '/packs/docs') {
        const packId = document.querySelector('input[name="selected-pack"]:checked')?.value;
        if (packId) {
            event.detail.path = `/packs/${packId}/docs`
        } else {
            // if nothing selected, abort request
            event.preventDefault();
        }
    }
})

/**
 * Handle HTMX swap events for loading documentation:
 * 1. Expand the collapsible section.
 * 2. Highlight the code in the documentation.
 */
document.addEventListener('htmx:afterSwap', (event) => {
    try {
        const tab = event.target.dataset.tab;
        if (tab) {
            let tabInput = document.getElementById(tab);
            if (tabInput) {
                tabInput.checked = true;
            } else {
                console.error(`Failed to find tab input for tab ${tab}`)
            }
        }
    } catch (e) {
        console.error('Failed to select tab')
    }
    // Find any new code blocks inside the swapped content
    event.target.querySelectorAll('pre code').forEach((block) => {
        hljs.highlightElement(block); // Apply syntax highlighting
    });
});

/**
 * Update preview on new form elements.
 */
document.addEventListener('htmx:afterSettle', (event) => {
    try {
        if (event.target.id === 'dynamic-properties' && event.detail.requestConfig?.triggeringEvent?.type === 'change') {
            htmx.trigger('#preview-panel-tree', 'refreshPreview');
        }
    } catch (e) {
        console.error('Failed to refresh preview panel tree')
    }
});