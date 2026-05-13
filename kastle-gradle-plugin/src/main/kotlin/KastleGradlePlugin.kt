package org.jetbrains.kastle

import com.charleskorn.kaml.Yaml
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.di.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import org.gradle.api.Plugin
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logging
import org.jetbrains.kastle.io.FileFormat
import org.jetbrains.kastle.io.FileSystemPackRepository.Companion.export
import org.jetbrains.kastle.io.export
import org.jetbrains.kastle.logging.LogLevel
import org.jetbrains.kastle.server.*
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import java.io.File
import java.net.URI

abstract class KastleGradlePlugin : Plugin<Settings> {
    private val logger = Logging.getLogger(KastleGradlePlugin::class.java)

    override fun apply(settings: Settings) {
        // Read the repository path from gradle.properties or use default
        val repositoryPath = settings.providers.gradleProperty("kastle.repositoryPath")
            .getOrElse(".")

        val repositoryDir = File(settings.rootDir, repositoryPath)
        if (!repositoryDir.exists() || !repositoryDir.isDirectory) {
            settings.gradle.rootProject {
                logger.warn("Kastle repository path doesn't exist: ${repositoryDir.absolutePath}")
            }
            return
        }

        val repository = LocalPackRepository(repositoryDir.absolutePath)
        val modules2packs = mutableMapOf<String, Pair<PackMetadata, SourceModuleMetadata>>()
        val customPluginRepositories = mutableSetOf<MavenRepository>()

        // associate modules and packs, collect repositories
        runBlocking {
            val packs = repository.getAll().toList()
            for (pack in packs) {
                for (module in pack.modules) {
                    val modulePath = module.fullPath(pack.id)
                    val projectRef = pack.id.toProjectRef(module.path)

                    settings.include(projectRef)
                    settings.project(projectRef).apply {
                        projectDir = repositoryDir.resolve(modulePath)
                    }

                    customPluginRepositories += pack.pluginRepositories
                    modules2packs[projectRef] = pack to module
                }
            }
        }

        // include all plugin repositories
        settings.pluginManagement.repositories { repositories ->
            repositories.gradlePluginPortal()
            repositories.mavenCentral()
            repositories.google()

            for (repository in customPluginRepositories) {
                repositories.maven { it.url = URI(repository.url) }
            }
        }

        val versionsCatalog = runBlocking { repository.versions() }

        val conventionPluginIds = generateKastleConventionBuild(
            settings = settings,
            modules2packs = modules2packs,
            versionsCatalog = versionsCatalog,
            customPluginRepositories = customPluginRepositories,
        )

        generateProjectBuildFiles(
            settings = settings,
            conventionPluginIds = conventionPluginIds,
        )

        // Register top-level tasks on the root project
        settings.gradle.rootProject { project ->
            fun doExport(fileFormat: FileFormat) {
                val exportPath = kotlinx.io.files.Path(
                    project.findProperty("exportPath") as? String ?: "export"
                )
                logger.lifecycle("Exporting repository to $exportPath...")
                runBlocking {
                    val export = repository.export(
                        path = exportPath,
                        fileFormat = fileFormat,
                    )
                    val exportedCatalogs = export.catalogs()
                    val alreadyExportedNames = exportedCatalogs.map { it.name }.toSet()
                    val catalogsExtension = project.extensions.getByType(VersionCatalogsExtension::class.java)
                    val catalogNames = catalogsExtension.catalogNames
                    val externalCatalogs = (catalogNames - alreadyExportedNames).map { catalogName ->
                        val gradleCatalog = catalogsExtension.named(catalogName)
                        val pluginAliases = gradleCatalog.pluginAliases.associateWith { alias ->
                            val plugin = gradleCatalog.findPlugin(alias).get().get()
                            PluginArtifact(
                                id = plugin.pluginId,
                                version = CatalogVersion.Number(plugin.version.toString()),
                            )
                        }
                        val versionAliases = gradleCatalog.versionAliases.associateWith { alias ->
                            gradleCatalog.findVersion(alias).get().requiredVersion
                        }.mapKeys { (key) -> key.replace('.', '-') }

                        val libraryAliases = gradleCatalog.libraryAliases.associateWith { alias ->
                            val dependency = gradleCatalog.findLibrary(alias).get().get()
                            CatalogArtifact(
                                module = "${dependency.module.group}:${dependency.module.name}",
                                version = dependency.versionConstraint.requiredVersion.let(CatalogVersion::Number),
                            )
                        }.mapKeys { (key) -> key.replace('.', '-') }

                        VersionsCatalog(
                            name = catalogName,
                            source = VersionsCatalogSource.EXTERNAL,
                            plugins = pluginAliases,
                            versions = versionAliases,
                            libraries = libraryAliases,
                        )
                    }
                    if (externalCatalogs.isNotEmpty()) {
                        export.catalogs(exportedCatalogs + externalCatalogs)
                    }
                    logger.lifecycle("Exported to $exportPath")
                }
            }

            project.tasks.register("kslExportToJson") { task ->
                task.group = "kastle"
                task.description = "Export the repository to JSON format"
                task.doLast { doExport(FileFormat.JSON) }
            }

            project.tasks.register("kslExportToCbor") { task ->
                task.group = "kastle"
                task.description = "Export the repository to CBOR format"
                task.doLast { doExport(FileFormat.CBOR) }
            }

            // TODO input / output args
            project.tasks.register("kslRunProject") { task ->
                task.group = "kastle"
                task.description = "Build a test project from descriptor file project.ksl.yaml"
                task.doLast {
                    val descriptorFile = kotlinx.io.files.Path("project.ksl.yaml") // TODO
                    val exportPath = kotlinx.io.files.Path("build", "project") // TODO
                    logger.lifecycle("Templating to ./build/project...")
                    runBlocking {
                        val fs = SystemFileSystem
                        val projectDescriptor = if (fs.exists(descriptorFile)) {
                            fs.source(descriptorFile).buffered().readString().let {
                                Yaml.default.decodeFromString(ProjectDescriptor.serializer(), it)
                            }
                        } else ProjectDescriptor(
                            name = "project",
                            group = "com.example",
                            packs = repository.ids().toList(),
                        )
                        ProjectGenerator(repository)
                            .generate(projectDescriptor)
                            .export(exportPath, fs)
                    }
                }
            }

            project.tasks.register("kslRunServer") { task ->
                task.group = "kastle"
                task.description = "Start a server with an interactive front end"
                task.doLast {
                    logger.lifecycle("Kastle server running at http://127.0.0.1:2626")
                    logger.lifecycle("Press Ctrl+C to stop the server")
                    logger.lifecycle("For logging, run with --info or --debug")
                    runBlocking {
                        embeddedServer(CIO, port = 2626) {
                            dependencies {
                                provide<PackRepository> {
                                    repository
                                }
                                provide<ProjectGenerator> {
                                    ProjectGenerator(repository)
                                }
                                provide<org.jetbrains.kastle.logging.Logger> {
                                    object : org.jetbrains.kastle.logging.Logger {
                                        override var level: LogLevel = LogLevel.INFO
                                        override fun log(
                                            level: LogLevel,
                                            exception: Throwable?,
                                            message: () -> String
                                        ) {
                                            logger.log(
                                                org.gradle.api.logging.LogLevel.valueOf(level.name),
                                                message(),
                                                exception
                                            )
                                        }
                                    }
                                }
                            }
                            json()
                            routing()
                            serialization()
                            monitoring()
                            errorHandling()
                        }.start(wait = true)
                    }
                }
            }
        }

        settings.gradle.beforeProject { project ->
            if (!project.name.startsWith("ksl-")) return@beforeProject

            val (pack, module) = modules2packs[project.path] ?: return@beforeProject
            project.extraProperties[REPOSITORY_PROPERTY] = repository
            project.extraProperties[PACK_PROPERTY] = pack
            project.extraProperties[SOURCE_MODULE_PROPERTY] = module
            project.extraProperties[VERSIONS_PROPERTY] = versionsCatalog

            project.pluginManager.apply(KastlePackPlugin::class.java)
        }
    }

    private fun generateKastleConventionBuild(
        settings: Settings,
        modules2packs: Map<String, Pair<PackMetadata, SourceModuleMetadata>>,
        versionsCatalog: VersionsCatalog,
        customPluginRepositories: Set<MavenRepository>,
    ): Map<String, String> {
        val conventionsDir = File(settings.rootDir, ".gradle/kastle-conventions")
        val srcDir = File(conventionsDir, "src/main/kotlin/org/jetbrains/kastle/generated")
        srcDir.mkdirs()

        val projectPathToPluginId = mutableMapOf<String, String>()
        val generatedPlugins = mutableListOf<GeneratedConventionPlugin>()

        for ((projectPath, packAndModule) in modules2packs) {
            val (_, module) = packAndModule
            val pluginArtifacts = module.gradlePlugins.mapNotNull { catalogRef ->
                val lookupKey = catalogRef.tomlKey.removePrefix("plugins-")
                val plugin = versionsCatalog.plugins[lookupKey] ?: return@mapNotNull null
                val pluginId = plugin.id
                val versionNumber = when (val version = plugin.version) {
                    is CatalogVersion.Ref -> versionsCatalog.versions[version.ref]
                        ?: error("Missing plugin: ${plugin.id}")
                    is CatalogVersion.Number -> version.number
                }

                GeneratedPluginArtifact(
                    pluginId = pluginId,
                    version = versionNumber,
                    markerArtifact = "$pluginId:$pluginId.gradle.plugin:$versionNumber",
                )
            }

            if (pluginArtifacts.isEmpty()) continue

            val conventionPluginId = "org.jetbrains.kastle.generated.${projectPath.toConventionPluginSlug()}"
            val implementationClass = "${projectPath.toConventionClassName()}ConventionPlugin"

            projectPathToPluginId[projectPath] = conventionPluginId
            generatedPlugins += GeneratedConventionPlugin(
                id = conventionPluginId,
                implementationClass = implementationClass,
                pluginArtifacts = pluginArtifacts,
            )

            File(srcDir, "$implementationClass.kt").writeText(
                buildString {
                    appendLine("package org.jetbrains.kastle.generated")
                    appendLine()
                    appendLine("import org.gradle.api.Plugin")
                    appendLine("import org.gradle.api.Project")
                    appendLine()
                    appendLine("class $implementationClass : Plugin<Project> {")
                    appendLine("    override fun apply(project: Project) {")
                    for (artifact in pluginArtifacts) {
                        appendLine("        project.pluginManager.apply(${artifact.pluginId.quoteKotlinString()})")
                    }
                    appendLine("    }")
                    appendLine("}")
                }
            )
        }

        File(conventionsDir, "settings.gradle.kts").writeText(
            buildString {
                appendLine("pluginManagement {")
                appendLine("    repositories {")
                appendLine("        gradlePluginPortal()")
                appendLine("        mavenCentral()")
                appendLine("        google()")
                for (repository in customPluginRepositories) {
                    appendLine("        maven { url = uri(${repository.url.quoteKotlinString()}) }")
                }
                appendLine("    }")
                appendLine("}")
                appendLine()
                appendLine("dependencyResolutionManagement {")
                appendLine("    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)")
                appendLine("    repositories {")
                appendLine("        gradlePluginPortal()")
                appendLine("        mavenCentral()")
                appendLine("        google()")
                for (repository in customPluginRepositories) {
                    appendLine("        maven { url = uri(${repository.url.quoteKotlinString()}) }")
                }
                appendLine("    }")
                appendLine("}")
                appendLine()
                appendLine("rootProject.name = \"kastle-generated-conventions\"")
            }
        )

        File(conventionsDir, "build.gradle.kts").writeText(
            buildString {
                appendLine("plugins {")
                appendLine("    `kotlin-dsl`")
                appendLine("}")
                appendLine()
                appendLine("dependencies {")

                val markerArtifacts = generatedPlugins
                    .flatMap { it.pluginArtifacts }
                    .map { it.markerArtifact }
                    .distinct()
                    .sorted()

                for (markerArtifact in markerArtifacts) {
                    appendLine("    implementation(${markerArtifact.quoteKotlinString()})")
                }

                appendLine("}")
                appendLine()
                appendLine("gradlePlugin {")
                appendLine("    plugins {")

                for (plugin in generatedPlugins) {
                    val registrationName = plugin.id.toRegistrationName()
                    appendLine("        create(${registrationName.quoteKotlinString()}) {")
                    appendLine("            id = ${plugin.id.quoteKotlinString()}")
                    appendLine("            implementationClass = ${"org.jetbrains.kastle.generated.${plugin.implementationClass}".quoteKotlinString()}")
                    appendLine("        }")
                }

                appendLine("    }")
                appendLine("}")
            }
        )

        if (generatedPlugins.isNotEmpty()) {
            logger.lifecycle("Including generated Kastle conventions build at {}", conventionsDir)
            settings.pluginManagement.includeBuild(conventionsDir.absolutePath)
        }

        return projectPathToPluginId
    }

    private fun generateProjectBuildFiles(
        settings: Settings,
        conventionPluginIds: Map<String, String>,
    ) {
        val generatedBuildScriptsDir = File(settings.rootDir, "build/kastle/generated-build-scripts")
        generatedBuildScriptsDir.mkdirs()

        for ((projectPath, conventionPluginId) in conventionPluginIds) {
            val descriptor = settings.project(projectPath)

            val generatedBuildFile = File(
                generatedBuildScriptsDir,
                "${projectPath.toConventionPluginSlug()}.gradle.kts"
            )

            generatedBuildFile.writeText(
                buildString {
                    appendLine("plugins {")
                    appendLine("    id(${conventionPluginId.quoteKotlinString()})")
                    appendLine("}")
                    appendLine()
                }
            )

            descriptor.buildFileName = descriptor.projectDir
                .toPath()
                .relativize(generatedBuildFile.toPath())
                .toString()
        }
    }

    private data class GeneratedConventionPlugin(
        val id: String,
        val implementationClass: String,
        val pluginArtifacts: List<GeneratedPluginArtifact>,
    )

    private data class GeneratedPluginArtifact(
        val pluginId: String,
        val version: String,
        val markerArtifact: String,
    )

    private fun String.toConventionPluginSlug(): String =
        trim(':')
            .replace(Regex("[^A-Za-z0-9]+"), ".")
            .trim('.')
            .lowercase()

    private fun String.toConventionClassName(): String =
        trim(':')
            .split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }
            .joinToString("") { part ->
                part.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
            .ifBlank { "Root" }

    private fun String.toRegistrationName(): String =
        replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "generatedConventionPlugin" }

    private fun String.quoteKotlinString(): String =
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
