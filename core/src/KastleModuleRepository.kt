package org.jetbrains.kastle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.kastle.utils.slots

interface KodRepository {
    companion object {
        val EMPTY = object : KodRepository {
            override fun kodIds(): Flow<KodId> = emptyFlow()
            override suspend fun get(kodId: KodId): KodDescriptor? = null
            override suspend fun slot(slotId: SlotId): Slot? = null
        }
    }
    fun kodIds(): Flow<KodId>
    fun all(): Flow<KodDescriptor> =
        kodIds().mapNotNull(::get)

    suspend fun get(kodId: KodId): KodDescriptor?
    suspend fun slot(slotId: SlotId): Slot? =
        get(slotId.kod)?.sources?.asSequence()
            ?.flatMap { it.slots }
            ?.find { slot -> slot.name == slotId.name }
}

suspend fun KodRepository.get(kodId: String): KodDescriptor? =
    get(KodId.parse(kodId))

interface MutableKodRepository : KodRepository {
    suspend fun add(descriptor: KodDescriptor)
    suspend fun remove(id: KodId)
}