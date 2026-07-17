package org.jetbrains.kastle

import kotlinx.serialization.Serializable
import kotlin.collections.map

@Serializable
data class ProjectDescriptor(
    val name: String,
    val group: String,
    val packaging: PackagingStyle = PackagingStyle.FLAT,
    val properties: Map<VariableId, String> = emptyMap(),
    val packs: List<PackSelection> = emptyList(),
) {
    val packIds: List<PackId> = packs.map { it.packId }
}

enum class PackagingStyle {
    FLAT,
    NESTED,
}