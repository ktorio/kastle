package org.jetbrains.kastle

import kotlinx.serialization.Serializable

@Serializable
sealed interface PackMetadata {
    val id: PackId
    val name: String
    val version: SemanticVersion
    val icon: String?
    val description: String?
    val license: String?
    val group: Group?
    val categories: List<String>
    val links: PackLinks?
    val documentation: String?
    val requires: List<PackId>
    val properties: List<Property>
    val attributes: Map<VariableId, String>
    val repositories: List<Repository>
}

@Serializable
data class PackManifest(
    override val id: PackId = PackId("", ""),
    override val name: String,
    override val version: SemanticVersion = SemanticVersion(1, 0, 0),
    override val group: Group? = null,
    override val categories: List<String> = emptyList(),
    override val license: String? = null,
    override val icon: String? = null,
    override val description: String? = null,
    override val links: PackLinks? = null,
    override val documentation: String? = null,
    override val requires: List<PackId> = emptyList(),
    override val properties: List<Property> = emptyList(),
    override val attributes: Map<VariableId, String> = emptyMap(),
    override val repositories: List<Repository> = emptyList(),
    val modules: List<SourceModule>? = null,
    val commonSources: List<SourceDefinition> = emptyList(),
    val rootSources: List<SourceDefinition> = emptyList(),
): PackMetadata

@Serializable
data class ModuleManifest(
    val platform: String? = null,
    val platforms: List<String>? = null,
    val dependencies: List<String>? = null,
    val gradle: GradleSettings? = null,
    val sources: List<SourceDefinition>? = null,
)

@Serializable
data class PackDescriptor(
    val info: PackMetadata,
    val sources: PackSources,
): PackMetadata by info {
    val commonSources: List<SourceFile> get() = sources.common
    val rootSources: List<SourceFile> get() = sources.root
    val sourceModules: List<SourceModule> get() = sources.modules.modules
}

/**
 * Templates for a PACK.
 *
 * @property common templates that are repeated in every module
 * @property root templates that are defined in the project root
 * @property modules regular project sources, organized by module
 */
@Serializable
data class PackSources(
    val common: List<SourceFile> = emptyList(),
    val root: List<SourceFile> = emptyList(),
    val modules: ProjectModules = ProjectModules.Empty,
) {
    companion object {
        val Empty = PackSources()
    }
}

val PackDescriptor.allSources: Sequence<SourceFile> get() =
    commonSources.asSequence() +
        rootSources.asSequence() +
        sourceModules.asSequence().flatMap { it.sources }

@Serializable
data class Group(
    val id: String,
    val name: String? = null,
    val icon: String? = null,
)

// TODO use versioned requirements
@Serializable
data class PackReference(
    val id: PackId,
    val version: VersionRange
)

@Serializable
data class PackLinks(
    val vcs: String? = null,
    val home: String? = null,
    val docs: String? = null,
)

@Serializable
data class SlotDescriptor(
    val slot: Slot,
    val parent: Url,
): Slot by slot

@Serializable(PackIdSerializer::class)
data class PackId(val group: String, val id: String) {
    companion object {
        fun parse(text: String) = text.split('/', limit = 2).let { (group, pack) ->
            PackId(group, pack)
        }
    }
    override fun toString(): String =
        "$group/$id"
}

@Serializable(VariableIdSerializer::class)
data class VariableId(val packId: PackId, val name: String) {
    companion object {
        fun parse(text: String): VariableId {
            val segments = text.split('/', limit = 3)
            if (segments.size != 3) throw IllegalArgumentException("Invalid variable id: $text")
            val (group, pack, variable) = segments
            return VariableId(PackId(group, pack), variable)
        }
    }
    override fun toString(): String =
        "$packId/$name"
}

@Serializable(SlotIdSerializer::class)
data class SlotId(val pack: PackId, val name: String) {
    companion object {
        fun parse(text: String) = text.split('/', limit = 3).let { (group, pack, slot) ->
            SlotId(PackId(group, pack), slot)
        }
    }
    val group: String get() = pack.group
    val packId: String get() = pack.id

    override fun toString(): String =
        "$group/$packId/$name"
}