package org.jetbrains.kastle.io

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.PackMetadata
import org.jetbrains.kastle.VersionsCatalog

@OptIn(ExperimentalSerializationApi::class)
class CborFilePackRepository(
    root: Path,
    fs: FileSystem = SystemFileSystem,
    val cbor: Cbor = Cbor { ignoreUnknownKeys = true; encodeDefaults = false },
): FileSystemPackRepository(
    root = root,
    fs = fs,
    ext = "cbor",
) {
    override fun readDescriptor(path: Path): PackDescriptor? =
        path.readCbor<PackDescriptor>(fs, cbor)

    override fun writeDescriptor(path: Path, descriptor: PackDescriptor) =
        path.writeCbor(descriptor, fs, cbor)

    override fun readMetadata(path: Path): PackMetadata? =
        path.readCbor<PackMetadata>(fs, cbor)

    override fun writeMetadata(path: Path, metadata: PackMetadata) =
        path.writeCbor(metadata, fs, cbor)

    override fun readVersions(path: Path): VersionsCatalog =
        path.readCbor<VersionsCatalog>(fs, cbor) ?: VersionsCatalog.Empty

    override fun writeVersions(path: Path, versions: VersionsCatalog) =
        path.writeCbor(versions, fs, cbor)
}