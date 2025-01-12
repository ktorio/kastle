package org.jetbrains.kastle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull

interface FeatureRepository {
    companion object {
        val EMPTY = object : FeatureRepository {
            override fun featureIds(): Flow<FeatureId> = emptyFlow()
            override suspend fun get(featureId: FeatureId): FeatureDescriptor? = null
            override suspend fun slot(slotId: SlotId): Slot? = null
        }
    }
    fun featureIds(): Flow<FeatureId>
    fun all(): Flow<FeatureDescriptor> =
        featureIds().mapNotNull(::get)

    suspend fun get(featureId: FeatureId): FeatureDescriptor?
    suspend fun slot(slotId: SlotId): Slot? =
        get(slotId.feature)?.sources?.flatMap { it.slots.orEmpty() }?.find { slot ->
            slot.name == slotId.name
        }
}

suspend fun FeatureRepository.get(featureId: String): FeatureDescriptor? =
    get(FeatureId.parse(featureId))

interface MutableFeatureRepository : FeatureRepository {
    suspend fun add(descriptor: FeatureDescriptor)
    suspend fun remove(id: FeatureId)
}