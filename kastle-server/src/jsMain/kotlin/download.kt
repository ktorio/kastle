import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.*
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlin.js.Promise

/**
 * External declaration for JavaScript Uint8Array typed array.
 */
external class Uint8Array(length: Int) {
    val length: Int
    fun set(array: Uint8Array, offset: Int)
}

/**
 * Download button handler with progress tracking.
 */
suspend fun downloadProject() {
    val downloadBtn = document.getElementById("download-button") as? HTMLButtonElement ?: return
    val loader = document.getElementById("download-button-loader") as? HTMLElement ?: return
    val progressDiv = document.getElementById("download-button-progress") as? HTMLElement ?: return

    try {
        // Disable button and show loader
        downloadBtn.disabled = true
        loader.style.display = "inline-block"
        progressDiv.textContent = "Fetching file..."

        // Replace with your API endpoint
        val url = buildProjectGenerationUrl("/project/download")

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
        val chunks = mutableListOf<Uint8Array>()

        // Read the data chunks
        while (true) {
            val readPromise = reader.read() as Promise<dynamic>
            val result = readPromise.await()
            
            if (result.done) break

            val value = result.value as? Uint8Array ?: continue
            chunks.add(value)
            downloadedSize += value.length.toDouble()

            // Update progress
            if (totalSize != null && totalSize > 0) {
                val percentComplete = ((downloadedSize / totalSize) * 100).toFixed(2)
                progressDiv.textContent = "$percentComplete%"
            }
        }

        // Combine all chunks into a single Uint8Array
        val allChunks = Uint8Array(downloadedSize.toInt())
        var position = 0
        for (chunk in chunks) {
            allChunks.set(chunk, position)
            position += chunk.length
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
        progressDiv.textContent = "Downloaded!"

    } catch (error: Throwable) {
        console.error("Download failed:", error)
        progressDiv.textContent = "Failed: ${error.message}"
    } finally {
        // Re-enable button and hide loader
        downloadBtn.disabled = false
        loader.style.display = "none"
    }
}

// Extension function to format numbers like JavaScript's toFixed
private fun Double.toFixed(digits: Int): String {
    return this.asDynamic().toFixed(digits) as String
}