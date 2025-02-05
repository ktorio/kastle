package org.jetbrains.kastle.utils

import org.jetbrains.kastle.KodId
import org.jetbrains.kastle.SlotId
import org.jetbrains.kastle.Url

val Url.protocol: String get() = substringBefore(':')
val Url.afterProtocol: String get() = substringAfter(':')
val Url.relativeFile: String get() = afterProtocol.trimStart('/')
val Url.slotId: SlotId get() = afterProtocol.split('/').filter {
    it.isNotEmpty()
}.let { (group, kod, slot) -> SlotId(KodId(group, kod), slot) }
val Url.extension: String get() = substringAfterLast('.', "").lowercase()