package org.jetbrains.kastle

import kotlinx.io.Buffer

data class SourceFileEntry(
    val path: String,
    val content: () -> Buffer
)