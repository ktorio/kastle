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
        val file = path.resolve(name)
        createPathTo(path, file, fs)
        fs.sink(file).buffered().use { sink ->
            content().transferTo(sink)
        }
    }
}

/**
 * For some reason `createDirectories` doesn't work in Gradle tasks, so here we write our own function.
 */
private fun createPathTo(root: Path, path: Path, fs: FileSystem) {
    val ancestors = mutableListOf<Path>()
    var current: Path? = path
    generateSequence {
        current?.parent
            .takeIf { it != root }
            .also { current = it }
    }.forEach(ancestors::add)
    ancestors.reversed().forEach {
        fs.createDirectories(it)
    }
}