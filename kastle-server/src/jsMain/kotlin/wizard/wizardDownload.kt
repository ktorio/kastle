package wizard

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.*
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlin.js.Promise

/**
 * Download wizard project with progress tracking.
 */
suspend fun downloadWizardProject() {
    val downloadBtn = document.getElementById("wizard-download-button") as? HTMLButtonElement ?: return
    val progressDiv = document.getElementById("wizard-download-progress") as? HTMLElement ?: return

    try {
        // Disable button and show progress
        downloadBtn.disabled = true
        progressDiv.textContent = "Preparing download..."

        // Build the download URL
        val basePath = getWizardBasePath()
        val url = buildWizardProjectUrl("$basePath/project/download")

        val response = window.fetch(url).await()

        // Check if the response is ok
        if (!response.ok) {
            throw Exception("HTTP error! status: ${response.status}")
        }

        // Get the total size of the file
        val totalSize = response.headers.get("Content-Length")?.toDoubleOrNull()
        var downloadedSize = 0.0

        // Create a reader to read the response stream
        val body = response.body
        val reader = body?.getReader() ?: throw Exception("No response body")
        val chunks = mutableListOf<dynamic>()

        // Read the data chunks
        progressDiv.textContent = "Downloading..."
        while (true) {
            val readPromise = reader.read() as Promise<dynamic>
            val result = readPromise.await()

            if (result.done as Boolean) break

            val value = result.value
            chunks.add(value)
            downloadedSize += (value.length as Int).toDouble()

            // Update progress
            if (totalSize != null && totalSize > 0) {
                val percentComplete = ((downloadedSize / totalSize) * 100).toFixed(0)
                progressDiv.textContent = "Downloading... $percentComplete%"
            }
        }

        // Combine all chunks into a single Uint8Array
        val allChunks = js("new Uint8Array(downloadedSize)")
        var position = 0
        for (chunk in chunks) {
            allChunks.set(chunk, position)
            position += chunk.length as Int
        }

        // Create blob from the binary data
        val blob = Blob(
            arrayOf(allChunks),
            BlobPropertyBag(
                type = response.headers.get("Content-Type") ?: "application/octet-stream"
            )
        )

        // Get filename from Content-Disposition header or use default
        var filename = "project.zip"
        val disposition = response.headers.get("Content-Disposition")
        if (disposition != null && disposition.contains("filename=")) {
            filename = disposition.split("filename=")[1].replace(Regex("[\"']"), "")
        }

        // Create download link and trigger download
        val objectURL = URL.createObjectURL(blob)
        val link = document.createElement("a") as HTMLAnchorElement
        link.href = objectURL
        link.download = filename

        document.body?.appendChild(link)
        link.click()
        document.body?.removeChild(link)

        // Clean up
        URL.revokeObjectURL(objectURL)
        progressDiv.textContent = "Download complete!"

        // Clear message after a delay
        window.setTimeout({
            progressDiv.textContent = ""
        }, 3000)

    } catch (error: Throwable) {
        console.error("Download failed:", error)
        progressDiv.textContent = "Download failed: ${error.message}"
    } finally {
        // Re-enable button
        downloadBtn.disabled = false
    }
}

// Extension function to format numbers like JavaScript's toFixed
private fun Double.toFixed(digits: Int): String {
    return this.asDynamic().toFixed(digits) as String
}
