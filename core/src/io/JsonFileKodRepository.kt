package org.jetbrains.kastle.io

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.KodDescriptor
import org.jetbrains.kastle.KodRepository

class JsonFileKodRepository(
    root: Path,
    fs: FileSystem = SystemFileSystem,
    json: Json = Json,
): FileSystemKodRepository(
    root = root,
    fs = fs,
    ext = "json",
    read = { it.readJson<KodDescriptor>(fs, json) },
    write = { path, descriptor -> path.writeJson(descriptor, fs, json) }
) {
    companion object {
        suspend fun KodRepository.exportToJson(
            path: String
        ) = exportToJson(Path(path))

        suspend fun KodRepository.exportToJson(
            path: Path,
            clear: Boolean = true,
            fs: FileSystem = SystemFileSystem,
            json: Json = Json,
        ): JsonFileKodRepository {
            if (clear)
                fs.deleteRecursively(path)
            fs.createDirectories(path)
            return JsonFileKodRepository(path, fs, json).also { export ->
                all().collect(export::add)
            }
        }
    }
}