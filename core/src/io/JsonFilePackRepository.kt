package org.jetbrains.kastle.io

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.PackRepository

class JsonFilePackRepository(
    root: Path,
    fs: FileSystem = SystemFileSystem,
    json: Json = Json,
): FileSystemPackRepository(
    root = root,
    fs = fs,
    ext = "json",
    read = { it.readJson<PackDescriptor>(fs, json) },
    write = { path, descriptor -> path.writeJson(descriptor, fs, json) }
) {
    companion object {
        suspend fun PackRepository.exportToJson(
            path: Path,
            clear: Boolean = true,
            fs: FileSystem = SystemFileSystem,
            json: Json = Json,
        ): JsonFilePackRepository {
            if (clear)
                fs.deleteRecursively(path)
            fs.createDirectories(path)
            return JsonFilePackRepository(path, fs, json).also { export ->
                all().collect(export::add)
            }
        }
    }
}