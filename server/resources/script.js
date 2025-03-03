/**
 * Load the correct syntax highlighting stylesheet based on the user's preference.
 */
document.onload = function() {
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
        document.getElementById('highlight-style').href = '/assets/a11y-dark.min.css'
    } else {
        document.getElementById('highlight-style').href = '/assets/a11y-light.min.css'
    }
}

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
        const url = new URL(requestPath, window.location.origin);
        const form = document.getElementById('form-panel-contents');
        for (let input of form.getElementsByTagName('input')) {
            const key = removeUpToFirstSlash(input.name || input.id)
            switch(input.type) {
                case 'text': case 'number': case 'password': case 'email': case 'url': case 'search':
                    url.searchParams.append(key, input.value);
                    break;
                case 'checkbox':
                    if (input.checked)
                        url.searchParams.append(key, 'true');
                    break;
                case 'radio':
                    if (input.checked)
                        url.searchParams.append(key, input.value);
                    break;

            }
        }
        // TODO select, etc.

        for (const el of document.getElementsByClassName('include-pack-toggle')) {
            if (el.checked) {
                url.searchParams.append('pack', el.dataset.packId);
            }
        }

        const selectedFileElement = document.querySelector(`input[name="preview-file"]:checked`);
        if (selectedFileElement) {
            const selectedFile = removeUpToFirstSlash(selectedFileElement.id);
            url.searchParams.append('selected', selectedFile);
        }

        event.detail.path = url.pathname + url.search;
    }
    // Include current selected pack for docs request on load
    else if (requestPath === '/pack/docs') {
        const packId = document.querySelector('input[name="selected-pack"]:checked')?.value;
        if (packId) {
            event.detail.path = `/pack/${packId}/docs`
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
        if (event.target.parentElement.classList.contains('collapsible')) {
            event.target.parentElement.getElementsByTagName('input')[0].checked = true;
        }
    } catch (e) {
        console.error('Failed to expand collapsible section')
    }
    // Find any new code blocks inside the swapped content
    event.target.querySelectorAll('pre code').forEach((block) => {
        hljs.highlightElement(block); // Apply syntax highlighting
    });
});

function removeUpToFirstSlash(str) {
    const indexOfSlash = str.indexOf('/');
    if (indexOfSlash !== -1) {
        return str.substring(indexOfSlash + 1); // Remove up to the first '/'
    }
    return str; // Return the original string if no '/' is found
}
