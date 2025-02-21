package org.jetbrains.kastle.templates

enum class TemplateFormat {
    KOTLIN,
    OTHER,
}

val String.extensionFormat: TemplateFormat
    get() = when (substringAfterLast('.')) {
        "kt", "kts" -> TemplateFormat.KOTLIN
        else -> TemplateFormat.OTHER
    }