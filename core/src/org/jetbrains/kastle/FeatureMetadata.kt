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

@Serializable
sealed interface Revision

@Serializable
data class VersionRange(
    val start: SemanticVersion,
    val end: SemanticVersion
): Revision

@Serializable(SemanticVersionSerializer::class)
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val qualifier: String? = null,
): Revision {
    companion object {
        private val semanticVersionRegex = Regex("""^(\d+)\.(\d+)\.(\d+)(-([\w.]+))?${'$'}""")

        fun parse(text: String): SemanticVersion =
            semanticVersionRegex.matchEntire(text)?.destructured?.let { (major, minor, patch, qualifier) ->
                SemanticVersion(major.toInt(), minor.toInt(), patch.toInt(), qualifier.takeIf { it.isNotEmpty() })
            } ?: throw IllegalArgumentException("Invalid semantic version: $text")
    }

    override fun toString(): String =
        "$major.$minor.$patch" + (qualifier?.let { "-$it" } ?: "")
}

@Serializable
data class BuildSystemDependency(
    val group: String,
    val artifact: String,
    val version: Revision,
)

@Serializable
data class Repository(
    val id: String,
    val url: Url
)

@Serializable
data class Property(
    val key: String,
    val type: String,
    val default: String,
    val description: String? = null,
)

@Serializable
data class Module(
    val path: String,
    val description: String,
    val dependencies: List<Dependency>
)

@Serializable
sealed interface Dependency

@Serializable
data class ArtifactDependency(
    val group: String,
    val artifact: String,
    val version: String
): Dependency

@Serializable
data class ModuleDependency(
    val module: String,
): Dependency

@Serializable
data class SourceTemplateReference(
    val path: String,
    val target: Url = "file:$path",
)

@Serializable
sealed interface SourceText {
    val text: String
}

@Serializable
data class SourceTemplate(
    override val text: String,
    val target: Url,
    val imports: List<String>? = null,
    val slots: List<Slot>? = null,
): SourceText

@JvmInline
@Serializable
value class Snippet(
    override val text: String
): SourceText

@Serializable
sealed interface Slot {
    val name: String
    val position: SlotPosition
    val requirement: Requirement
    val block: SourceText?
}

@Serializable(SlotPositionSerializer::class)
sealed interface SlotPosition {
    companion object {
        private val Regex by lazy {
            Regex("(\\w+)\\(([^)]+)\\)")
        }

        fun parse(text: String): SlotPosition {
            val (function, argsString) = Regex.matchEntire(text)?.destructured
                ?: throw IllegalArgumentException("Invalid slot position: $text")
            val args = argsString.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val range = try {
                val (start, end) = args
                IntRange(start.toInt(), end.toInt())
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid slot position: $text", e)
            }
            return when(function) {
                "top" -> TopLevel(range)
                "inline" -> Inline(range, args[2])
                else -> throw IllegalArgumentException("Invalid slot position: $text")
            }
        }
    }

    val range: IntRange

    data class TopLevel(override val range: IntRange): SlotPosition {
        override fun toString(): String = "top(${range.start}, ${range.endInclusive})"
    }

    // TODO multiple receivers, context parameters, scope parameters
    data class Inline(override val range: IntRange, val receiver: String): SlotPosition {
        override fun toString(): String = "inline(${range.start}, ${range.endInclusive}, $receiver)"
    }
}

@Serializable
data class RepeatingSlot(
    override val name: String,
    override val position: SlotPosition,
    override val requirement: Requirement = Requirement.OPTIONAL,
    override val block: SourceText? = null
): Slot

@Serializable
data class NamedSlot(
    override val name: String,
    override val position: SlotPosition,
    override val requirement: Requirement = Requirement.OPTIONAL,
    override val block: SourceText? = null
): Slot

typealias Url = String

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

val Url.protocol: String get() = substringBefore(':')
val Url.afterProtocol: String get() = substringAfter(':')
val Url.slotId: SlotId get() = afterProtocol.split('/').filter {
    it.isNotEmpty()
}.let { (group, feature, slot) -> SlotId(FeatureId(group, feature), slot) }
val Url.extension: String get() = substringAfterLast('.', "").lowercase()

/**
 * Rule for slot requirment
 */
enum class Requirement {
    // Fail when missing
    REQUIRED,
    // Skip when missing
    OMITTED,
    // Ignore
    OPTIONAL,
}