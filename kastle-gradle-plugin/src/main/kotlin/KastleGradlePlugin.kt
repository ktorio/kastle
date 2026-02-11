package org.jetbrains.kastle

import com.charleskorn.kaml.Yaml
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logging
import org.jetbrains.kastle.io.FileFormat
import org.jetbrains.kastle.io.FileSystemPackRepository.Companion.export
import org.jetbrains.kastle.io.export
import org.jetbrains.kastle.logging.LogLevel
import org.jetbrains.kastle.server.errorHandling
import org.jetbrains.kastle.server.json
import org.jetbrains.kastle.server.monitoring
import org.jetbrains.kastle.server.routing
import org.jetbrains.kastle.server.serialization
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import java.io.File
import java.nio.file.Paths

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

        // Discover all modules and create subprojects
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

                    modules2packs[projectRef] = pack to module
                }
            }
        }

        // Register top-level tasks on the root project
        settings.gradle.rootProject { project ->
            project.tasks.register("kslExportToJson") { task ->
                task.group = "kastle"
                task.description = "Export the repository to JSON format"
                task.doLast {
                    val exportPath = kotlinx.io.files.Path(
                        project.findProperty("exportPath") as? String ?: "export"
                    )
                    runBlocking {
                        repository.export(
                            path = exportPath,
                            fileFormat = FileFormat.JSON,
                        )
                        logger.lifecycle("Exported to $exportPath")
                    }
                }
            }

            project.tasks.register("kslExportToCbor") { task ->
                task.group = "kastle"
                task.description = "Export the repository to CBOR format"
                task.doLast {
                    val exportPath = kotlinx.io.files.Path(
                        project.findProperty("exportPath") as? String ?: "export"
                    )
                    logger.lifecycle("Exporting repository to $exportPath...")
                    runBlocking {
                        repository.export(
                            path = exportPath,
                            fileFormat = FileFormat.CBOR,
                        )
                        logger.lifecycle("Export done.")
                    }
                }
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
            if (project.name.startsWith("ksl-")) {
                val (pack, module) = modules2packs[project.path] ?: return@beforeProject
                project.extraProperties[REPOSITORY_PROPERTY] = repository
                project.extraProperties[PACK_PROPERTY] = pack
                project.extraProperties[SOURCE_MODULE_PROPERTY] = module

                project.pluginManager.apply(KastlePackPlugin::class.java)
            }
        }
    }
}