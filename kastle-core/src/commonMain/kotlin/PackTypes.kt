package org.jetbrains.kastle

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.kastle.utils.StringExpression

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
    val requires: List<PackRequirement>
    val properties: List<PropertyDescriptor>
    val repositories: List<MavenRepository>
    val pluginRepositories: List<MavenRepository>
    val modules: List<SourceModuleMetadata>
}

@Serializable
data class PackManifest(
    override val id: PackId = PackId.PLACEHOLDER,
    override val name: String,
    override val version: SemanticVersion = SemanticVersion(1, 0, 0),
    override val group: Group? = null,
    override val tags: List<String> = emptyList(),
    override val license: String? = null,
    override val icon: String? = null,
    override val description: String? = null,
    override val links: PackLinks? = null,
    override val documentation: String? = null,
    override val requires: List<@Contextual PackRequirement> = emptyList(),
    override val properties: List<PropertyDescriptor> = emptyList(),
    override val repositories: List<MavenRepository> = emptyList(),
    override val pluginRepositories: List<MavenRepository> = emptyList(),
    override val modules: List<SourceModuleMetadata> = emptyList(),
    val commonSources: List<SourceDefinition> = emptyList(),
    val rootSources: List<SourceDefinition> = emptyList(),
): PackMetadata

@Serializable
data class PackDescriptor(
    val manifest: PackManifest,
    val propertyValues: PackPropertyAssignments,
    val sources: PackSources,
): PackMetadata by manifest {
    val commonSources: List<SourceFile> get() = sources.common
    val rootSources: List<SourceFile> get() = sources.root
    val sourceModules: List<SourceModule> get() = sources.modules.modules
}

typealias PackPropertyAssignments = Map<PropertyScope, List<PropertyAssignment>>

@Serializable(PropertyScopeSerializer::class)
sealed interface PropertyScope {
    companion object {
        fun parse(text: String): PropertyScope =
            if (text == "(root)")
                Root
            else if (text.startsWith("module:"))
                Module(text.removePrefix("module:"))
            else
                throw IllegalArgumentException("Invalid property scope: $text")
    }


    data object Root: PropertyScope {
        override fun toString(): String = "(root)"
    }
    data class Module(val path: String): PropertyScope {
        override fun toString(): String = "module:$path"
    }
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
    val id: String = "",
    val name: String? = null,
    val icon: String? = null,
    val url: String? = null,
    val email: String? = null,
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
    val parent: StringExpression,
): Slot by slot

@Serializable(PackIdSerializer::class)
data class PackId(val group: String, val id: String) {
    companion object {
        internal val ID_REGEX = Regex("""^[a-z0-9][a-z0-9-.]*[a-z0-9]$""")
        // used during parsing
        internal val PLACEHOLDER = PackId("null", "null")

        fun parse(text: String) = text.split('/', limit = 2).let { split ->
            require(split.size == 2) { "Invalid pack id: $text" }
            val (group, pack) = split
            PackId(group, pack)
        }
    }
    init {
        require(group.matches(ID_REGEX)) { "Invalid group id: $group" }
        require(id.matches(ID_REGEX)) { "Invalid pack id: $id" }
    }

    override fun toString(): String =
        "$group/$id"
}


@Serializable(PackRequirementStringSerializer::class)
data class PackRequirement(
    val packId: PackId,
    val modules: Map<String, String> = emptyMap(),
) {
    companion object {
        /**
         * This format is not used in the manifests, only for later serialization.
         */
        fun parse(input: String): PackRequirement {
            val split = input.split('|', limit = 2)
            return when(split.size) {
                1 -> PackRequirement(packId = PackId.parse(split[0].trim()))
                2 -> {
                    val (packIdString, modulesString) = split
                    PackRequirement(
                        packId = PackId.parse(packIdString.trim()),
                        modules = modulesString.split(',').associate { term ->
                            val (key, value) = term.split('=', limit = 2)
                            key.trim() to value.trim()
                        }
                    )
                }
                else -> error("Invalid pack requirement: $input")
            }
        }
    }

    override fun toString(): String =
        buildString {
            append(packId)
            if (modules.isNotEmpty()) {
                append('|')
                append(modules.entries.joinToString(",") { (key, value) -> "$key=$value" })
            }
        }

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

    /**
     * For populating variable scopes, we remove the packId when working inside pack templates.
     */
    fun relativeString(currentPackId: PackId): String =
        when(packId) {
            currentPackId -> name
            else -> toString()
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
