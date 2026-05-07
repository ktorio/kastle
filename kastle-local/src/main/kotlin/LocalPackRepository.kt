package org.jetbrains.kastle

import com.charleskorn.kaml.*
import kotlinx.coroutines.flow.*
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.bytestring.ByteString
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteString
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.kastle.StaticSource.Companion.sourceFile
import org.jetbrains.kastle.VersionsCatalog.Companion.orEmpty
import org.jetbrains.kastle.io.*
import org.jetbrains.kastle.kotlin.KT_EXTENSION
import org.jetbrains.kastle.kotlin.KT_SCRIPT_EXTENSION
import org.jetbrains.kastle.templates.*
import org.jetbrains.kastle.utils.StringExpression
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
private const val DEFAULT_VERSION_CATALOG = "../gradle/libs.versions.toml"
private const val PROPERTY_VALUES = "propertyValues"

class LocalPackRepository(
    private val root: Path,
    private val fs: FileSystem = SystemFileSystem,
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

    private val repository: PackRepository = object : PackRepository {
        private val metadataCache = mutableMapOf<PackId, PackMetadata>()
        private val fullCache = mutableMapOf<PackId, PackDescriptor>()

        override fun ids(): Flow<PackId> =
            remoteRepository.ids()

        override fun groups(): Flow<Group> =
            remoteRepository.groups()

        override suspend fun readFile(path: String): Source? =
            remoteRepository.readFile(path)

        override suspend fun get(packId: PackId): PackMetadata? =
            metadataCache[packId] ?: (this@LocalPackRepository.get(packId) ?: remoteRepository.get(packId))?.also {
                metadataCache[packId] = it
            }

        override suspend fun read(packId: PackId): PackDescriptor? =
            fullCache[packId] ?: (this@LocalPackRepository.read(packId) ?: remoteRepository.read(packId))?.also {
                fullCache[packId] = it
            }

        // TODO merge
        override suspend fun catalogs(): List<VersionsCatalog> =
            remoteRepository.catalogs() + this@LocalPackRepository.catalogs()
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

    override fun groups(): Flow<Group> =
        fs.list(root).asFlow().mapNotNull { dir ->
            if (dir.isDir() && fs.exists(dir.resolve(GROUP_YAML))) {
                // icons must be relative to root
                dir.resolve(GROUP_YAML).readYaml<Group>()?.let { group ->
                    group.copy(icon = group.icon?.let { dir.relativeTo(root).resolve(it).toString() })
                }
            } else null
        }

    override suspend fun get(packId: PackId): PackMetadata? {
        try {
            val projectPath = root.resolve(packId.toString())
            val groupPath = projectPath.parent!!
            val manifestYaml = projectPath.resolve(PACK_YAML).readYamlNode()?.yamlMap ?: return null
            val filteredYaml = YamlMap(manifestYaml.entries.filterNot { it.key.content == PROPERTY_VALUES }, manifestYaml.path)
            val manifest: PackManifest = Yaml.default.decodeFromYamlNode(filteredYaml) ?: return null
            val properties = manifest.properties.toMutableList()
            val group = (manifest.group ?: projectPath.resolve("../$GROUP_YAML").readYaml() ?: Group()).let { group ->
                group.copy(
                    id = packId.group,
                    icon = group.icon?.let { groupPath.resolve(it).relativeTo(root) }?.toString()
                )
            }
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
        } catch (e: Exception) {
            throw PackReadException(packId, e)
        }
    }

    private fun Path.isDir(): Boolean =
        fs.metadataOrNull(this)?.isDirectory == true

    override suspend fun read(packId: PackId): PackDescriptor? {
        try {
            val projectPath = root.resolve(packId.toString())
            val groupPath = projectPath.parent!!
            val rawManifest: YamlMap = projectPath.resolve(PACK_YAML).readYamlNode(fs, yaml)?.yamlMap ?: return null
            val filteredManifest =
                YamlMap(rawManifest.entries.filterNot { it.key.content == PROPERTY_VALUES }, rawManifest.path)
            val manifest: PackManifest = yaml.decodeFromYamlNode(filteredManifest)
            val group = (manifest.group ?: projectPath.resolve("../$GROUP_YAML").readYaml() ?: Group()).let { group ->
                group.copy(
                    id = packId.group,
                    icon = group.icon?.let { groupPath.resolve(it).relativeTo(root) }?.toString()
                )
            }
            val properties = manifest.properties.toMutableList()
            val documentation = projectPath.resolve("README.md").readText()
            val kotlinTemplateEngine = KotlinCompilerTemplateEngine(projectPath)
            val expressionParser = KotlinExpressionParser(kotlinTemplateEngine.psiFileFactory)
            val propertyValues = mutableMapOf(
                PropertyScope.Root to readPropertyValues(rawManifest, packId, expressionParser)
            ) + projectPath.moduleFolders().mapNotNull { modulePath ->
                val relativePath = modulePath.relativeTo(projectPath).toString()
                val moduleYaml = modulePath.resolve(MODULE_YAML)
                    .readYamlNode(fs, yaml)?.yamlMap
                    ?: return@mapNotNull null
                PropertyScope.Module(relativePath) to readPropertyValues(moduleYaml, packId, expressionParser)
            }

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

            val readSource: suspend (SourceDefinition) -> SourceFile = { (path, text, target, condition, priority) ->
                require(target != null) { "Missing target for project-level source: ${path ?: text}" }
                val file = path?.let(projectPath::resolve)
                val targetExpression = expressionParser.parseTemplate(target)
                val conditionExpression = condition?.let(expressionParser::parse)
                val format = path?.extensionFormat
                    ?: targetExpression.takeIfSlot()?.getExtensionFromSlot()
                    ?: target.extensionFormat

                when (format) {
                    TemplateFormat.KOTLIN -> {
                        kotlinTemplateEngine.read(
                            path = path?.let(::Path),
                            text = text ?: file?.takeIf(fs::exists)?.readText() ?: error("Missing source file: $targetExpression")
                        ).copy(
                            packId = packId,
                            target = targetExpression,
                            condition = conditionExpression,
                            priority = priority,
                        )
                    }
                    TemplateFormat.OTHER ->
                        when (file?.name?.extension?.lowercase()) {
                            HANDLEBARS_EXTENSION -> {
                                handlebarsTemplateEngine.read(
                                    targetExpression,
                                    text ?: file.takeIf(fs::exists)?.readText() ?: error("Missing source file: $targetExpression")
                                ).copy(
                                    packId = packId,
                                    target = targetExpression.removeExtension(HANDLEBARS_EXTENSION),
                                    condition = conditionExpression,
                                    priority = priority,
                                )
                            }
                            else -> StaticSource(
                                target = targetExpression,
                                condition = conditionExpression,
                                packId = packId,
                                contents = text?.toByteArray()?.let(::ByteString)
                                    ?: file?.takeIf(fs::exists)?.let { fs.source(file).buffered().use { it.readByteString() } }
                                    ?: error("Missing source file: $targetExpression"),
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
        } catch (e: Exception) {
            throw PackReadException(packId, e)
        }
    }

    private fun readPropertyValues(
        rawManifest: YamlMap,
        packId: PackId,
        expressionParser: KotlinExpressionParser
    ): List<PropertyAssignment> = rawManifest.get<YamlList>(PROPERTY_VALUES)?.items?.map { node ->
        val nodeMap = node.yamlMap
        val key = nodeMap.getScalar("key")?.yamlScalar?.content
        require(key != null) { "Property value key is required: $node" }
        val variableId = VariableId.parse(key, relativePackId = packId)
        val value = nodeMap.getScalar("value")?.yamlScalar?.content
        val expression = nodeMap.getScalar("expression")?.yamlScalar?.content?.let(expressionParser::parse)
        if (value != null) {
            ValueAssignment(packId, variableId, value)
        } else if (expression != null) {
            ExpressionAssignment(packId, variableId, expression)
        } else {
            throw IllegalArgumentException("Property value must contain either value or expression: $node")
        }
    }.orEmpty()

    override suspend fun readFile(path: String): Source? {
        val file = root.resolve(path.trimStart('/'))
        if (!fs.exists(file)) return null
        return fs.source(file).buffered()
    }

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

        fun readDependencies(dependencies: String): DependenciesMap =
            platforms.singleOrNull()?.let { mapOf(it to moduleYaml.readDependencies(dependencies)) }
                ?: (platforms.associateWith { platform -> moduleYaml.readDependencies("$dependencies@$platform") }
                        + (Platform.COMMON to moduleYaml.readDependencies(dependencies)))

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
        val manifest = readSourceModuleManifest(moduleYaml, relativeModulePath)

        suspend fun readModuleSource(file: Path, target: String? = null): SourceFile =
            when (file.name.extension.lowercase()) {
                HANDLEBARS_EXTENSION -> {
                    handlebarsTemplateEngine.read(modulePath, file).let { template ->
                        val targetExpression = target?.let(expressionParser::parseTemplate) ?: template.target
                        template.copy(
                            target = targetExpression.removeExtension(HANDLEBARS_EXTENSION),
                            packId = packId,
                        )
                    }
                }

                KT_EXTENSION, KT_SCRIPT_EXTENSION -> {
                    val kotlinTemplateEngine = KotlinCompilerTemplateEngine(
                        path = file.parent,
                        onProperty = properties::add,
                    )
                    kotlinTemplateEngine.read(file, file.readText()).let { template ->
                        val actualTarget = target?.let(expressionParser::parseTemplate) ?: template.target
                        template.copy(
                            target = actualTarget,
                            packId = packId,
                        )
                    }
                }

                else -> fs.sourceFile(file, modulePath).let { source ->
                    val actualTarget = target?.let(expressionParser::parseTemplate) ?: source.target
                    source.copy(
                        target = actualTarget,
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
                onProperty = properties::add,
            )
            sources += kotlinTemplateEngine.ktFiles.map { sourceFile ->
                kotlinTemplateEngine.read(
                    sourceFolder.relativeTo(modulePath).resolvePackageDir(sourceFile),
                    sourceFile,
                ).copy(packId = packId)
            }

            // include non-kotlin files
            sources += fs.walkFiles(sourceFolder)
                .filter { file -> !file.name.endsWith(".kt") }
                .asFlow()
                .map(::readModuleSource).toList()
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
        val sourcesFromManifest = moduleYaml.get<YamlList>("sources")?.items.orEmpty()
        for (manifestSource in sourcesFromManifest) {
            val (path, text, targetUrl, condition, priority) = yaml.decodeFromYamlNode<SourceDefinition>(manifestSource)
            val conditionExpression = condition?.let(expressionParser::parse)

            if (path != null && path.contains('*')) {
                require(path.endsWith("/*")) { "Wildcard must be at the end of the path: $path" }
                val wildCardParent = modulePath.resolve(path.removeSuffix("/*"))
                for (file in fs.walkFiles(wildCardParent)) {
                    sources += readModuleSource(
                        file,
                        target = "file:${file.relativeTo(modulePath)}"
                    ).copy(
                        condition = conditionExpression,
                        priority = priority
                    )
                }
            } else {
                sources += if (text == null) {
                    require(path != null) { "Path or text is required but both are missing for source: $manifestSource" }
                    readModuleSource(modulePath.resolve(path), target = targetUrl)
                } else {
                    require(targetUrl != null) { "Target is required when using text for source: $manifestSource" }
                    val targetExpression = expressionParser.parseTemplate(targetUrl)
                        .removeExtension(HANDLEBARS_EXTENSION)
                    handlebarsTemplateEngine.read(targetExpression, text)
                        .copy(packId = packId)
                }.copy(
                    condition = conditionExpression,
                    priority = priority
                )
            }
        }

        val moduleCondition = moduleYaml.get<YamlScalar>("if")?.content?.let(expressionParser::parse)

        return SourceModule(
            manifest = manifest,
            sources = sources.dedupeFiles() + resources,
            condition = moduleCondition,
            conditionPackId = if (moduleCondition != null) packId else null,
        )
    }

    /**
     * Allows for overriding details for files under src dir. Preference is given to
     * entries with conditions.
     *
     * Slots are allowed to be duplicate, so they are ignored.
     */
    private fun List<SourceFile>.dedupeFiles(): List<SourceFile> =
        groupBy { it.target }
            .flatMap { (target, files) ->
                when(target.protocol) {
                    "file" -> when(files.size) {
                        1 -> files
                        else -> files.filter { it.condition != null }.ifEmpty {
                            files.subList(files.size - 1, files.size)
                        }
                    }
                    "slot" -> files
                    else -> error("Unknown protocol: ${target.protocol}")
                }
            }

    private fun getStandardSourceFolders(
        platforms: Set<Platform>,
        modulePath: Path,
    ): Pair<List<Path>, List<Path>> {
        val (sources, resources) = when {
            platforms.size == 1 -> listOf(
                listOf("src", "test"),
                listOf("resources", "testResources")
            )
            else -> {
                listOf(
                    "src",
                    "resources",
                    "test",
                    "testResources"
                ).map { folder ->
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

    override suspend fun catalogs(): List<VersionsCatalog> {
        val defaultLibs = loadVersionCatalog(DEFAULT_VERSION_CATALOG)
        val repositoryLibs = loadVersionCatalog(REPOSITORY_VERSION_CATALOG)

        val externalCatalogs = fs.list(root).filter {
            it.name.endsWith(".versions.toml") && it.name != REPOSITORY_VERSION_CATALOG
        }
        return buildList {
            add(defaultLibs.orEmpty() + repositoryLibs.orEmpty())

            // We consider other catalog files as stand-ins for external files
            for (catalogFile in externalCatalogs) {
                val catalog = loadVersionCatalog(catalogFile.toString()) ?: continue
                add(
                    catalog.copy(
                        name = catalogFile.name.substringBefore('.'),
                        source = VersionsCatalogSource.EXTERNAL,
                    )
                )
            }
        }
    }

    private fun loadVersionCatalog(catalogPath: String): VersionsCatalog? {
        return runCatching {
            root.resolve(catalogPath).readToml<VersionsCatalog>(fs)
        }.getOrNull()
    }

    private suspend fun StringExpression.getExtensionFromSlot(): TemplateFormat {
        if (protocol != "slot") return TemplateFormat.OTHER
        val parentUrl = repository.slot(slotId)?.parent
            ?: throw IllegalArgumentException("Slot missing: $this")
        return when(parentUrl.protocol) {
            "file" -> parentUrl.toString().extensionFormat
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
