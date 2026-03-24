package org.jetbrains.kastle.io

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path

actual fun FileSystem.mkdir(path: Path) {
    createDirectories(path) // just delegate to kotlinx-io
}
