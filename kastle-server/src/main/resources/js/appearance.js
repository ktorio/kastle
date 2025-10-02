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