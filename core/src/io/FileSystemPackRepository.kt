package org.jetbrains.kastle.io

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.PackId
import org.jetbrains.kastle.MutablePackRepository
import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.logging.ConsoleLogger
import org.jetbrains.kastle.logging.Logger
import org.jetbrains.kastle.io.FileFormat.JSON
import org.jetbrains.kastle.io.FileFormat.CBOR
import java.text.Format

open class FileSystemPackRepository(
    val root: Path,
    val fs: FileSystem = SystemFileSystem,
    val ext: String,
    val read: (Path) -> PackDescriptor?,
    val write: (Path, PackDescriptor) -> Unit,
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
            fs.list(groupPath)
        }.asFlow().mapNotNull { path ->
            if (!path.toString().endsWith(ext)) return@mapNotNull null
            PackId.parse("${path.parent!!.name}/${path.name.removeSuffix(".${ext}")}")
        }

    override suspend fun get(id: PackId): PackDescriptor? {
        return read(root.resolve("$id.$ext"))
    }

    override suspend fun add(descriptor: PackDescriptor) {
        val file = root.resolve("${descriptor.id}.$ext")
        file.parent?.let(fs::createDirectories)
        write(file, descriptor)
    }

    override suspend fun remove(id: PackId) {
        fs.delete(root.resolve("$id.$ext"))
    }

}