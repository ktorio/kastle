package org.jetbrains.kastle.utils

import kotlinx.io.Buffer
import kotlinx.io.writeCodePointValue
import kotlinx.io.writeString
import org.jetbrains.kastle.SourceImport
import org.jetbrains.kastle.SourceImports

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

/**
 * Tracks which import symbols are actually referenced in the output body during a dry-run.
 * Initialized with the template's imports; symbols are marked off as they're encountered
 * in appended content. Import and package lines are skipped via regex. Wildcard imports
 * are always retained since resolving them would require external dependencies.
 */
class ImportTrackingAppendable(private val imports: SourceImports) : Appendable {
    private val preambleLine = Regex("^\\s*(import|package)\\s")

    // Non-wildcard imports not yet seen in the output; removed as they're matched
    private val pending: MutableMap<String, Pair<Regex, SourceImport>>

    init {
        val map = mutableMapOf<String, Pair<Regex, SourceImport>>()
        for (import in imports.imports) {
            val fqn = when (import) {
                is SourceImport.Module -> import.value
                is SourceImport.External -> import.value
            }
            if (!fqn.endsWith(".*")) {
                val simpleName = fqn.substringAfterLast('.')
                map[simpleName] = Regex("\\b${Regex.escape(simpleName)}\\b") to import
            }
        }
        pending = map
    }

    override fun append(value: Char) = apply { scan(value.toString()) }
    override fun append(value: CharSequence?) = apply { if (value != null) scan(value) }
    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int) = apply {
        if (value != null) {
            checkBounds(startIndex, endIndex, value)
            scan(value, startIndex, endIndex)
        }
    }

    private fun scan(content: CharSequence, start: Int = 0, end: Int = content.length) {
        if (pending.isEmpty()) return
        content.subSequence(start, end).split('\n').forEach { line ->
            if (pending.isEmpty()) return
            if (preambleLine.containsMatchIn(line)) return@forEach
            val iter = pending.iterator()
            while (iter.hasNext()) {
                val (_, regexAndImport) = iter.next()
                if (regexAndImport.first.containsMatchIn(line)) iter.remove()
            }
        }
    }

    /**
     * Returns a copy of the original [imports] with unused symbols removed, or the
     * same instance if nothing was filtered. Wildcards are never removed.
     */
    fun filterUnused(): SourceImports {
        if (pending.isEmpty()) return imports
        val unused = pending.values.mapTo(mutableSetOf()) { it.second }
        return imports.copy(imports = imports.imports.filterNot { it in unused })
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
