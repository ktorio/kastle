package org.jetbrains.kastle.utils

import kotlinx.io.files.Path

fun Path.normalize(): Path {
    val parts = toString().split('/')
    val normalized = mutableListOf<String>()

    for (part in parts) {
        when {
            part.isEmpty() || part == "." -> {
                // Skip empty parts and current directory references
                continue
            }
            part == ".." -> {
                // Go up one level if possible (but don't go above root)
                if (normalized.isNotEmpty() && normalized.last() != "..") {
                    normalized.removeAt(normalized.lastIndex)
                } else if (!toString().startsWith("/")) {
                    // For relative paths, keep the ".." if we can't resolve it
                    normalized.add(part)
                }
            }
            else -> {
                normalized.add(part)
            }
        }
    }

    val result = normalized.joinToString("/")
    return Path(if (toString().startsWith("/") && result.isNotEmpty()) "/$result" else result.ifEmpty { "." })
}