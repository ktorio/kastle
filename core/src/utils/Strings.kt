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

fun String.appendPath(vararg paths: String): String =
    sequenceOf(this, *paths)
        .filter(String::isNotEmpty)
        .map { it.trim('/') }
        .joinToString("/")

fun String.startOfLine(index: Int): Int? {
    for (i in index - 1 downTo 0) {
        val ch = get(i)
        if (ch == '\n')
            return i
        else if (ch.isWhitespace())
            continue
        return null
    }
    return null
}

fun String.endOfLine(index: Int): Int? {
    for (i in index + 1 until length) {
        val ch = get(i)
        if (ch == '\n')
            return i
        else if (ch.isWhitespace())
            continue
        return null
    }
    return null
}

fun String.indentAt(index: Int): Int? {
    var lastNonWhitespace = index
    for (i in index - 1 downTo 0) {
        val ch = get(i)
        if (ch == '\n')
            return lastNonWhitespace - i - 1
        else if (ch.isWhitespace())
            continue
        lastNonWhitespace = i
    }
    return null
}
