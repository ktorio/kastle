package org.jetbrains.kastle.io

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.kastle.*
import org.jetbrains.kastle.io.FileFormat.CBOR
import org.jetbrains.kastle.io.FileFormat.JSON
import org.jetbrains.kastle.logging.ConsoleLogger
import org.jetbrains.kastle.logging.Logger

abstract class FileSystemPackRepository(
    val root: Path,
    val fs: FileSystem = SystemFileSystem,
    val ext: String,
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
            fs.mkdirs(path)
            val export = when(fileFormat) {
                JSON -> JsonFilePackRepository(path, fs)
                CBOR -> CborFilePackRepository(path, fs)
            }

            // versions catalog
            export.catalogs(catalogs())

            // extra files
            files().collect { path ->
                readFile(path)?.let { export.file(path, it) }
            }

            // sources and manifests
            readAll().collect { pack ->
                try {
                    export.add(pack)
                } catch (e: Exception) {
                    logger.info { "Failed to export pack ${pack.id}: ${e.message}" }
                }
            }
            return export
        }
    }

    abstract fun readDescriptor(path: Path): PackDescriptor?
    abstract fun writeDescriptor(path: Path, descriptor: PackDescriptor)
    abstract fun readMetadata(path: Path): PackMetadata?
    abstract fun writeMetadata(path: Path, metadata: PackMetadata)
    abstract fun readVersions(path: Path): VersionsCatalog
    abstract fun writeVersions(path: Path, versions: VersionsCatalog)

    protected fun <T> tryRead(readOp: (Path) -> T): (Path) -> T = { path ->
        try {
            readOp(path)
        } catch (e: Exception) {
            throw PackReadException(idFromPath(path), e)
        }
    }

    override fun ids(): Flow<PackId> =
        fs.list(root).flatMap { groupPath ->
            if (fs.isDirectory(groupPath)) {
                fs.list(groupPath)
            } else emptyList()
        }.asFlow().mapNotNull(::idFromPath)

    protected fun idFromPath(path: Path): PackId? {
        return if (!path.toString().endsWith(ext)) null
        else PackId.parse("${path.parent!!.name}/${path.name.removeSuffix(".${ext}")}")
    }

    override fun groups(): Flow<Group> =
        fs.list(root).asFlow().mapNotNull { groupPath ->
            fs.list(groupPath).firstOrNull()?.let { path ->
                if (!path.toString().endsWith(ext)) return@mapNotNull null
                tryRead(::readDescriptor)(path)?.group
            }
        }

    override fun getAll(): Flow<PackMetadata> =
        allPackFiles()
            .filter { it.name.endsWith(".meta.${ext}") }
            .mapNotNull(tryRead(::readMetadata))
            .asFlow()

    override fun readAll(): Flow<PackDescriptor> {
        val versionsDir = getVersionsDir()
        return allPackFiles()
            .filter { it.name.endsWith(".${ext}") && !it.name.endsWith(".meta.${ext}") }
            .filterNot { it.parent == versionsDir }
            .mapNotNull(tryRead(::readDescriptor))
            .asFlow()
    }

    override suspend fun get(packId: PackId): PackMetadata? =
        readMetadata(root.resolve("$packId.$ext"))

    override suspend fun read(packId: PackId): PackDescriptor? =
        readDescriptor(root.resolve("$packId.$ext"))

    override suspend fun add(descriptor: PackDescriptor) {
        val completeFile = root.resolve("${descriptor.id}.$ext")
        val metaFile = root.resolve("${descriptor.id}.meta.$ext")
        completeFile.parent?.let(fs::createDirectories)
        writeDescriptor(completeFile, descriptor)
        writeMetadata(metaFile, descriptor.manifest)
    }

    override suspend fun remove(id: PackId) {
        fs.delete(root.resolve("$id.$ext"))
    }

    override suspend fun versions(): VersionsCatalog =
        readVersions(root.resolve("versions/${VersionsCatalog.DEFAULT_NAME}.versions.$ext"))

    override suspend fun catalogs(): List<VersionsCatalog> {
        val versionsDir = getVersionsDir()
        if (!fs.isDirectory(versionsDir)) return emptyList()
        return fs.list(versionsDir).map { readVersions(it) }
    }

    override suspend fun catalogs(catalogs: List<VersionsCatalog>) {
        fs.mkdirs(getVersionsDir())
        for (catalog in catalogs) {
            writeVersions(root.resolve("versions/${catalog.name}.versions.$ext"), catalog)
        }
    }

    private fun getVersionsDir(): Path = root.resolve("versions")

    override fun files(): Flow<String> =
        fs.walkFiles(root).filter {
            !it.name.endsWith(".$ext")
        }.map {
            it.relativeTo(root).toString()
        }.asFlow()

    override suspend fun readFile(path: String): Source? =
        root.resolve(path.trimStart('/'))
            .takeIf(fs::exists)?.let(fs::source)?.buffered()

    override suspend fun file(path: String, bytes: Source) {
        val path = root.resolve(path.trimStart('/'))
        path.parent?.let(fs::mkdirs)
        fs.sink(path).use { sink ->
            bytes.transferTo(sink)
        }
    }

    private fun allPackFiles(): Sequence<Path> = fs.list(root).asSequence().flatMap { groupDir ->
        if (!fs.isDirectory(groupDir)) return@flatMap emptySequence()
        fs.list(groupDir).asSequence()
    }
}
