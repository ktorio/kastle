package org.jetbrains.kastle

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar
import kotlinx.coroutines.flow.*
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.kastle.StaticSource.Companion.sourceFile
import org.jetbrains.kastle.amper.readDependencies
import org.jetbrains.kastle.amper.readPlatforms
import org.jetbrains.kastle.io.*
import org.jetbrains.kastle.io.resolve
import org.jetbrains.kastle.kotlin.KT_EXTENSION
import org.jetbrains.kastle.kotlin.KT_SCRIPT_EXTENSION
import org.jetbrains.kastle.templates.*
import org.jetbrains.kastle.utils.extension
import org.jetbrains.kastle.utils.protocol
import org.jetbrains.kastle.utils.slotId
import org.jetbrains.kastle.utils.takeIfSlot
import org.jetbrains.kotlin.psi.KtFile
import kotlin.collections.filterNot
import kotlin.random.Random

private const val PACK_YAML = "pack.ksl.yaml"
private const val GROUP_YAML = "group.ksl.yaml"
private const val MODULE_YAML = "module.ksl.yaml"
private const val REPOSITORY_VERSION_CATALOG = "repository.versions.toml"

private val EXPRESSION_PATTERN = Regex("""\$\{([^}]+)}""")

class LocalPackRepository(
    private val root: Path,
    private val fs: FileSystem = SystemFileSystem,
    private val versionsCatalogFile: String = "../gradle/libs.versions.toml",
    random: Random = Random(System.currentTimeMillis()),
    remoteRepository: PackRepository = PackRepository.EMPTY,
): PackRepository {
    private val handlebarsTemplateEngine = HandlebarsTemplateEngine(random)
    private val serializersModule = SerializersModule {
        // TODO this doesn't work for some reason
        polymorphic(SourceFile::class) {
            defaultDeserializer { SourceTemplate.serializer() }
            subclass(StaticSource::class)
            subclass(SourceTemplate::class)
        }
    }
    private val yaml = Yaml(serializersModule)

    constructor(root: String): this(Path(root))
    constructor(root: String, catalogFile: String): this(Path(root), versionsCatalogFile = catalogFile)

    private val repository: PackRepository = object : PackRepository {
        private val metadataCache = mutableMapOf<PackId, PackMetadata>()
        private val fullCache = mutableMapOf<PackId, PackDescriptor>()

        override fun ids(): Flow<PackId> =
            remoteRepository.ids()

        override suspend fun get(packId: PackId): PackMetadata? =
            metadataCache[packId] ?: (this@LocalPackRepository.get(packId) ?: remoteRepository.get(packId))?.also {
                metadataCache[packId] = it
            }

        override suspend fun read(packId: PackId): PackDescriptor? =
            fullCache[packId] ?: (this@LocalPackRepository.read(packId) ?: remoteRepository.read(packId))?.also {
                fullCache[packId] = it
            }

        override suspend fun versions(): VersionsCatalog =
            remoteRepository.versions() + this@LocalPackRepository.versions()
    }

    override fun ids(): Flow<PackId> =
        fs.list(root).flatMap { groupPath ->
            if (!groupPath.isDir())
                return@flatMap emptyList()
            fs.list(groupPath)
        }.asFlow().mapNotNull { path ->
            if (path.isDir() && fs.exists(path.resolve(PACK_YAML)))
                PackId.parse("${path.parent!!.name}/${path.name}")
            else null
        }

    override suspend fun get(packId: PackId): PackMetadata? {
        val projectPath = root.resolve(packId.toString())
        val manifestYaml = projectPath.resolve(PACK_YAML).readYamlNode()?.yamlMap ?: return null
        val filteredYaml = YamlMap(manifestYaml.entries.filterNot { it.key.content == "propertyValues" }, manifestYaml.path)
        val manifest: PackManifest = Yaml.default.decodeFromYamlNode(filteredYaml) ?: return null
        val properties = manifest.properties.toMutableList()
        val group = manifest.group
            ?: projectPath.resolve("../$GROUP_YAML").readYaml()
            ?: Group(packId.group)
        val documentation = projectPath.resolve("README.md").readText()
        val moduleManifests = projectPath.moduleFolders().mapNotNull { modulePath ->
            readSourceModuleManifest(projectPath, modulePath)
        }.toList()

        return manifest.copy(
            id = packId,
            group = group,
            properties = properties.distinctBy { it.key },
            documentation = documentation,
            modules = moduleManifests
        )
    }

    private fun Path.isDir(): Boolean =
        fs.metadataOrNull(this)?.isDirectory == true

    override suspend fun read(packId: PackId): PackDescriptor? {
        val projectPath = root.resolve(packId.toString())
        val rawManifest: YamlMap = projectPath.resolve(PACK_YAML).readYamlNode(fs, yaml)?.yamlMap ?: return null
        val filteredManifest = YamlMap(rawManifest.entries.filterNot { it.key.content == "propertyValues" }, rawManifest.path)
        val manifest: PackManifest = yaml.decodeFromYamlNode(filteredManifest)
        val group = manifest.group
            ?: projectPath.resolve("../$GROUP_YAML").readYaml()
            ?: Group(packId.group)
        val properties = manifest.properties.toMutableList()
        val documentation = projectPath.resolve("README.md").readText()
        val kotlinTemplateEngine = KotlinCompilerTemplateEngine(projectPath, repository)
        val expressionParser = KotlinExpressionParser(kotlinTemplateEngine.psiFileFactory)
        val propertyValues: List<PropertyAssignment> = rawManifest.get<YamlList>("propertyValues")?.items?.map { node ->
            val nodeMap = node.yamlMap
            val key = nodeMap.getScalar("key")?.yamlScalar?.content
            require(key != null) { "Property value key is required: $node" }
            val variableId = VariableId.parse(key, relativePackId = packId)
            val value = nodeMap.getScalar("value")?.yamlScalar?.content
            val expression = nodeMap.getScalar("expression")?.yamlScalar?.content?.let(expressionParser::parse)
            if (value != null) {
                ValueAssignment(variableId, value)
            } else if (expression != null) {
                ExpressionAssignment(variableId, expression)
            } else {
                throw IllegalArgumentException("Property value must contain either value or expression: $node")
            }
        }.orEmpty()

        val projectSources = projectPath.moduleFolders().asFlow()
            .mapNotNull { modulePath ->
                readSourceModule(
                    projectPath,
                    modulePath,
                    packId,
                    properties,
                    expressionParser
                )
            }.toList().let(ProjectModules::fromList)

        val readSource: suspend (SourceDefinition) -> SourceFile = { (path, text, target, condition) ->
            require(target != null) { "Missing target for project-level source: ${path ?: text}" }
            val file = projectPath.resolve(path ?: "source.kt")
            if (!fs.exists(file))
                throw IllegalArgumentException("Missing source file: $file")

            val format = path?.extensionFormat
                ?: target.takeIfSlot()?.getExtensionFromSlot()
                ?: target.extensionFormat
            val conditionExpression = condition?.let(expressionParser::parse)
            val targetExpressions = parseTargetExpressions(expressionParser, target)

            when (format) {
                TemplateFormat.KOTLIN -> {
                    kotlinTemplateEngine.read(
                        path = path?.let(::Path),
                        text = file.readText() ?: text
                    ).copy(
                        packId = packId,
                        target = target,
                        condition = conditionExpression,
                        targetExpressions = targetExpressions
                    )
                }
                TemplateFormat.OTHER ->
                    when (file.name.extension.lowercase()) {
                        "hbs" -> handlebarsTemplateEngine.read(
                            target.removeSuffix(".hbs"),
                            file.readText() ?: text ?: throw IllegalArgumentException("Missing path or text in source definition")
                        ).copy(
                            packId = packId,
                            condition = conditionExpression,
                            targetExpressions = targetExpressions
                        )
                        else -> StaticSource(
                            contents = fs.source(file).buffered().use { it.readByteString() },
                            target = target,
                            condition = conditionExpression,
                            targetExpressions = targetExpressions,
                            packId = packId,
                        )
                    }
            }
        }

        return PackDescriptor(
            manifest = manifest.copy(
                id = packId,
                group = group,
                properties = properties.distinctBy { it.key },
                documentation = documentation,
            ),
            propertyValues = propertyValues,
            sources = PackSources(
                common = manifest.commonSources.map { readSource(it) },
                root = manifest.rootSources.map { readSource(it) },
                modules = projectSources,
            )
        )
    }

    private fun parseTargetExpressions(
        expressionParser: KotlinExpressionParser,
        target: String
    ): List<TargetExpression>? {
        val placeholders = extractExpressionPlaceholders(target)
        if (placeholders.isEmpty()) return null
        return placeholders.map { (placeholder, expressionContent) ->
            TargetExpression(placeholder, expressionParser.parse(expressionContent))
        }
    }

    /**
     * Extracts all ${expression} placeholders from a string.
     * Returns a list of pairs: (full placeholder including ${}, expression content inside).
     *
     * Example: "file:theme/\${themeName}.json" -> [("\${themeName}", "themeName")]
     */
    fun extractExpressionPlaceholders(template: String): List<Pair<String, String>> =
        EXPRESSION_PATTERN.findAll(template).map { match ->
            match.value to match.groupValues[1]
        }.toList()

    private fun readSourceModuleManifest(projectPath: Path, modulePath: Path): SourceModuleManifest? {
        val relativeModulePath = modulePath.relativeTo(projectPath).toString()
        val moduleYaml = modulePath.resolve(MODULE_YAML)
            .readYamlNode(fs, yaml)?.yamlMap
            ?: return null

        return readSourceModuleManifest(moduleYaml, relativeModulePath)
    }

    private fun readSourceModuleManifest(
        moduleYaml: YamlMap,
        relativeModulePath: String
    ): SourceModuleManifest {
        val platforms = moduleYaml.readPlatforms()

        val amperSettings = moduleYaml.get<YamlMap>("amper")?.let { node ->
            yaml.decodeFromYamlNode<AmperSettings>(node)
        }
        val gradleSettings = moduleYaml.get<YamlMap>("gradle")?.let { node ->
            yaml.decodeFromYamlNode<GradleSettings>(node)
        }

        // TODO verify this is correct
        fun readDependencies(dependencies: String): DependenciesMap =
            platforms.singleOrNull()?.let {
                mapOf(it to moduleYaml.readDependencies(dependencies))
            } ?: (platforms.associateWith { platform ->
                moduleYaml.readDependencies("$dependencies@$platform")
            } + (Platform.COMMON to moduleYaml.readDependencies(dependencies)))

        val dependencies = readDependencies("dependencies")
        val testDependencies = readDependencies("testDependencies")

        return SourceModuleManifest(
            path = relativeModulePath,
            platforms = platforms,
            dependencies = dependencies,
            testDependencies = testDependencies,
            gradle = gradleSettings ?: GradleSettings(),
            amper = amperSettings ?: AmperSettings(),
        )
    }

    private suspend fun readSourceModule(
        projectPath: Path,
        modulePath: Path,
        packId: PackId,
        properties: MutableList<PropertyDescriptor>,
        expressionParser: KotlinExpressionParser
    ): SourceModule? {
        val relativeModulePath = modulePath.relativeTo(projectPath).toString()
        val moduleYaml = modulePath.resolve(MODULE_YAML)
            .readYamlNode(fs, yaml)?.yamlMap
            ?: return null
        val manifest = readSourceModuleManifest(moduleYaml, relativeModulePath) ?: return null

        suspend fun readModuleSource(file: Path, target: String? = null): SourceFile =
            when (file.name.extension.lowercase()) {
                HANDLEBARS_EXTENSION -> handlebarsTemplateEngine.read(modulePath, file).let { template ->
                    val actualTarget = (target ?: template.target).removeSuffix(".hbs")
                    template.copy(
                        target = actualTarget,
                        packId = packId,
                        targetExpressions = parseTargetExpressions(expressionParser, actualTarget),
                    )
                }

                KT_EXTENSION, KT_SCRIPT_EXTENSION -> {
                    val kotlinTemplateEngine = KotlinCompilerTemplateEngine(
                        path = file.parent,
                        repository = repository,
                        onProperty = properties::add,
                    )
                    kotlinTemplateEngine.read(file, file.readText()).let { template ->
                        val actualTarget = target ?: template.target
                        template.copy(
                            target = actualTarget,
                            packId = packId,
                            targetExpressions = parseTargetExpressions(expressionParser, actualTarget),
                        )
                    }
                }

                else -> fs.sourceFile(file, modulePath).let { source ->
                    val actualTarget = target ?: source.target
                    source.copy(
                        target = actualTarget,
                        targetExpressions = parseTargetExpressions(expressionParser, actualTarget),
                        packId = packId,
                    )
                }
            }

        val sources = mutableListOf<SourceFile>()
        val resources = mutableListOf<SourceFile>()
        val (sourceFolders, resourceFolders) = getStandardSourceFolders(manifest.platforms, modulePath)

        for (sourceFolder in sourceFolders) {
            if (!fs.exists(sourceFolder))
                continue

            // properties are supplied both from the manifest and from declarations in the source files
            val kotlinTemplateEngine = KotlinCompilerTemplateEngine(
                path = sourceFolder,
                repository = repository,
                onProperty = properties::add,
            )
            sources += kotlinTemplateEngine.ktFiles.map { sourceFile ->
                kotlinTemplateEngine.read(
                    sourceFolder.relativeTo(modulePath).resolvePackageDir(sourceFile),
                    sourceFile,
                ).copy(packId = packId)
            }

            // include non-kotlin files
            sources += fs.walkFiles(sourceFolder).filter { file ->
                !file.name.endsWith(".kt")
            }.asFlow().map(::readModuleSource).toList()
        }

        // resource files included; can be templated
        for (resourceFolder in resourceFolders) {
            if (fs.exists(resourceFolder)) {
                resources += fs.walkFiles(resourceFolder)
                    .asFlow()
                    .map(::readModuleSource)
                    .toList()
            }
        }

        // additional sources defined for module; also assume no kotlin sources
        // TODO remove duplicates from files in source folders
        val sourcesFromManifest = moduleYaml.get<YamlList>("sources")?.items.orEmpty()
        for (manifestSource in sourcesFromManifest) {
            val (path, text, target, condition, priority) = yaml.decodeFromYamlNode<SourceDefinition>(manifestSource)
            val conditionExpression = condition?.let(expressionParser::parse)

            if (path != null && path.contains('*')) {
                require(path.endsWith("/*")) { "Wildcard must be at the end of the path: $path" }
                val wildCardParent = modulePath.resolve(path.removeSuffix("/*"))
                for (file in fs.walkFiles(wildCardParent)) {
                    sources += readModuleSource(
                        file,
                        target = "file:${file.relativeTo(modulePath)}"
                    ).copy(condition = conditionExpression, priority = priority)
                }
            } else {
                sources += if (text == null) {
                    require(path != null) { "Path or text is required but both are missing for source: $manifestSource" }
                    readModuleSource(modulePath.resolve(path), target = target)
                } else {
                    require(target != null) { "Target is required when using text for source: $manifestSource" }
                    val targetExpressions = parseTargetExpressions(expressionParser, target)
                    handlebarsTemplateEngine.read(target.removeSuffix(".hbs"), text).copy(
                        packId = packId,
                        targetExpressions = targetExpressions
                    )
                }.copy(condition = conditionExpression, priority = priority)
            }
        }

        return SourceModule(
            manifest = manifest,
            sources = sources + resources,
        )
    }

    private fun getStandardSourceFolders(
        platforms: Set<Platform>,
        modulePath: Path,
    ): Pair<List<Path>, List<Path>> {
        val (sources, resources) = when {
            platforms.size == 1 -> listOf(listOf("src"), listOf("resources"))
            else -> {
                listOf("src", "resources").map { folder ->
                    buildList {
                        add(folder)
                        platforms.forEach { platform ->
                            add("$folder@$platform")
                        }
                    }
                }
            }
        }
        return sources.map(modulePath::resolve) to resources.let {
            if (Platform.ANDROID in platforms)
                it + "res"
            else it
        }.map(modulePath::resolve)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun versions(): VersionsCatalog {
        val builtInArtifacts =
            fs.list(root).filter {
                it.name.endsWith(".versions.toml") && it.name != REPOSITORY_VERSION_CATALOG
            }.mapNotNull { file ->
                file.readToml<BuiltInToml>(fs)?.libraries
            }.reduceOrNull { left, right -> left + right }.orEmpty()

        val builtInCatalog = VersionsCatalog(
            libraries = builtInArtifacts.mapValues { (_, artifact) ->
                val (group, artifact, version) = artifact
                CatalogArtifact(
                    "$group:$artifact",
                    CatalogVersion.Number(version),
                    builtIn = true
                )
            }
        )

        val libraryCatalog = loadVersionCatalog(versionsCatalogFile)
            ?: error("Failed to read versions catalog from $versionsCatalogFile")
        // TODO: allow ignoring repository version catalogs by Renovate
        val repositoryVersionCatalog = loadVersionCatalog(REPOSITORY_VERSION_CATALOG) ?: VersionsCatalog.Empty

        return builtInCatalog + libraryCatalog + repositoryVersionCatalog
    }

    private fun loadVersionCatalog(catalogPath: String): VersionsCatalog? {
        return root.resolve(catalogPath).readToml<VersionsCatalog>(fs)
    }

    private suspend fun Url.getExtensionFromSlot(): TemplateFormat {
        if (protocol != "slot") return TemplateFormat.OTHER
        val parentUrl = repository.slot(slotId)?.parent
            ?: throw IllegalArgumentException("Slot missing: $this")
        return when(parentUrl.protocol) {
            "file" -> parentUrl.extensionFormat
            "slot" -> parentUrl.getExtensionFromSlot()
            else -> error("Unknown source target protocol: $parentUrl")
        }
    }

    /**
     * Recursively find all modules, based on Amper conventions.
     */
    private fun Path.moduleFolders(): Sequence<Path> =
        if (fs.metadataOrNull(this)?.isDirectory != true)
            emptySequence()
        else if (fs.exists(resolve(MODULE_YAML)))
            sequenceOf(this)
        else fs.list(this).asSequence()
            .flatMap { it.moduleFolders() }

    private fun Path.resolvePackageDir(ktFile: KtFile): Path {
        val packageDir = ktFile.packageFqName.asString().replace(Regex("^kastle\\.?"), "").replace('.', '/')
        if (packageDir.isEmpty()) return this
        return resolve(packageDir)
    }

    @Serializable
    data class BuiltInToml(
        val libraries: Map<String, ArtifactDependency>,
    )
}
