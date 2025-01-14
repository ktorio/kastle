package org.jetbrains.kastle.io

import kotlinx.coroutines.flow.Flow
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.kastle.SourceFileEntry
import kotlin.use

suspend fun Flow<SourceFileEntry>.export(path: Path, fs: FileSystem = SystemFileSystem) {
    fs.createDirectories(path)
    collect { (name, content) ->
        fs.sink(path.resolve(name)).buffered().use { sink ->
            content().transferTo(sink)
        }
    }
}