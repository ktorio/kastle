package org.jetbrains.kastle.utils

import org.jetbrains.kastle.Block
import org.jetbrains.kastle.Slot
import org.jetbrains.kastle.SourceTemplate

val SourceTemplate.slots: Sequence<Slot> get() =
    blocks?.asSequence()?.filterIsInstance<Slot>().orEmpty()

fun SourceTemplate.isFile(): Boolean =
    target.protocol == "file"

fun SourceTemplate.isSlot(): Boolean =
    target.protocol == "slot"

val Block.range: IntRange get() =
    position.range

val Block.rangeStart: Int get() =
    range.first

val Block.outerStart: Int get() =
    position.outer.first

val Block.outerEnd: Int get() =
    position.outer.last

// TODO
val Block.indent: Int get() =
    position.indent

val Block.rangeEnd: Int get() =
    range.last

val Block.body: IntRange get() =
    position.inner

val Block.bodyStart: Int get() =
    position.inner.first

val Block.bodyEnd: Int get() =
    position.inner.last

fun Int.stringOf(char: Char) =
    if (this <= 0) "" else CharArray(this) { char }.concatToString()

fun String.indent(indent: String) =
    lines().joinToString("\n$indent")

operator fun Block.contains(block: Block?) =
    block != null && block.rangeStart in range.first until range.last