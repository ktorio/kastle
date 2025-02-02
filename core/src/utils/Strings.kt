package org.jetbrains.kastle.utils

/**
 * Gets the whitespace between the last newline and the first non-whitespace character.
 */
fun String.getIndentAt(startIndex: Int) = lastIndexOf('\n', startIndex).takeIf { it > 0 }
    ?.let { newLineIndex ->
        val nonWhitespaceIndex = subSequence(newLineIndex..<startIndex)
            .indexOfFirst { !it.isWhitespace() }
            .takeIf { it > newLineIndex + 1 } ?: startIndex
        substring(newLineIndex + 1, nonWhitespaceIndex)
    }?.takeIf(String::isBlank) ?: ""

fun String.trimAngleBrackets() =
    trimEnclosingCharacters('<', '>')

fun String.trimBraces() =
    trimEnclosingCharacters('{', '}')

fun String.trimEnclosingCharacters(start: Char, end: Char) =
    if (startsWith(start) && endsWith(end))
        substring(1, length - 1)
    else this

fun String.unwrapQuotes() =
    if (startsWith('"') && endsWith('"'))
        substring(1, length - 1)
    else this