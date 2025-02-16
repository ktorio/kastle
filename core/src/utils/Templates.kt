package org.jetbrains.kastle.utils

import org.jetbrains.kastle.Block
import org.jetbrains.kastle.Slot
import org.jetbrains.kastle.SourcePosition
import org.jetbrains.kastle.SourceTemplate

val SourceTemplate.slots: Sequence<Slot> get() =
    blocks?.asSequence()?.filterIsInstance<Slot>().orEmpty()

val Block.rangeStart: Int get() =
    position.rangeStart

val Block.rangeEnd: Int get() =
    position.rangeEnd

val Block.bodyStart: Int? get() =
    body?.rangeStart

val Block.bodyEnd: Int? get() =
    body?.rangeEnd

val SourcePosition.rangeStart: Int get() =
    range.start

val SourcePosition.rangeEnd: Int get() =
    range.endInclusive + 1

fun String.indent(indent: String) =
    lines().joinToString("\n$indent")

operator fun Block.contains(block: Block?) =
    block != null && block.rangeStart in position.range