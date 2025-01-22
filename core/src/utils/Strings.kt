package org.jetbrains.kastle.utils

/**
 * Gets the whitespace between the last newline and the first non-whitespace character.
 */
fun String.getIndentAt(startIndex: Int) = lastIndexOf('\n', startIndex).takeIf { it > 0 }
    ?.let { newLineIndex ->
        val nonWhitespaceIndex = indexOfFirst {
            !it.isWhitespace()
        }.takeIf { it > 0 } ?: newLineIndex
        substring(newLineIndex + 1, nonWhitespaceIndex)
    }?.takeIf(String::isBlank) ?: ""