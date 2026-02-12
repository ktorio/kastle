package org.jetbrains.kastle.io

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.PackMetadata
import org.jetbrains.kastle.VersionsCatalog

class JsonFilePackRepository(
    root: Path,
    fs: FileSystem = SystemFileSystem,
    val json: Json = Json,
): FileSystemPackRepository(
    root = root,
    fs = fs,
    ext = "json",
) {
    override fun readMetadata(path: Path): PackMetadata? =
        path.readJson<PackMetadata>(fs, json)

    override fun writeMetadata(path: Path, metadata: PackMetadata) =
        path.writeJson(metadata, fs, json)

    override fun readDescriptor(path: Path): PackDescriptor? =
        path.readJson<PackDescriptor>(fs, json)

    override fun writeDescriptor(path: Path, descriptor: PackDescriptor) =
        path.writeJson(descriptor, fs, json)

    override fun readVersions(path: Path): VersionsCatalog =
        path.readJson<VersionsCatalog>(fs, json) ?: VersionsCatalog.Empty

    override fun writeVersions(path: Path, versions: VersionsCatalog) =
        path.writeJson(versions, fs, json)
}