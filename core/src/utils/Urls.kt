package org.jetbrains.kastle.utils

import org.jetbrains.kastle.FeatureId
import org.jetbrains.kastle.SlotId
import org.jetbrains.kastle.Url

val Url.protocol: String get() = substringBefore(':')
val Url.afterProtocol: String get() = substringAfter(':')
val Url.relativeFile: String get() = afterProtocol.trimStart('/')
val Url.slotId: SlotId get() = afterProtocol.split('/').filter {
    it.isNotEmpty()
}.let { (group, feature, slot) -> SlotId(FeatureId(group, feature), slot) }
val Url.extension: String get() = substringAfterLast('.', "").lowercase()