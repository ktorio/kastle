package org.jetbrains.kastle

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.concatWith
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.io.Source
import org.jetbrains.kastle.utils.slots

interface PackRepository {
    companion object {
        val EMPTY = object : PackRepository {
            override fun ids(): Flow<PackId> = emptyFlow()
            override fun groups(): Flow<Group> = emptyFlow()
            override fun files(): Flow<String> = emptyFlow()
            override suspend fun get(packId: PackId): PackMetadata? = null
            override fun readAll(): Flow<PackDescriptor> = emptyFlow()
            override suspend fun read(packId: PackId): PackDescriptor? = null
            override suspend fun readFile(path: String): Source? = null
            override suspend fun slot(slotId: SlotId): SlotDescriptor? = null
            override suspend fun versions(): VersionsCatalog = VersionsCatalog.Empty
        }
    }

    /**
     * Get all pack IDs in this repository.
     */
    fun ids(): Flow<PackId>

    /**
     * Get all groups in this repository.
     */
    fun groups(): Flow<Group>

    /**
     * Get a list of supplementary files. Used for items referenced, but not included, in the metadata, such as images.
     *
     * Currently, the default is to only return icons.
     */
    fun files(): Flow<String> =
        getAll().mapNotNull { pack ->
            pack.icon?.let { "${pack.id}/$it" }
        }.onCompletion {
            emitAll(groups().mapNotNull { group ->
                group.icon?.let { "${group.id}/$it" }
            })
        }

    /**
     * Get metadata for a pack in this repository, ignoring sources.
     */
    suspend fun get(packId: PackId): PackMetadata?

    /**
     * Get metadata for all packs in this repository, ignoring sources.
     */
    fun getAll(): Flow<PackMetadata> = ids().map {
        get(it) ?: throw MissingPackException(it)
    }

    /**
     * Get the version catalog for this repository.
     */
    suspend fun versions(): VersionsCatalog

    /**
     * Get a pack by its ID.  Includes sources.
     */
    suspend fun read(packId: PackId): PackDescriptor?

    /**
     * Read a file from the repository. Used for items referenced, but not included, in the metadata, such as images.
     */
    suspend fun readFile(path: String): Source?

    /**
     * Get full details for all packs in this repository.
     */
    fun readAll(): Flow<PackDescriptor> =
        ids().map { read(it) ?: throw MissingPackException(it) }

    /**
     * Get full details for the selected packs in this repository.
     */
    fun readAll(packIds: Collection<PackId>): Flow<PackDescriptor> =
        packIds.asFlow().map { read(it) ?: throw MissingPackException(it) }

    /**
     * Get full details of the selected packs AND all their dependencies.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getAllWithRequirements(packIds: Collection<PackId>): Flow<PackDescriptor> =
        packIds.asFlow().flatMapConcat { packId ->
            flow {
                try {
                    val pack = read(packId) ?: throw MissingPackException(packId)
                    emit(pack)
                    emitAll(getAllWithRequirements(pack.requires.filter { it !in packIds }))
                } catch (cause: Throwable) {
                    throw PackReadException(packId, cause)
                }
            }
        }

    /**
     * Get a slot by its ID.
     */
    suspend fun slot(slotId: SlotId): SlotDescriptor? =
        read(slotId.pack)?.allSources
            ?.filterIsInstance<SourceTemplate>()
            ?.firstNotNullOfOrNull { source ->
                source.slots
                    .find { slot -> slot.name == slotId.name }
                    ?.let { SlotDescriptor(it, source.target) }
            }
}

suspend fun PackRepository.read(packId: String): PackDescriptor? =
    read(PackId.parse(packId))

interface MutablePackRepository : PackRepository {
    suspend fun add(descriptor: PackDescriptor)
    suspend fun remove(id: PackId)
    suspend fun versions(versions: VersionsCatalog)
    suspend fun file(path: String, bytes: Source)
}