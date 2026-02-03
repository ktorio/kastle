package org.jetbrains.kastle.utils

import org.jetbrains.kastle.PackId
import org.jetbrains.kastle.SlotId
import org.jetbrains.kastle.Url

val StringExpression.protocol: String get() = toString().protocol
val String.protocol: String get() = substringBefore(':')
val StringExpression.afterProtocol: String get() = toString().substringAfter(':')
val StringExpression.relativeFile: String get() = toString().relativeFile
val String.parentPath: String get() = relativeFile.replaceAfterLast('/', "").dropLast(1)
val String.relativeFile: String get() = substringAfter(':').trimStart('/')
val StringExpression.fileName: String get() = afterProtocol.substringAfterLast('/')
val StringExpression.slotId: SlotId get() = afterProtocol.split('/')
    .filter { it.isNotEmpty() }
    .let { (group, pack, slot) -> SlotId(PackId(group, pack), slot) }
val StringExpression.extension: String get() = toString().extension
val String.extension: String get() = toString().substringAfterLast('.', "").lowercase()
fun StringExpression.takeIfSlot() = takeIf { it.protocol == "slot" }