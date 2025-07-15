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

fun String.capitalizeFirst() =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

fun String.trimAngleBrackets() =
    trimEnclosingCharacters('<', '>').trim()

fun String.trimBraces() =
    trimEnclosingCharacters('{', '}').trim()

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

fun String.startOfLine(index: Int, ignoreNonWhitespace: Boolean = false): Int? {
    for (i in index - 1 downTo 0) {
        val ch = get(i)
        if (ch == '\n')
            return i
        else if (!ch.isWhitespace() && !ignoreNonWhitespace)
            return null
    }
    return null
}

fun String.endOfLine(index: Int, ignoreNonWhitespace: Boolean = false): Int? {
    for (i in index + 1 until length) {
        val ch = get(i)
        if (ch == '\n')
            return i
        else if (!ch.isWhitespace() && !ignoreNonWhitespace)
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

fun String.previousLine(index: Int): String? {
    val currentLineStart = startOfLine(index) ?: return null
    val previousLineStart = startOfLine(currentLineStart) ?: 0
    return substring(previousLineStart, currentLineStart)
}

fun CharSequence.firstNonSpace(start: Int = 0, limit: Int = length): Int {
    for (i in start until limit) {
        if (get(i) != ' ')
            return i
    }
    return limit
}

fun CharSequence.lastNonWhitespace(start: Int = length, limit: Int = 0): Int {
    for (i in start - 1  downTo limit) {
        if (!get(i).isWhitespace())
            return i + 1
    }
    return limit
}

fun CharSequence.newLineIndices(
    start: Int = 0,
    limit: Int = length,
): Sequence<Int> = sequence {
    var index = start
    while (true) {
        index = indexOf('\n', index)
        if (index < 0 || index >= limit)
            break
        yield(index)
        index++
    }
}

/**
 * Removes line indents by the factor provided, multiplied by 4.
 */
fun Appendable.append(
    csq: CharSequence,
    start: Int,
    end: Int,
    level: Int,
): java.lang.Appendable {
    if (level <= 0) return append(csq, start, end)
    if (start == end) return this
    val skipIndent = level * 4

    var index = start
    for (newLineIndex in csq.newLineIndices(start, end)) {
        append(csq, index, newLineIndex)
        append('\n')

        index = minOf(
            csq.firstNonSpace(newLineIndex + 1, end),
            newLineIndex + 1 + skipIndent,
        )
    }
    if (index >= end)
        return this

    append(csq, index, end)
    return this
}