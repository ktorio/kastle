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
    val tags: List<String>
    val links: PackLinks?
    val documentation: String?
    val requires: List<PackId>
    val properties: List<PropertyDescriptor>
    val repositories: List<MavenRepository>
    val modules: List<SourceModuleMetadata>
}

@Serializable
data class PackManifest(
    override val id: PackId = PackId("", ""),
    override val name: String,
    override val version: SemanticVersion = SemanticVersion(1, 0, 0),
    override val group: Group? = null,
    override val tags: List<String> = emptyList(),
    override val license: String? = null,
    override val icon: String? = null,
    override val description: String? = null,
    override val links: PackLinks? = null,
    override val documentation: String? = null,
    override val requires: List<PackId> = emptyList(),
    override val properties: List<PropertyDescriptor> = emptyList(),
    override val repositories: List<MavenRepository> = emptyList(),
    override val modules: List<SourceModuleMetadata> = emptyList(),
    val commonSources: List<SourceDefinition> = emptyList(),
    val rootSources: List<SourceDefinition> = emptyList(),
): PackMetadata

@Serializable
data class PackDescriptor(
    val manifest: PackMetadata,
    val propertyValues: List<PropertyAssignment>,
    val sources: PackSources,
): PackMetadata by manifest {
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

val PackDescriptor.commonAndRootSources: Sequence<SourceFile> get() =
    commonSources.asSequence() + rootSources.asSequence()

val PackDescriptor.allSources: Sequence<SourceFile> get() =
    commonSources.asSequence() + rootSources.asSequence() + sourceModules.asSequence().flatMap { it.sources }

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
    val guide: String? = null,
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
        fun parse(text: String, relativePackId: PackId? = null): VariableId {
            val segments = text.split('/', limit = 3)
            if (segments.size < 3) {
                if (relativePackId == null)
                    throw IllegalArgumentException("Invalid variable id: $text")
                return VariableId(relativePackId, text)
            }
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