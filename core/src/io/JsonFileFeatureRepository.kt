package org.jetbrains.kastle.io

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.FeatureDescriptor
import org.jetbrains.kastle.FeatureRepository

class JsonFileFeatureRepository(
    root: Path,
    fs: FileSystem = SystemFileSystem,
    json: Json = Json,
): FileSystemFeatureRepository(
    root = root,
    fs = fs,
    ext = "json",
    read = { it.readJson<FeatureDescriptor>(fs, json) },
    write = { path, descriptor -> path.writeJson(descriptor, fs, json) }
) {
    companion object {
        suspend fun FeatureRepository.exportToJson(
            path: String
        ) = exportToJson(Path(path))

        suspend fun FeatureRepository.exportToJson(
            path: Path,
            clear: Boolean = true,
            fs: FileSystem = SystemFileSystem,
            json: Json = Json,
        ): JsonFileFeatureRepository {
            if (clear)
                fs.deleteRecursively(path)
            fs.createDirectories(path)
            return JsonFileFeatureRepository(path, fs, json).also { export ->
                all().collect(export::add)
            }
        }
    }
}