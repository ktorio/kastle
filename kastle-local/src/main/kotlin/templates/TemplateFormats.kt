package org.jetbrains.kastle.templates

import org.jetbrains.kastle.kotlin.KT_EXTENSION
import org.jetbrains.kastle.kotlin.KT_SCRIPT_EXTENSION

enum class TemplateFormat {
    KOTLIN,
    OTHER,
}

val String.extensionFormat: TemplateFormat
    get() = when (substringAfterLast('.')) {
        KT_EXTENSION, KT_SCRIPT_EXTENSION -> TemplateFormat.KOTLIN
        else -> TemplateFormat.OTHER
    }