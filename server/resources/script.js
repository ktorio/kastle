document.onload = function() {
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
        document.getElementById('highlight-style').href = '/assets/a11y-dark.min.css'
    } else {
        document.getElementById('highlight-style').href = '/assets/a11y-light.min.css'
    }
}

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

/**
 * Populate the preview parameters from the state of the form elements.
 */
document.addEventListener('htmx:configRequest', (event) => {
    console.log(event.detail.path);
    if (event.detail.path.startsWith('/preview')) {
        const url = new URL(event.detail.path, window.location.origin);
        url.searchParams.append('group', 'org.test');
        url.searchParams.append('name', 'test-artifact');
        for (const el of document.getElementsByClassName('include-pack-toggle')) {
            if (el.checked) {
                url.searchParams.append('pack', el.dataset.id)
            }
        }
        event.detail.path = url.pathname + url.search;
    }
})