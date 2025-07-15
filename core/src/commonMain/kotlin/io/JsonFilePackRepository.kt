package org.jetbrains.kastle.io

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.VersionsCatalog

class JsonFilePackRepository(
    root: Path,
    fs: FileSystem = SystemFileSystem,
    json: Json = Json,
): FileSystemPackRepository(
    root = root,
    fs = fs,
    ext = "json",
    read = { it.readJson<PackDescriptor>(fs, json) },
    write = { path, descriptor -> path.writeJson(descriptor, fs, json) },
    readVersions = { it.readJson<VersionsCatalog>(fs, json) ?: VersionsCatalog.Empty },
    writeVersions = { path, versions -> path.writeJson(versions, fs, json) },
)