package org.jetbrains.kastle

import kotlinx.serialization.Serializable
import org.jetbrains.kastle.ProjectModules.*
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
        return qualifierRank.compareTo(other.qualifierRank)
    }

    private val qualifierRank: Int get() =
        when(qualifier?.lowercase()?.replace(Regex("(\\p{Alpha}).*"), "$1")) {
            "alpha" -> 1
            "beta" -> 2
            "rc" -> 3
            "snapshot" -> 4
            else -> 0
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
    val path: String = "",
    val platforms: Set<Platform> = emptySet(),
    val dependencies: DependenciesMap = emptyMap(),
    val testDependencies: DependenciesMap = emptyMap(),
    val sources: List<SourceFile> = emptyList(),
    val gradle: GradleSettings = GradleSettings(),
    val amper: AmperSettings = AmperSettings(),
) {
    val allDependencies: Set<Dependency> =
        (dependencies.values.flatten() + testDependencies.values.flatten()).toSet()

    val gradlePlugins: List<String> get() = gradle.plugins

    fun fullPath(packId: PackId) = if (path.isEmpty()) packId.toString() else "$packId/$path"
}

typealias DependenciesMap = Map<Platform, Set<Dependency>>

fun DependenciesMap.merge(other: DependenciesMap): DependenciesMap =
    (keys + other.keys).associateWith { platform ->
        this[platform].orEmpty() + other[platform].orEmpty()
    }

enum class Platform(val code: String) {
    COMMON("common"),
    JVM("jvm"),
    ANDROID("android"),
    IOS("ios"),
    WASM("wasmJs"),
    JS("js"),
    WEB("web"),
    NATIVE("native");

    companion object {
        fun parse(text: String): Platform =
            entries.firstOrNull { it.code == text }
                ?: throw IllegalArgumentException("Invalid platform: $text")
    }

    override fun toString(): String = code
}

// Amper convention
val Platform.srcDir get() = when(this) {
    Platform.COMMON -> "src"
    else -> "src@$code"
}

// Amper convention
val Platform.resourcesDir get() = when(this) {
    Platform.COMMON -> "resources"
    else -> "resources@$code"
}

@Serializable
data class AmperSettings(
    val compose: String? = null,
    val application: AmperApplicationSettings? = null,
)

@Serializable
data class AmperApplicationSettings(
    val mainClass: String? = null,
)

@Serializable
data class GradleSettings(
    val plugins: List<String> = emptyList(),
)

@Serializable
data class GradleProjectSettings(
    val plugins: List<GradlePlugin> = emptyList(),
)

@Serializable
data class GradlePlugin(
    val id: String,
    val name: String,
    val version: CatalogVersion,
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
        path = when {
            other.path.isEmpty() || path == other.path -> path
            path.isEmpty() -> other.path
            else -> return null
        },
        platforms = platforms + other.platforms,
        dependencies = dependencies.merge(other.dependencies),
        testDependencies = testDependencies.merge(other.testDependencies),
        sources = (sources + other.sources).also { mergedSources ->
            val uniquePaths = mutableSetOf<Url>()
            mergedSources.forEach {
                require(it.target.protocol != "file" || uniquePaths.add(it.target)) {
                    "Duplicate target in sources: ${it.target}"
                }
            }
        },
        gradle = GradleSettings((gradle.plugins + other.gradle.plugins).distinct()),
        amper = AmperSettings(amper.compose ?: other.amper.compose, amper.application ?: other.amper.application),
    )
}

@Serializable(DependencySerializer::class)
sealed interface Dependency {
    companion object {
        // TODO make gud
        fun parse(input: String): Dependency {
            val exported = input.endsWith("!")
            val text = if (exported) input.dropLast(1) else input
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
    override val exported: Boolean = false,
): Dependency {
    companion object {
        fun lookupFormat(key: String) =
            key.removePrefix("libs.").replace('.', '-')
    }

    override fun toString(): String = buildString {
        append('$')
        append(key)
        if (exported) append("!")
    }
}
val CatalogReference.lookupKey: String get() =
    key.removePrefix("$").removePrefix("libs.").replace('.', '-')

fun CatalogReference.gradleFormat(versionsCatalog: VersionsCatalog): String? {
    val artifact = versionsCatalog.libraries[lookupKey] ?: return null
    val versionNumber = when(artifact.version) {
        is CatalogVersion.Ref -> versionsCatalog.versions[artifact.version.ref] ?: return null
        is CatalogVersion.Number -> artifact.version.number
    }
    return "${artifact.group}:${artifact.artifact}:$versionNumber"
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
        if (exported) append(":!")
    }
}

@Serializable
data class ModuleDependency(
    val path: String,
    override val exported: Boolean = false,
): Dependency {
    override fun toString(): String = buildString {
        append(path)
        if (exported) append("!")
    }
}

@Serializable
data class VersionsCatalog(
    val plugins: Map<String, PluginArtifact> = emptyMap(),
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
            plugins = (plugins + other.plugins).toSortedMap(),
            versions = (versions + other.versions).toSortedMap(),
            libraries = (libraries + other.libraries).toSortedMap(),
        )

    operator fun get(key: String): CatalogArtifact? =
        libraries[key]
}

@Serializable
data class PluginArtifact(
    val id: String,
    val version: CatalogVersion,
)

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
    companion object {
        fun parse(text: String) =
            when {
                text.startsWith('$') -> Ref(text.substring(1))
                else -> Number(text)
            }
    }

    @Serializable
    data class Ref(val ref: String): CatalogVersion {
        override fun toString(): String = "$$ref"
    }

    @JvmInline
    @Serializable
    value class Number(val number: String): CatalogVersion {
        override fun toString(): String = number
    }
}