package org.jetbrains.kastle

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.kastle.structure.relativizePath
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

@Serializable(PackSelectionSerializer::class)
sealed interface PackSelection {
    companion object {
        fun parse(text: String) = when {
            '|' in text -> PackRequirement.parse(text)
            else -> PackId.parse(text)
        }
    }
}

val PackSelection.packId get() = when (this) {
    is PackId -> this
    is PackRequirement -> packId
}

fun PackSelection.asRequirement(): PackRequirement = when(this) {
    is PackId -> PackRequirement(this)
    is PackRequirement -> this
}

@Serializable(PackIdSerializer::class)
data class PackId(val group: String, val id: String): PackSelection {
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
    val modules: Map<String, String>? = null,
): PackSelection {
    private val normalizedModules: Map<String, String>? get() = modules?.ifEmpty { null }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PackRequirement) return false
        return packId == other.packId && normalizedModules == other.normalizedModules
    }

    override fun hashCode(): Int {
        var result = packId.hashCode()
        result = 31 * result + (normalizedModules?.hashCode() ?: 0)
        return result
    }
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

    fun transform(modules: ProjectModules): ProjectModules =
        when(this.modules) {
            null -> modules
            else -> modules.mapNotNull { module ->
                val replacementPath = this.modules[module.path] ?: return@mapNotNull null
                module.copy(
                    manifest = module.manifest.copy(
                        path = remapDependencyPath(
                            dependencyPath = replacementPath,
                            originalModulePath = module.path,
                            newModulePath = replacementPath,
                        ),
                        dependencies = module.dependencies.mapValues { (_, dependencies) ->
                            dependencies.map { dependency ->
                                when (dependency) {
                                    is ModuleDependency -> ModuleDependency(
                                        path = remapDependencyPath(
                                            dependencyPath = dependency.path,
                                            originalModulePath = module.path,
                                            newModulePath = replacementPath,
                                        ),
                                        exported = dependency.exported,
                                        scope = dependency.scope,
                                    )
                                    else -> dependency
                                }
                            }.toSet()
                        }
                    ),
                )
            }
        }

    private fun remapDependencyPath(
        dependencyPath: String,
        originalModulePath: String,
        newModulePath: String,
    ): String {
        val moduleRemap = this.modules ?: return dependencyPath
        if (!dependencyPath.startsWith("..") && !dependencyPath.startsWith("./")) {
            return moduleRemap[dependencyPath] ?: dependencyPath
        }
        val resolved = joinAndNormalize(parentPath(originalModulePath), dependencyPath)
        val remappedTarget = moduleRemap[resolved] ?: return dependencyPath
        val base = parentPath(newModulePath)
        return if (base.isEmpty()) remappedTarget else relativizePath(base, remappedTarget)
    }

    private fun parentPath(path: String): String {
        val idx = path.lastIndexOf('/')
        return if (idx <= 0) "" else path.substring(0, idx)
    }

    private fun joinAndNormalize(base: String, relative: String): String {
        val segments = mutableListOf<String>()
        if (base.isNotEmpty()) segments.addAll(base.split('/').filter { it.isNotEmpty() })
        for (segment in relative.split('/')) {
            when (segment) {
                "", "." -> {}
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments.add(segment)
            }
        }
        return segments.joinToString("/")
    }

    override fun toString(): String =
        buildString {
            append(packId)
            if (!modules.isNullOrEmpty()) {
                append('|')
                append(modules.entries.joinToString(",") { (key, value) ->
                    "$key=$value"
                })
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
