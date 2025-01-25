package org.jetbrains.kastle

import kotlinx.serialization.Serializable

sealed interface FeatureMetadata {
    val id: FeatureId
    val name: String
    val version: SemanticVersion
    val icon: String?
    val description: String?
    val license: String?
    val group: Group?
    val links: FeatureLinks?
    val documentation: String?
    val prerequisites: List<FeatureReference>
    val properties: List<Property>
    val repositories: List<Repository>
    val dependencies: List<BuildSystemDependency>
    val modules: List<Module>
}

@Serializable
data class FeatureManifest(
    override val id: FeatureId,
    override val name: String,
    override val version: SemanticVersion,
    override val group: Group? = null,
    override val license: String? = null,
    override val icon: String? = null,
    override val description: String? = null,
    override val links: FeatureLinks? = null,
    override val documentation: String? = null,
    override val prerequisites: List<FeatureReference> = emptyList(),
    override val properties: List<Property> = emptyList(),
    override val repositories: List<Repository> = emptyList(),
    override val dependencies: List<BuildSystemDependency> = emptyList(),
    override val modules: List<Module> = emptyList(),
    val sources: List<SourceTemplateReference> = emptyList(),
): FeatureMetadata

@Serializable
data class FeatureDescriptor(
    override val id: FeatureId,
    override val name: String,
    override val version: SemanticVersion,
    override val group: Group? = null,
    override val license: String? = null,
    override val icon: String? = null,
    override val description: String? = null,
    override val links: FeatureLinks? = null,
    override val documentation: String? = null,
    override val prerequisites: List<FeatureReference> = emptyList(),
    override val properties: List<Property> = emptyList(),
    override val repositories: List<Repository> = emptyList(),
    override val dependencies: List<BuildSystemDependency> = emptyList(),
    override val modules: List<Module> = emptyList(),
    val sources: List<SourceTemplate> = emptyList(),
): FeatureMetadata {
    constructor(manifest: FeatureManifest, sources: List<SourceTemplate>) : this(
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
        manifest.dependencies,
        manifest.modules,
        sources
    )
}

@Serializable
data class Group(
    val id: String,
    val name: String,
    val icon: String? = null,
)

@Serializable
data class FeatureReference(
    val id: FeatureId,
    val version: VersionRange
)

@Serializable
data class FeatureLinks(
    val vcs: String? = null,
    val home: String? = null,
    val docs: String? = null,
)

@Serializable(FeatureIdSerializer::class)
data class FeatureId(val group: String, val id: String) {
    companion object {
        fun parse(text: String) = text.split('/', limit = 2).let { (group, feature) ->
            FeatureId(group, feature)
        }
    }
    override fun toString(): String =
        "$group/$id"
}

@Serializable(SlotIdSerializer::class)
data class SlotId(val feature: FeatureId, val name: String) {
    companion object {
        fun parse(text: String) = text.split('/', limit = 3).let { (group, feature, slot) ->
            SlotId(FeatureId(group, feature), slot)
        }
    }
    val group: String get() = feature.group
    val featureId: String get() = feature.id

    override fun toString(): String =
        "$group/$featureId/$name"
}