package org.jetbrains.kastle

import kotlinx.serialization.Serializable
import org.jetbrains.kastle.utils.protocol


@Serializable(RevisionSerializer::class)
sealed interface Revision {
    companion object {
        fun parse(text: String) =
            VersionRange.tryParse(text) ?: SemanticVersion.parse(text)
    }
}

@Serializable(VersionRangeSerializer::class)
data class VersionRange(
    val start: SemanticVersion,
    val end: SemanticVersion,
): Revision {
    companion object {
        private val regex = Regex("""^\[(?<from>[^,]+),(?<to>[^)]+)\)$""")

        fun tryParse(text: String): VersionRange? =
            regex.matchEntire(text)?.toRange()

        fun parse(text: String): VersionRange =
            regex.matchEntire(text)?.toRange()
                ?: throw IllegalArgumentException("Invalid version range: $text")

        private fun MatchResult.toRange() =
            VersionRange(
                SemanticVersion.parse(groupValues[1]),
                SemanticVersion.parse(groupValues[2])
            )
    }

    override fun toString(): String =
        "[$start,$end)"
}

@Serializable(SemanticVersionSerializer::class)
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val qualifier: String? = null,
): Revision, Comparable<SemanticVersion> {
    companion object {
        private val semanticVersionRegex = Regex("""^(\d+)\.(\d+)\.(\d+)(-([\w.]+))?${'$'}""")

        fun parse(text: String): SemanticVersion =
            semanticVersionRegex.matchEntire(text)?.destructured?.let { (major, minor, patch, qualifier) ->
                SemanticVersion(major.toInt(), minor.toInt(), patch.toInt(), qualifier.takeIf { it.isNotEmpty() })
            } ?: throw IllegalArgumentException("Invalid semantic version: $text")
    }

    override fun compareTo(other: SemanticVersion): Int {
        val majorCompare = major.compareTo(other.major)
        if (majorCompare != 0) return majorCompare
        val minorCompare = minor.compareTo(other.minor)
        if (minorCompare != 0) return minorCompare
        val patchCompare = patch.compareTo(other.patch)
        if (qualifier == null && other.qualifier == null) return patchCompare
        if (qualifier == null) return 1
        if (other.qualifier == null) return -1
        return 0 // TODO beta, snapshot, etc.
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
sealed interface ProjectModules {
    companion object {
        fun fromList(modules: List<SourceModule>) =
            when (modules.size) {
                0 -> Empty
                1 -> Single(modules.single())
                else -> Multi(modules)
            }
    }

    val modules: List<SourceModule>

    operator fun plus(other: ProjectModules): ProjectModules

    @Serializable
    data object Empty: ProjectModules {
        override val modules: List<SourceModule> = emptyList()
        override fun plus(other: ProjectModules) = other
    }
    @Serializable
    data class Single(val module: SourceModule): ProjectModules {
        override fun plus(other: ProjectModules): ProjectModules =
            when(other) {
                is Empty -> this
                is Single -> module.tryMerge(other.module)?.let(::Single) ?: Multi(listOf(module, other.module))
                is Multi -> Multi(listOf(module) + other.modules)
            }
        override val modules: List<SourceModule> = listOf(module)
    }
    @Serializable
    data class Multi(override val modules: List<SourceModule>): ProjectModules {
        override fun plus(other: ProjectModules): ProjectModules {
            val modules = modules.toMutableList()
            val otherModules = other.modules.toMutableList()
            for (i in modules.indices) {
                for (j in otherModules.indices) {
                    when (val merged = modules[i].tryMerge(otherModules[j])) {
                        null -> {}
                        else -> {
                            modules[i] = merged
                            otherModules.removeAt(j)
                            break
                        }
                    }
                }
            }
            return Multi(modules + otherModules)
        }
    }
}

@Serializable
data class SourceModule(
    val type: SourceModuleType = SourceModuleType.LIB,
    val path: String = "",
    val platforms: List<String> = emptyList(),
    val dependencies: List<Dependency> = emptyList(),
    val testDependencies: List<Dependency> = emptyList(),
    val sources: List<SourceTemplate> = emptyList(),
)

enum class SourceModuleType {
    LIB,
    APP;

    companion object {
        fun parse(text: String) = when {
            text == "lib" -> LIB
            text.endsWith("app") -> APP
            else -> throw IllegalArgumentException("Invalid module type: $text")
        }
    }

    override fun toString(): String =
        when(this) {
            LIB -> "lib"
            APP -> "jvm/app" // TODO
        }
}

fun SourceModule.tryMerge(other: SourceModule): SourceModule? {
    return SourceModule(
        type = when {
            other.type == SourceModuleType.LIB || type == other.type -> type
            type == SourceModuleType.LIB -> other.type
            else -> return null
        },
        path = when {
            other.path.isEmpty() || path == other.path -> path
            path.isEmpty() -> other.path
            else -> return null
        },
        platforms = (platforms + other.platforms).distinct(),
        dependencies = (dependencies + other.dependencies).distinct(),
        testDependencies = (testDependencies + other.testDependencies).distinct(),
        sources = (sources + other.sources).also { mergedSources ->
            val uniquePaths = mutableSetOf<Url>()
            mergedSources.forEach {
                require(it.target.protocol != "file" || uniquePaths.add(it.target)) {
                    "Duplicate target in sources: ${it.target}"
                }
            }
        },
    )
}

// TODO catalog references, variables, etc.
@Serializable(DependencySerializer::class)
sealed interface Dependency {
    companion object {
        // TODO make gud
        fun parse(text: String): Dependency {
            val (group, artifact, version) = text.split(':', limit = 3)
            return ArtifactDependency(group, artifact, version)
        }
    }
}

@Serializable
data class ArtifactDependency(
    val group: String,
    val artifact: String,
    val version: String
): Dependency {
    override fun toString(): String =
        "$group:$artifact:$version"
}

@Serializable
data class ModuleDependency(
    val module: String,
): Dependency {
    override fun toString(): String = module
}