package org.jetbrains.kastle

import kotlinx.serialization.Serializable

@Serializable
data class ProjectDescriptor(
    val name: String,
    val group: String,
    val packaging: PackagingStyle = PackagingStyle.FLAT,
    val properties: Map<VariableId, String> = emptyMap(),
    val packs: List<PackId> = emptyList(),
)

enum class PackagingStyle {
    FLAT,
    NESTED,
}