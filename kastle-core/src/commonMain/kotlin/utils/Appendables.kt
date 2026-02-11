package org.jetbrains.kastle.utils

import kotlinx.io.Buffer
import kotlinx.io.writeCodePointValue
import kotlinx.io.writeString

object DevNull: Appendable {
    override fun append(value: Char): Appendable = this
    override fun append(value: CharSequence?): Appendable = this
    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable {
        if (value == null) return this
        checkBounds(startIndex, endIndex, value)
        return this
    }
}

class BufferAppendable(val buffer: Buffer): Appendable {
    override fun append(value: Char): Appendable {
        buffer.writeCodePointValue(value.code)
        return this
    }

    override fun append(value: CharSequence?): Appendable {
        if (value == null) return this
        buffer.writeString(value)
        return this
    }

    override fun append(
        value: CharSequence?,
        startIndex: Int,
        endIndex: Int
    ): Appendable {
        if (value == null) return this
        checkBounds(startIndex, endIndex, value)
        buffer.writeString(value, startIndex, endIndex)
        return this
    }
}

private fun checkBounds(startIndex: Int, endIndex: Int, value: CharSequence) {
    check(startIndex <= endIndex) {
        "Overlap $startIndex > $endIndex: ${value.substring(endIndex, startIndex)}"
    }
    check(endIndex <= value.length) {
        "End index out of bounds: $endIndex > ${value.length}"
    }
}
