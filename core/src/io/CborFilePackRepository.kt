package org.jetbrains.kastle.io

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import org.jetbrains.kastle.PackDescriptor

@OptIn(ExperimentalSerializationApi::class)
class CborFilePackRepository(
    root: Path,
    fs: FileSystem = SystemFileSystem,
    cbor: Cbor = Cbor.Default,
): FileSystemPackRepository(
    root = root,
    fs = fs,
    ext = "cbor",
    read = { it.readCbor<PackDescriptor>(fs, cbor) },
    write = { path, descriptor -> path.writeCbor(descriptor, fs, cbor) }
)