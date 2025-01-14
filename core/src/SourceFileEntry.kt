package org.jetbrains.kastle

import kotlinx.coroutines.flow.Flow
import kotlinx.io.Source
import kotlinx.io.files.Path
import kotlinx.io.files.sink
import org.jetbrains.kastle.io.resolve

data class SourceFileEntry(
    val name: String,
    val content: () -> Source
)