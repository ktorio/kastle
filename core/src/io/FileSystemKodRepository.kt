package org.jetbrains.kastle.io

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.kastle.KodDescriptor
import org.jetbrains.kastle.KodId
import org.jetbrains.kastle.MutableKodRepository

open class FileSystemKodRepository(
    val root: Path,
    val fs: FileSystem = SystemFileSystem,
    val ext: String,
    val read: (Path) -> KodDescriptor?,
    val write: (Path, KodDescriptor) -> Unit,
) : MutableKodRepository {

    override fun kodIds(): Flow<KodId> =
        fs.list(root).flatMap { groupPath ->
            fs.list(groupPath)
        }.asFlow().mapNotNull { path ->
            if (!path.toString().endsWith(ext)) return@mapNotNull null
            KodId.parse("${path.parent!!.name}/${path.name.removeSuffix(".${ext}")}")
        }

    override suspend fun get(id: KodId): KodDescriptor? {
        return read(root.resolve("$id.$ext"))
    }

    override suspend fun add(descriptor: KodDescriptor) {
        val file = root.resolve("${descriptor.id}.$ext")
        file.parent?.let(fs::createDirectories)
        write(file, descriptor)
    }

    override suspend fun remove(id: KodId) {
        fs.delete(root.resolve("$id.$ext"))
    }

}