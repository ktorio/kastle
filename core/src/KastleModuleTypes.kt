package org.jetbrains.kastle

import kotlinx.serialization.Serializable

sealed interface KodMetadata {
    val id: KodId
    val name: String
    val version: SemanticVersion
    val icon: String?
    val description: String?
    val license: String?
    val group: Group?
    val links: KodLinks?
    val documentation: String?
    val prerequisites: List<KodReference>
    val properties: List<Property>
    val repositories: List<Repository>
}

@Serializable
data class KodManifest(
    override val id: KodId,
    override val name: String,
    override val version: SemanticVersion,
    override val group: Group? = null,
    override val license: String? = null,
    override val icon: String? = null,
    override val description: String? = null,
    override val links: KodLinks? = null,
    override val documentation: String? = null,
    override val prerequisites: List<KodReference> = emptyList(),
    override val properties: List<Property> = emptyList(),
    override val repositories: List<Repository> = emptyList(),
    val path: String = "",
): KodMetadata

@Serializable
data class KodDescriptor(
    override val id: KodId,
    override val name: String,
    override val version: SemanticVersion,
    override val group: Group? = null,
    override val license: String? = null,
    override val icon: String? = null,
    override val description: String? = null,
    override val links: KodLinks? = null,
    override val documentation: String? = null,
    override val prerequisites: List<KodReference> = emptyList(),
    override val properties: List<Property> = emptyList(),
    override val repositories: List<Repository> = emptyList(),
    val structure: ProjectStructure = ProjectStructure.Empty,
): KodMetadata {
    constructor(manifest: KodManifest, structure: ProjectStructure) : this(
        manifest.id,
        manifest.name,
        manifest.version,
        manifest.group,
        manifest.license,
        manifest.icon,
        manifest.description,
        manifest.links,
        manifest.documentation,
        manifest.prerequisites,
        manifest.properties,
        manifest.repositories,
        if (structure is ProjectStructure.Single && structure.module.path.isEmpty())
            structure.copy(module = structure.module.copy(path = manifest.path))
        else structure
    )
}

val KodDescriptor.sources: Sequence<SourceTemplate> get() =
    structure.modules.asSequence().flatMap { it.sources }

@Serializable
data class Group(
    val id: String,
    val name: String,
    val icon: String? = null,
)

@Serializable
data class KodReference(
    val id: KodId,
    val version: VersionRange
)

@Serializable
data class KodLinks(
    val vcs: String? = null,
    val home: String? = null,
    val docs: String? = null,
)

@Serializable(KodIdSerializer::class)
data class KodId(val group: String, val id: String) {
    companion object {
        fun parse(text: String) = text.split('/', limit = 2).let { (group, kod) ->
            KodId(group, kod)
        }
    }
    override fun toString(): String =
        "$group/$id"
}

@Serializable(SlotIdSerializer::class)
data class SlotId(val kod: KodId, val name: String) {
    companion object {
        fun parse(text: String) = text.split('/', limit = 3).let { (group, kod, slot) ->
            SlotId(KodId(group, kod), slot)
        }
    }
    val group: String get() = kod.group
    val kodId: String get() = kod.id

    override fun toString(): String =
        "$group/$kodId/$name"
}