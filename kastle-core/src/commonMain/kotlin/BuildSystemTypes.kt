package org.jetbrains.kastle

import kotlinx.serialization.Serializable
import org.jetbrains.kastle.Dependency.Companion.DEFAULT_SCOPE
import org.jetbrains.kastle.Dependency.Companion.EXPORTED_SCOPE
import org.jetbrains.kastle.Dependency.Companion.parseScope
import org.jetbrains.kastle.ProjectModules.*
import org.jetbrains.kastle.utils.Expression
import org.jetbrains.kastle.utils.TreeMap.Companion.toTreeMap
import org.jetbrains.kastle.utils.unwrapQuotes
import org.jetbrains.kastle.utils.wrapQuotes
import kotlin.jvm.JvmInline

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
        private val semanticVersionRegex = Regex("""^(\d+)\.(\d+)\.(\d+)(-([\w.]+))?$""")

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
data class MavenRepository(
    val id: String,
    val url: Url,
    val gradleFunction: String? = null,
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

    fun map(mapping: (SourceModule) -> SourceModule): ProjectModules =
        when (this) {
            is Empty -> this
            is Single -> Single(mapping(module))
            is Multi -> Multi(modules.map { mapping(it) })
        }

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
        this.module.tryMerge(other.module)?.let(::Single)
            ?: Multi(listOf(this.module, other.module))
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
        is Single -> Single(
            module.copy(manifest = module.manifest.copy(path = ""))
        )
        is Multi -> {
            val path = modules.first().path
            val slashIndex = path.indexOf('/', 1) // ignore starting slash
            val firstSegment = if (slashIndex == -1) return this else path.substring(0, slashIndex) + '/'
            if (modules.all { it.path.startsWith(firstSegment) })
                Multi(modules.map { module ->
                    module.copy(
                        manifest = module.manifest.copy(
                            path = module.path.substring(firstSegment.length)
                        )
                    )
                })
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
sealed interface SourceModuleMetadata {
    /**
     * Path to the module in the resulting project.
     */
    val path: String

    /**
     * Module path before structural changes (i.e., flattening).
     */
    val originalPath: String

    /**
     * Platforms that this module supports.
     */
    val platforms: Set<Platform>

    /**
     * Gradle dependencies for the module; either imported or module references.
     */
    val dependencies: DependenciesMap

    /**
     * Gradle dependencies for the module's tests; either imported or module references.
     */
    val testDependencies: DependenciesMap

    /**
     * Gradle-specific settings, like plugins.
     */
    val gradle: GradleSettings

    /**
     * Amper-specific settings.
     */
    @Deprecated("use toolchain", ReplaceWith("toolchain"))
    val amper: AmperSettings get() = toolchain

    /**
     * Amper-specific settings.
     */
    val toolchain: AmperSettings
}

@Serializable
data class SourceModuleManifest(
    override val path: String = "",
    override val originalPath: String = path,
    override val platforms: Set<Platform> = emptySet(),
    override val dependencies: DependenciesMap = emptyMap(),
    override val testDependencies: DependenciesMap = emptyMap(),
    override val gradle: GradleSettings = GradleSettings(),
    override val toolchain: ToolchainSettings = ToolchainSettings(),
): SourceModuleMetadata

val SourceModuleMetadata.allDependencies: Set<Dependency> get() =
    (dependencies.values.flatten() + testDependencies.values.flatten()).toSet()

val SourceModuleMetadata.gradlePlugins: List<CatalogReference> get() =
    gradle.plugins

fun SourceModuleMetadata.fullPath(packId: PackId) =
    if (path.isEmpty()) packId.toString() else "$packId/$path"

@Serializable
data class Condition(
    val expression: Expression,
    val packId: PackId,
)

@Serializable
data class SourceModule(
    val manifest: SourceModuleManifest = SourceModuleManifest(),
    val sources: List<SourceFile> = emptyList(),
    val condition: Condition? = null,
): SourceModuleMetadata by manifest

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

@Deprecated("use ToolchainSettings", ReplaceWith("ToolchainSettings"))
typealias AmperSettings = ToolchainSettings

@Serializable
data class ToolchainSettings(
    val compose: String? = null,
    val ktor: String? = null,
    val application: ToolchainApplicationSettings? = null,
    val kotlin: ToolchainKotlinSettings? = null,
) {
    fun isEmpty() = compose == null && ktor == null && application == null && kotlin == null
    fun isNotEmpty() = !isEmpty()
}

@Serializable
data class ToolchainApplicationSettings(
    val mainClass: String? = null,
)

@Serializable
data class ToolchainKotlinSettings(
    val serialization: String? = null,
)

@Serializable
data class GradleSettings(
    val plugins: List<CatalogReference> = emptyList(),
)

@Serializable
data class GradleProjectSettings(
    val repositories: List<MavenRepository> = emptyList(),
    val pluginRepositories: List<MavenRepository> = emptyList(),
    val plugins: List<GradlePlugin> = emptyList(),
)

@Serializable
data class GradlePlugin(
    val id: String,
    val name: String,
    val catalogKey: String,
    val version: CatalogVersion,
)

enum class SourceModuleType(val code: String) {
    LIB("lib"),
    JVM_APP("jvm/app"),
    ANDROID_APP("android/app"),
    IOS_APP("ios/app");

    companion object {
        fun parse(text: String) = entries
            .firstOrNull { it.code == text }
            ?: throw IllegalArgumentException("Invalid module type: $text")
    }

    override fun toString(): String = code
}

fun SourceModule.tryMerge(other: SourceModule): SourceModule? {
    return manifest.tryMerge(other.manifest)?.let { manifest ->
        SourceModule(
            manifest = manifest,
            sources = sources + other.sources,
            condition = condition ?: other.condition, // TODO: merge conditions with logical AND
        )
    }
}

fun SourceModuleManifest.tryMerge(other: SourceModuleManifest): SourceModuleManifest? {
    return SourceModuleManifest(
        path = when {
            other.path.isEmpty() || path == other.path -> path
            path.isEmpty() -> other.path
            else -> return null
        },
        platforms = platforms.intersect(other.platforms),
        dependencies = dependencies.merge(other.dependencies),
        testDependencies = testDependencies.merge(other.testDependencies),
        gradle = GradleSettings((gradle.plugins + other.gradle.plugins).distinct()),
        toolchain = AmperSettings(
            toolchain.compose ?: other.toolchain.compose,
            toolchain.ktor ?: other.toolchain.ktor,
            toolchain.application ?: other.toolchain.application,
            toolchain.kotlin ?: other.toolchain.kotlin,
        ),
    )
}

@Serializable(DependencySerializer::class)
sealed interface Dependency {
    companion object {
        const val DEFAULT_SCOPE = "implementation"
        const val EXPORTED_SCOPE = "api"

        fun parseScope(input: String): String = when (val idx = input.indexOf('!')) {
            -1 -> DEFAULT_SCOPE
            input.lastIndex -> EXPORTED_SCOPE
            else -> input.substring(idx + 1)
        }

        fun parse(input: String): Dependency {
            ReferenceDependency.tryParse(input)?.let { referenceDependency ->
                return referenceDependency
            }
            FunctionDependency.tryParse(input)?.let { functionDependency ->
                return functionDependency
            }
            if (input.startsWith("$"))
                return CatalogReference.parse(input)
            if (!input.contains(":"))
                return ModuleDependency.parse(input)

            return ArtifactDependency.parse(input)
        }
    }

    val scope: String

    @Deprecated("Use scope == Dependency.EXPORTED_SCOPE instead.", ReplaceWith("scope == Dependency.EXPORTED_SCOPE"))
    val exported: Boolean
}

context(dependency: Dependency)
private fun StringBuilder.appendScope(): StringBuilder = when (val scope = dependency.scope) {
    DEFAULT_SCOPE -> ""
    EXPORTED_SCOPE -> "!"
    else -> "!$scope"
}.let(::append)

@Serializable(CatalogReferenceSerializer::class)
data class CatalogReference(
    val key: String,
    @Deprecated("Use scope == Dependency.EXPORTED_SCOPE instead.", ReplaceWith("scope == Dependency.EXPORTED_SCOPE"))
    override val exported: Boolean = false,
    override val scope: String = if (exported) EXPORTED_SCOPE else DEFAULT_SCOPE,
): Dependency {
    constructor(
        key: String,
        exported: Boolean = false,
    ): this(
        key = key,
        exported = exported,
        scope = if (exported) EXPORTED_SCOPE else DEFAULT_SCOPE,
    )

    companion object {
        fun parse(text: String): CatalogReference {
            val scope = parseScope(text)
            val text = text.substringBefore('!')
            return CatalogReference(
                key = text.trimStart('$'),
                exported = scope == EXPORTED_SCOPE,
                scope = scope,
            )
        }
    }

    val catalog get() = key.substringBefore('.')
    val keyInCatalog get() = key.removePrefix("$catalog.").removePrefix("plugins.")
    val tomlKey: String get() = keyInCatalog.replace('.', '-')

    @Suppress("DEPRECATION")
    fun copy(
        key: String = this.key,
        exported: Boolean = this.exported,
    ): CatalogReference = CatalogReference(key, exported)

    override fun toString(): String = buildString {
        append('$')
        append(key)
        appendScope()
    }
}

fun CatalogReference.gradleFormat(versionsCatalog: VersionsCatalog): String? {
    val artifact = versionsCatalog.libraries[tomlKey] ?: return null
    val versionNumber = when (artifact.version) {
        is CatalogVersion.Ref -> versionsCatalog.versions[artifact.version.ref] ?: return null
        is CatalogVersion.Number -> artifact.version.number
    }
    return "${artifact.group}:${artifact.name}:$versionNumber"
}


@Serializable(ArtifactDependencySerializer::class)
data class ArtifactDependency(
    val group: String,
    val artifact: String,
    val version: String,
    @Deprecated("Use scope == Dependency.EXPORTED_SCOPE instead.", ReplaceWith("scope == Dependency.EXPORTED_SCOPE"))
    override val exported: Boolean = false,
    override val scope: String = if (exported) EXPORTED_SCOPE else DEFAULT_SCOPE,
): Dependency {
    constructor(
        group: String,
        artifact: String,
        version: String,
        exported: Boolean = false,
    ): this(
        group = group,
        artifact = artifact,
        version = version,
        exported = exported,
        scope = if (exported) EXPORTED_SCOPE else DEFAULT_SCOPE,
    )

    companion object {
        fun parse(text: String): ArtifactDependency {
            val scope = parseScope(text)
            val text = text.substringBefore('!')
            val segments = text.split(':', limit = 3)
            require(segments.size == 3) { "Invalid dependency string: $text" }
            val (group, artifact, version) = segments
            return ArtifactDependency(group, artifact, version, exported = scope == EXPORTED_SCOPE, scope = scope)
        }
    }

    @Suppress("DEPRECATION")
    fun copy(
        group: String = this.group,
        artifact: String = this.artifact,
        version: String = this.version,
        exported: Boolean = this.exported,
    ): ArtifactDependency = ArtifactDependency(group, artifact, version, exported)

    override fun toString(): String = buildString {
        append("$group:$artifact:$version")
        appendScope()
    }
}

@Serializable
data class FunctionDependency(
    val functionName: String,
    val args: List<String> = emptyList(),
    @Deprecated("Use scope == Dependency.EXPORTED_SCOPE instead.", ReplaceWith("scope == Dependency.EXPORTED_SCOPE"))
    override val exported: Boolean = false,
    override val scope: String = if (exported) EXPORTED_SCOPE else DEFAULT_SCOPE,
): Dependency {
    constructor(
        functionName: String,
        args: List<String> = emptyList(),
    ): this(
        functionName = functionName,
        args = args,
        exported = false,
        scope = DEFAULT_SCOPE,
    )

    companion object {
        private val functionPattern = Regex("""^([a-zA-Z_]\w*)(?:\((.*)\))?$""")

        fun tryParse(text: String): FunctionDependency? {
            val scope = parseScope(text)
            val text = text.substringBefore('!')
            val match = functionPattern.matchEntire(text) ?: return null
            val (functionName, args) = match.destructured
            return FunctionDependency(
                functionName,
                args.split(',').map { it.trim().unwrapQuotes() },
                exported = scope == EXPORTED_SCOPE,
                scope = scope
            )
        }
    }

    fun copy(
        functionName: String = this.functionName,
        args: List<String> = this.args,
    ): FunctionDependency = FunctionDependency(functionName, args)

    override fun toString(): String = buildString {
        append(functionName)
        append("(")
        if (args.isNotEmpty())
            append(args.joinToString(", ") { it.wrapQuotes() })
        append(")")
        appendScope()
    }
}

@Serializable
data class ReferenceDependency(
    val reference: String,
    @Deprecated("Use scope == Dependency.EXPORTED_SCOPE instead.", ReplaceWith("scope == Dependency.EXPORTED_SCOPE"))
    override val exported: Boolean = false,
    override val scope: String = if (exported) EXPORTED_SCOPE else DEFAULT_SCOPE,
): Dependency {
    companion object {
        private val referencePattern = Regex("""^([a-zA-Z_]\w*)(\.([a-zA-Z_]\w*))*$""")

        fun tryParse(text: String): ReferenceDependency? {
            val scope = parseScope(text)
            val text = text.substringBefore('!')
            if (!referencePattern.matches(text)) return null
            return ReferenceDependency(text, exported = scope == EXPORTED_SCOPE, scope = scope)
        }
    }

    override fun toString(): String = buildString {
        append(reference)
        appendScope()
    }
}

@Serializable
data class ModuleDependency(
    val path: String,
    @Deprecated("Use scope == Dependency.EXPORTED_SCOPE instead.", ReplaceWith("scope == Dependency.EXPORTED_SCOPE"))
    override val exported: Boolean = false,
    override val scope: String = if (exported) EXPORTED_SCOPE else DEFAULT_SCOPE,
): Dependency {
    constructor(
        path: String,
        exported: Boolean,
    ): this(
        path = path,
        exported = exported,
        scope = if (exported) EXPORTED_SCOPE else DEFAULT_SCOPE,
    )

    companion object {
        fun parse(text: String): ModuleDependency {
            val scope = parseScope(text)
            val text = text.substringBefore('!')
            return ModuleDependency(
                path = text,
                exported = scope == EXPORTED_SCOPE,
                scope = scope,
            )
        }
    }

    @Suppress("DEPRECATION")
    fun copy(
        path: String = this.path,
        exported: Boolean = this.exported,
    ): ModuleDependency = ModuleDependency(path, exported)

    override fun toString(): String = buildString {
        append(path)
        appendScope()
    }
}

@Serializable
data class VersionsCatalog(
    val name: String = DEFAULT_NAME,
    val source: VersionsCatalogSource = VersionsCatalogSource.FILE,
    val plugins: Map<String, PluginArtifact> = emptyMap(),
    val versions: Map<String, String> = emptyMap(),
    val libraries: Map<String, CatalogArtifact> = emptyMap(),
) {
    companion object {
        const val DEFAULT_NAME = "libs"
        val Empty = VersionsCatalog()

        fun VersionsCatalog?.orEmpty() = this ?: Empty
    }
    init {
        require(name.isNotEmpty()) { "Versions catalog name cannot be empty" }
        require(name.all { it.isLetter() }) { "Versions catalog name must contain only letters" }
    }

    fun isEmpty() = versions.isEmpty() && libraries.isEmpty()

    operator fun plus(other: VersionsCatalog): VersionsCatalog =
        if (this.isEmpty()) other
        else if (other.isEmpty()) this
        else VersionsCatalog(
            name = name,
            source = source,
            plugins = (plugins + other.plugins).toTreeMap(),
            versions = (versions + other.versions).toTreeMap(),
            libraries = (libraries + other.libraries).toTreeMap(),
        )

    operator fun get(key: String): CatalogArtifact? =
        libraries[key]
}

@Serializable
enum class VersionsCatalogSource {
    /**
     * From a toml file in the repository.
     */
    FILE,
    /**
     * Imported from maven or some other source.
     */
    EXTERNAL
}

@Serializable
data class PluginArtifact(
    val id: String,
    val version: CatalogVersion,
)

@Serializable(with = CatalogArtifactSerializer::class)
data class CatalogArtifact(
    val module: String,
    val version: CatalogVersion,
    val builtIn: Boolean = false,
) {
    val group: String get() = module.substringBefore(':')
    val name: String get() = module.substringAfter(':')
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
