package org.jetbrains.kastle.io

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.kastle.*
import org.jetbrains.kastle.io.FileFormat.CBOR
import org.jetbrains.kastle.io.FileFormat.JSON
import org.jetbrains.kastle.logging.ConsoleLogger
import org.jetbrains.kastle.logging.Logger

open class FileSystemPackRepository(
    val root: Path,
    val fs: FileSystem = SystemFileSystem,
    val ext: String,
    val read: (Path) -> PackDescriptor?,
    val write: (Path, PackDescriptor) -> Unit,
    val readVersions: (Path) -> VersionsCatalog,
    val writeVersions: (Path, VersionsCatalog) -> Unit,
) : MutablePackRepository {
    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        suspend fun PackRepository.export(
            path: Path,
            fileFormat: FileFormat = CBOR,
            clear: Boolean = true,
            fs: FileSystem = SystemFileSystem,
            logger: Logger = ConsoleLogger(),
        ): MutablePackRepository {
            if (clear) fs.deleteRecursively(path)
            fs.createDirectories(path)
            val export = when(fileFormat) {
                JSON -> JsonFilePackRepository(path, fs)
                CBOR -> CborFilePackRepository(path, fs)
            }
            export.versions(versions())
            all().collect { pack ->
                try {
                    export.add(pack)
                } catch (e: Exception) {
                    logger.info { "Failed to export pack ${pack.id}: ${e.message}" }
                }
            }
            return export
        }
    }

    override fun ids(): Flow<PackId> =
        fs.list(root).flatMap { groupPath ->
            if (fs.isDirectory(groupPath)) {
                fs.list(groupPath)
            } else emptyList()
        }.asFlow().mapNotNull { path ->
            if (!path.toString().endsWith(ext)) return@mapNotNull null
            PackId.parse("${path.parent!!.name}/${path.name.removeSuffix(".${ext}")}")
        }

    override suspend fun get(packId: PackId): PackDescriptor? {
        return read(root.resolve("$packId.$ext"))
    }

    override suspend fun add(descriptor: PackDescriptor) {
        val file = root.resolve("${descriptor.id}.$ext")
        file.parent?.let(fs::createDirectories)
        write(file, descriptor)
    }

    override suspend fun remove(id: PackId) {
        fs.delete(root.resolve("$id.$ext"))
    }

    override suspend fun versions(): VersionsCatalog =
        readVersions(root.resolve("libs.versions.$ext"))

    override suspend fun versions(versions: VersionsCatalog) {
        writeVersions(root.resolve("libs.versions.$ext"), versions() + versions)
    }
}