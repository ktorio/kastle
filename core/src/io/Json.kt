package org.jetbrains.kastle.io

import kotlinx.io.*

fun Sink.writeJsonString(buffer: Buffer) {
    writeByte('"'.code.toByte())
    while (!buffer.exhausted()) {
        val codePoint = buffer.readCodePointValue()
        val char = codePoint.toChar()
        when (char) {
            '"' -> writeString("\\\"") // Escape double quotes
            '\\' -> writeString("\\\\") // Escape backslashes
            '\b' -> writeString("\\b") // Escape backspace
            '\n' -> writeString("\\n") // Escape newlines
            '\r' -> writeString("\\r") // Escape carriage returns
            '\t' -> writeString("\\t") // Escape tabs
            in '\u0000'..'\u001F', '\u007F' -> { // Escape control characters
                writeString(String.format("\\u%04X", char.code))
            }
            else -> writeCodePointValue(codePoint) // Append valid characters as is
        }
    }
    writeByte('"'.code.toByte())
}