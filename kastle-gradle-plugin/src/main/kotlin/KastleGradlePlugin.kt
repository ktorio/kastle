package org.jetbrains.kastle

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logging
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import java.io.File

abstract class KastleGradlePlugin : Plugin<Settings> {
    private val logger = Logging.getLogger(KastleGradlePlugin::class.java)

    override fun apply(settings: Settings) {
        // Read repository path from gradle.properties or use default
        val repositoryPath = settings.providers.gradleProperty("kastle.repositoryPath")
            .getOrElse(".")

        val repositoryDir = File(settings.rootDir, repositoryPath)
        if (!repositoryDir.exists() || !repositoryDir.isDirectory) {
            settings.gradle.rootProject {
                logger.warn("Kastle repository path doesn't exist: ${repositoryDir.absolutePath}")
            }
            return
        }
        val catalogPath =
            repositoryDir.resolve("gradle/libs.versions.toml").takeIf { it.exists() }
                ?: repositoryDir.resolve("../gradle/libs.versions.toml")

        val repository = LocalPackRepository(
            root = repositoryDir.absolutePath,
            catalogFile = catalogPath.relativeTo(repositoryDir).path
        )
        val modules2packs = mutableMapOf<String, Pair<PackDescriptor, SourceModule>>()

        // Discover all modules and create subprojects
        runBlocking {
            val packs = repository.all().toList()
            for (pack in packs) {
                for (module in pack.sourceModules) {
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