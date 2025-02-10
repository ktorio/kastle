package org.jetbrains.kastle

import kotlinx.serialization.Serializable

sealed interface PackMetadata {
    val id: PackId
    val name: String
    val version: SemanticVersion
    val icon: String?
    val description: String?
    val license: String?
    val group: Group?
    val links: PackLinks?
    val documentation: String?
    val requires: List<PackReference>
    val properties: List<Property>
    val repositories: List<Repository>
}

@Serializable
data class PackManifest(
    override val id: PackId,
    override val name: String,
    override val version: SemanticVersion,
    override val group: Group? = null,
    override val license: String? = null,
    override val icon: String? = null,
    override val description: String? = null,
    override val links: PackLinks? = null,
    override val documentation: String? = null,
    override val requires: List<PackReference> = emptyList(),
    override val properties: List<Property> = emptyList(),
    override val repositories: List<Repository> = emptyList(),
    val path: String = "",
): PackMetadata

@Serializable
data class PackDescriptor(
    override val id: PackId,
    override val name: String,
    override val version: SemanticVersion,
    override val group: Group? = null,
    override val license: String? = null,
    override val icon: String? = null,
    override val description: String? = null,
    override val links: PackLinks? = null,
    override val documentation: String? = null,
    override val requires: List<PackReference> = emptyList(),
    override val properties: List<Property> = emptyList(),
    override val repositories: List<Repository> = emptyList(),
    val structure: ProjectStructure = ProjectStructure.Empty,
): PackMetadata {
    constructor(manifest: PackManifest, structure: ProjectStructure) : this(
        manifest.id,
        manifest.name,
        manifest.version,
        manifest.group,
        manifest.license,
        manifest.icon,
        manifest.description,
        manifest.links,
        manifest.documentation,
        manifest.requires,
        manifest.properties,
        manifest.repositories,
        if (structure is ProjectStructure.Single && structure.module.path.isEmpty())
            structure.copy(module = structure.module.copy(path = manifest.path))
        else structure
    )
}

val PackDescriptor.sources: Sequence<SourceTemplate> get() =
    structure.modules.asSequence().flatMap { it.sources }

@Serializable
data class Group(
    val id: String,
    val name: String,
    val icon: String? = null,
)

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