package org.jetbrains.kastle

import kotlinx.serialization.Serializable
import org.jetbrains.kastle.ProjectModules.Multi
import org.jetbrains.kastle.ProjectModules.Single
import org.jetbrains.kastle.ProjectModules.Empty
import org.jetbrains.kastle.utils.protocol
import kotlin.collections.plus


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

    @Serializable
    data object Empty: ProjectModules {
        override val modules: List<SourceModule> = emptyList()
    }
    @Serializable
    data class Single(val module: SourceModule): ProjectModules {
        override val modules: List<SourceModule> = listOf(module)
    }
    @Serializable
    data class Multi(override val modules: List<SourceModule>): ProjectModules
}

operator fun ProjectModules.plus(other: ProjectModules): ProjectModules =
    if (this is Empty) other
    else if (other is Empty) this
    else if (this is Single && other is Single) {
        Single(this.module.tryMerge(other.module)
            ?: throw IllegalArgumentException("Cannot merge modules"))
    }
    else merge(this.modules, other.modules)

fun ProjectModules.map(mapping: (SourceModule) -> SourceModule): ProjectModules =
    when (this) {
        is Empty -> this
        is Single -> Single(mapping(module))
        is Multi -> Multi(modules.map { mapping(it) })
    }

fun ProjectModules.flatten(): ProjectModules =
    when (this) {
        is Empty -> this
        is Single -> Single(module.copy(path = ""))
        is Multi -> {
            val path = modules.first().path
            val slashIndex = path.indexOf('/', 1) // ignore starting slash
            val firstSegment = if (slashIndex == -1) return this else path.substring(0, slashIndex) + '/'
            if (modules.all { it.path.startsWith(firstSegment) })
                Multi(modules.map { it.copy(path = it.path.substring(firstSegment.length)) })
            else this
        }
    }

private fun merge(modules: List<SourceModule>, other: List<SourceModule>): Multi {
    val modules = modules.toMutableList()
    val otherModules = other.toMutableList()
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

@Serializable
data class SourceModule(
    val type: SourceModuleType = SourceModuleType.LIB,
    val path: String = "",
    val platforms: List<String> = emptyList(),
    val dependencies: List<Dependency> = emptyList(),
    val testDependencies: List<Dependency> = emptyList(),
    val sources: List<SourceTemplate> = emptyList(),
    val gradle: GradleSettings = GradleSettings(),
    val amper: AmperSettings = AmperSettings(),
    val ignoreCommon: Boolean = false,
) {
    val gradlePlugins: List<GradlePlugin> get() = gradle.plugins
    val allDependencies: List<Dependency> get() = dependencies + testDependencies
}

@Serializable
data class AmperSettings(
    val compose: String? = null,
)

@Serializable
data class GradleSettings(
    val plugins: List<GradlePlugin> = emptyList(),
)

@Serializable
data class GradlePlugin(
    val id: String,
    val name: String? = null,
    val version: String? = null,
)

enum class SourceModuleType(val code: String) {
    LIB("lib"),
    JVM_APP("jvm/app"),
    ANDROID_APP("android/app"),
    IOS_APP("ios/app");

    companion object {
        val DEFAULT = LIB

        fun parse(text: String) = entries
            .firstOrNull { it.code == text }
            ?: throw IllegalArgumentException("Invalid module type: $text")
    }

    override fun toString(): String = code
}

fun SourceModule.tryMerge(other: SourceModule): SourceModule? {
    return SourceModule(
        type = when {
            other.type == SourceModuleType.DEFAULT || type == other.type -> type
            type == SourceModuleType.DEFAULT -> other.type
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
        gradle = GradleSettings((gradle.plugins + other.gradle.plugins).distinct()),
        amper = AmperSettings(amper.compose ?: other.amper.compose),
    )
}

// TODO catalog references, variables, etc.
@Serializable(DependencySerializer::class)
sealed interface Dependency {
    companion object {
        // TODO make gud
        fun parse(input: String): Dependency {
            val exported = input.endsWith(":exported")
            val text = if (exported) input.substringBeforeLast(":exported") else input
            if (text.startsWith("$"))
                return CatalogReference(text.substring(1), exported = exported)
            if (!text.contains(":"))
                return ModuleDependency(text, exported = exported)

            val segments = text.split(':', limit = 3)
            require(segments.size == 3) { "Invalid dependency string: $text" }
            val (group, artifact, version) = segments
            return ArtifactDependency(group, artifact, version, exported = exported)
        }
    }

    val exported: Boolean
}

@Serializable
data class CatalogReference(
    val key: String,
    val group: String? = null,
    val artifact: String? = null,
    val version: CatalogVersion? = null,
    override val exported: Boolean = false,
): Dependency {
    val lookupKey: String get() =
        key.removePrefix("libs.").replace('.', '-')

    fun resolve(catalog: VersionsCatalog): CatalogReference {
        val library = catalog.libraries[lookupKey] ?: return this
        return copy(
            group = library.module,
            artifact = library.artifact,
            version = library.version,
        )
    }

    override fun toString(): String = buildString {
        append('$')
        append(key)
// TODO artifact
//        if (artifact != null) {
//            append(":")
//            append(artifact)
//        }
        if (exported) append(":exported")
    }
}

@Serializable(ArtifactDependencySerializer::class)
data class ArtifactDependency(
    val group: String,
    val artifact: String,
    val version: String,
    override val exported: Boolean = false,
): Dependency {
    companion object {
        fun parse(text: String): ArtifactDependency {
            val segments = text.split(':', limit = 3)
            require(segments.size == 3) { "Invalid dependency string: $text" }
            val (group, artifact, version) = segments
            return ArtifactDependency(group, artifact, version)
        }
    }

    override fun toString(): String = buildString {
        append("$group:$artifact:$version")
        if (exported) append(":exported")
    }
}

@Serializable
data class ModuleDependency(
    val path: String,
    override val exported: Boolean = false,
): Dependency {
    override fun toString(): String = buildString {
        append(path)
        if (exported) append(":exported")
    }
}

@Serializable
data class VersionsCatalog(
    val versions: Map<String, String> = emptyMap(),
    val libraries: Map<String, CatalogArtifact> = emptyMap(),
) {
    companion object {
        val Empty = VersionsCatalog()
    }

    fun isEmpty() = versions.isEmpty() && libraries.isEmpty()

    operator fun plus(other: VersionsCatalog): VersionsCatalog =
        if (this.isEmpty()) other
        else if (other.isEmpty()) this
        else VersionsCatalog(
            versions = (versions + other.versions).toSortedMap(),
            libraries = (libraries + other.libraries).toSortedMap(),
        )

    operator fun get(key: String): CatalogArtifact? =
        libraries[key]
}

@Serializable
data class CatalogArtifact(
    val module: String,
    val version: CatalogVersion,
    val builtIn: Boolean = false,
) {
    val group: String get() = module.substringBeforeLast(':')
    val artifact: String get() = module.substringAfterLast(':')
}

@Serializable(CatalogVersionSerializer::class)
sealed interface CatalogVersion {

    @Serializable
    data class Ref(val ref: String): CatalogVersion

    @JvmInline
    @Serializable
    value class Number(val number: String): CatalogVersion
}