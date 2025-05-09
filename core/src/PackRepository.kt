package org.jetbrains.kastle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.kastle.utils.slots

interface PackRepository {
    companion object {
        val EMPTY = object : PackRepository {
            override fun ids(): Flow<PackId> = emptyFlow()
            override suspend fun get(packId: PackId): PackDescriptor? = null
            override suspend fun slot(slotId: SlotId): SlotDescriptor? = null
        }
    }
    fun ids(): Flow<PackId>

    fun all(): Flow<PackDescriptor> = ids().mapNotNull(::get)

    suspend fun get(packId: PackId): PackDescriptor?

    suspend fun getAll(packIds: Collection<PackId>): Flow<PackDescriptor> =
        packIds.asFlow().mapNotNull(::get)

    suspend fun slot(slotId: SlotId): SlotDescriptor? =
        get(slotId.pack)?.allSources?.asSequence()
            ?.firstNotNullOfOrNull { source ->
                source.slots
                    .find { slot -> slot.name == slotId.name }
                    ?.let { SlotDescriptor(it, source.target) }
            }
}

suspend fun PackRepository.get(packId: String): PackDescriptor? =
    get(PackId.parse(packId))

interface MutablePackRepository : PackRepository {
    suspend fun add(descriptor: PackDescriptor)
    suspend fun remove(id: PackId)
}