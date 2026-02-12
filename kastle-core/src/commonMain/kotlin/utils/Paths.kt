package org.jetbrains.kastle.utils

import kotlinx.io.files.Path

/**
 * Normalizes a path by removing "." and ".." segments.
 */
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

/**
 * Validates and normalizes a user-provided *relative* path for use in APIs/repositories.
 *
 * What it enforces:
 * - not blank
 * - no NUL, no backslash, no CR/LF
 * - no empty segments (so no `//`, no leading/trailing `/` after normalization)
 * - no "." or ".." segments (prevents traversal)
 *
 * What it returns:
 * - a normalized relative path with '/' separators (e.g. "/a/b" -> "a/b")
 *
 * Throws:
 * - IllegalArgumentException via [require] with a descriptive message.
 */
fun requireValidRelativePath(
    input: String,
    allowLeadingSlash: Boolean = true
): String {
    val raw = input.trim()
    require(raw.isNotEmpty()) { "Path must not be blank" }

    // If present, treat a leading slash as "rooted at repository root", not an absolute FS path.
    val startIndex = if (allowLeadingSlash) raw.indexOfFirst { it != '/' }.let { if (it < 0) raw.length else it } else 0
    if (!allowLeadingSlash) {
        require(!raw.startsWith('/')) { "Path must be relative (must not start with '/')" }
    }

    // Fast check: all slashes means empty
    require(startIndex < raw.length) { "Path must contain at least one non-'/' character" }

    val out = StringBuilder(raw.length - startIndex)
    var segStart = startIndex
    var i = startIndex
    var wroteAnySegment = false

    fun fail(message: String): Nothing = throw IllegalArgumentException(message)

    fun checkAndAppendSegment(segEndExclusive: Int) {
        val segLen = segEndExclusive - segStart
        require(segLen > 0) {
            "Path contains an empty segment (e.g. \"//\") at index $segEndExclusive"
        }

        // Check "." / ".." without allocating a substring in the common case
        if (segLen == 1 && raw[segStart] == '.') {
            fail("Path contains invalid segment '.' at index $segStart")
        }
        if (segLen == 2 && raw[segStart] == '.' && raw[segStart + 1] == '.') {
            fail("Path contains invalid segment '..' at index $segStart")
        }

        if (wroteAnySegment) out.append('/')
        out.append(raw, segStart, segEndExclusive)
        wroteAnySegment = true
    }

    while (i < raw.length) {
        val c = raw[i]
        when (c) {
            '\u0000' -> fail("Path contains a NUL (\\u0000) character at index $i")
            '\\' -> fail("Path contains a backslash ('\\\\') at index $i; use '/' as a separator")
            '\r', '\n' -> fail("Path contains a line break character at index $i")
            '/' -> {
                checkAndAppendSegment(i)
                segStart = i + 1
            }
        }
        i++
    }

    // No trailing slash => last segment must exist
    require(segStart < raw.length) { "Path must not end with '/'" }
    checkAndAppendSegment(raw.length)

    return out.toString()
}