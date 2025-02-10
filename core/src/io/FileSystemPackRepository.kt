package org.jetbrains.kastle.io

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.PackId
import org.jetbrains.kastle.MutablePackRepository

open class FileSystemPackRepository(
    val root: Path,
    val fs: FileSystem = SystemFileSystem,
    val ext: String,
    val read: (Path) -> PackDescriptor?,
    val write: (Path, PackDescriptor) -> Unit,
) : MutablePackRepository {

    override fun packIds(): Flow<PackId> =
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