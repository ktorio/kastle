
// Download button
async function downloadProject() {
    const downloadBtn = document.getElementById('download-button');
    const loader = document.getElementById('download-button-loader');
    const progressDiv = document.getElementById('download-button-progress');

    try {
        // Disable button and show loader
        downloadBtn.disabled = true;
        loader.style.display = 'inline-block';
        progressDiv.textContent = 'Fetching file...';

        // Replace with your API endpoint
        const url = buildProjectGenerationUrl("/project/download");

        const response = await fetch(url);

        // Check if the response is ok
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        // Get the total size of the file
        const totalSize = response.headers.get('Content-Length');
        let downloadedSize = 0;

        // Create a reader to read the response stream
        const reader = response.body.getReader();
        const chunks = [];

        // Read the data chunks
        while (true) {
            const {done, value} = await reader.read();

            if (done) break;

            chunks.push(value);
            downloadedSize += value.length;

            // Update progress
            if (totalSize) {
                const percentComplete = ((downloadedSize / totalSize) * 100).toFixed(2);
                progressDiv.textContent = `${percentComplete}%`;
            }
        }

        // Combine all chunks into a single Uint8Array
        const allChunks = new Uint8Array(downloadedSize);
        let position = 0;
        for (const chunk of chunks) {
            allChunks.set(chunk, position);
            position += chunk.length;
        }

        // Create blob from the binary data
        const blob = new Blob([allChunks], {
            type: response.headers.get('Content-Type') || 'application/octet-stream'
        });

        // Get filename from Content-Disposition header or use default
        let filename = 'project.zip';
        const disposition = response.headers.get('Content-Disposition');
        if (disposition && disposition.includes('filename=')) {
            filename = disposition.split('filename=')[1].replace(/["']/g, '');
        }

        // Create download link and trigger download
        const objectURL = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = objectURL;
        link.download = filename;

        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);

        // Clean up
        window.URL.revokeObjectURL(objectURL);
        progressDiv.textContent = 'Downloaded!';

    } catch (error) {
        console.error('Download failed:', error);
        progressDiv.textContent = `Failed: ${error.message}`;
    } finally {
        // Re-enable button and hide loader
        downloadBtn.disabled = false;
        loader.style.display = 'none';
    }
}


function buildProjectGenerationUrl(requestPath) {
    const url = new URL(requestPath, window.location.origin);
    const form = document.getElementById('form-panel-contents');
    for (let input of form.getElementsByTagName('input')) {
        const key = removeUpToFirstSlash(input.name || input.id)
        switch (input.type) {
            case 'text':
            case 'number':
            case 'password':
            case 'email':
            case 'url':
            case 'search':
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

    if (requestPath.endsWith('/listing')) {
        const selectedFileElement = document.querySelector(`input[name="preview-file"]:checked`);
        if (selectedFileElement) {
            const selectedFile = removeUpToFirstSlash(selectedFileElement.id);
            url.searchParams.append('selected', selectedFile);
        }
    }
    return url;
}


function removeUpToFirstSlash(str) {
    const indexOfSlash = str.indexOf('/');
    if (indexOfSlash !== -1) {
        return str.substring(indexOfSlash + 1); // Remove up to the first '/'
    }
    return str; // Return the original string if no '/' is found
}