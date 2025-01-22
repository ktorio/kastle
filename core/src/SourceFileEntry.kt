package org.jetbrains.kastle

import kotlinx.io.Source

data class SourceFileEntry(
    val name: String,
    val content: () -> Source
)