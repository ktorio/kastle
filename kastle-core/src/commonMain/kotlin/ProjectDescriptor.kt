package org.jetbrains.kastle

import kotlinx.serialization.Serializable

@Serializable
data class ProjectDescriptor(
    val name: String,
    val group: String,
    val packaging: PackagingStyle = PackagingStyle.FLAT,
    val properties: Map<VariableId, String> = emptyMap(),
    val packs: List<PackId> = emptyList(),
    val platforms: PlatformSettings = PlatformSettings.All,
)

enum class PackagingStyle {
    FLAT,
    NESTED,
}

@Serializable(PlatformSettingsSerializer::class)
sealed interface PlatformSettings {
    companion object {
        fun parse(text: String) = when (text) {
            "*" -> All
            else -> of(text.split(',').map(Platform::parse).toSet())
        }
        fun of(platforms: Set<Platform>): PlatformSettings {
            val platformsWithCommon = platforms + Platform.COMMON
            return when(platformsWithCommon.size) {
                Platform.entries.size, 1 -> All
                else -> Selected(platformsWithCommon)
            }
        }
    }

    operator fun contains(platform: Platform): Boolean

    object All: PlatformSettings {
        override fun contains(platform: Platform): Boolean =
            true
    }

    data class Selected(val platforms: Set<Platform>): PlatformSettings {
        override fun contains(platform: Platform): Boolean =
            platform in platforms
    }
}