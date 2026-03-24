package org.jetbrains.kastle.io

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import java.nio.file.Files
import java.nio.file.Paths

actual fun FileSystem.mkdir(path: Path) {
    val jvmPath = Paths.get(path.toString())
    if (Files.exists(jvmPath)) return
    Files.createDirectory(jvmPath)
}
