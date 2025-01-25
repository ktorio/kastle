package org.jetbrains.kastle

import kotlinx.io.Buffer

data class SourceFileEntry(
    val name: String,
    val content: () -> Buffer
)