package org.jetbrains.kastle.io

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.kastle.FeatureDescriptor
import org.jetbrains.kastle.FeatureId
import org.jetbrains.kastle.MutableFeatureRepository

open class FileSystemFeatureRepository(
    val root: Path,
    val fs: FileSystem = SystemFileSystem,
    val ext: String,
    val read: (Path) -> FeatureDescriptor?,
    val write: (Path, FeatureDescriptor) -> Unit,
) : MutableFeatureRepository {

    override fun featureIds(): Flow<FeatureId> =
        fs.list(root).flatMap { groupPath ->
            fs.list(groupPath)
        }.asFlow().mapNotNull { path ->
            if (fs.metadataOrNull(path)?.isDirectory == true)
                FeatureId.parse("${path.parent!!.name}/${path.name.removeSuffix(".$ext")}")
            else null
        }

    override suspend fun get(id: FeatureId): FeatureDescriptor? {
        return read(root.resolve("$id.$ext"))
    }

    override suspend fun add(descriptor: FeatureDescriptor) {
        val file = root.resolve("${descriptor.id}.$ext")
        file.parent?.let(fs::createDirectories)
        write(file, descriptor)
    }

    override suspend fun remove(id: FeatureId) {
        fs.delete(root.resolve("$id.$ext"))
    }

}