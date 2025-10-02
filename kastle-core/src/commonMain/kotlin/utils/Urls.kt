package org.jetbrains.kastle.utils

import org.jetbrains.kastle.PackId
import org.jetbrains.kastle.SlotId
import org.jetbrains.kastle.Url

val Url.protocol: String get() = substringBefore(':')
val Url.afterProtocol: String get() = substringAfter(':')
val Url.parentPath: String get() = relativeFile.replaceAfterLast('/', "").dropLast(1)
val Url.relativeFile: String get() = afterProtocol.trimStart('/')
val Url.slotId: SlotId get() = afterProtocol.split('/')
    .filter { it.isNotEmpty() }
    .let { (group, pack, slot) -> SlotId(PackId(group, pack), slot) }
val Url.extension: String get() = substringAfterLast('.', "").lowercase()

fun Url.takeIfSlot() = takeIf { it.protocol == "slot" }