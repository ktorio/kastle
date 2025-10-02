package org.jetbrains.kastle.io

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.VersionsCatalog

@OptIn(ExperimentalSerializationApi::class)
class CborFilePackRepository(
    root: Path,
    fs: FileSystem = SystemFileSystem,
    cbor: Cbor = Cbor { ignoreUnknownKeys = true; encodeDefaults = false },
): FileSystemPackRepository(
    root = root,
    fs = fs,
    ext = "cbor",
    read = { it.readCbor<PackDescriptor>(fs, cbor) },
    write = { path, descriptor -> path.writeCbor(descriptor, fs, cbor) },
    readVersions = { it.readCbor<VersionsCatalog>(fs, cbor) ?: VersionsCatalog.Empty },
    writeVersions = { path, versions -> path.writeCbor(versions, fs, cbor) },
)