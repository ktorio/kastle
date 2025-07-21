package org.jetbrains.kastle

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.jetbrains.kastle.utils.camelCase
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import javax.inject.Inject
import kotlin.collections.plus
import kotlin.io.path.Path

// Extension class to hold configuration
abstract class KastleExtension @Inject constructor(objects: ObjectFactory) {
    // File path to versions catalog, defaults to libs.versions.toml
    @get:Input
    @get:Optional
    val versionsCatalog: RegularFileProperty = objects.fileProperty()
        .convention(objects.fileProperty().fileValue(java.io.File("gradle/libs.versions.toml")))

    // Input directory for repository
    @get:InputDirectory
    @get:Optional
    val repositoryPath: DirectoryProperty = objects.directoryProperty()
        .convention(objects.directoryProperty().fileValue(java.io.File(".")))

    // Output directory
    @get:OutputDirectory
    @get:Optional
    val outputPath: DirectoryProperty = objects.directoryProperty()
        .convention(objects.directoryProperty().fileValue(java.io.File("build/repository")))
}

private const val TEMPLATES_ARTIFACT = "org.jetbrains.kastle:kastle-templates:1.0.0-SNAPSHOT"

abstract class KastleGradlePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("kastle", KastleExtension::class.java)
        var repository: PackRepository? = null

        project.afterEvaluate {
            repository = resolveModules(project, extension)
        }

        // Register task that uses the extension
        project.tasks.register("buildKastle") { task ->
            task.doLast {
                // Print configuration values for now
                if (repository == null) {
                    project.logger.warn("Repository not initialized!")
                    return@doLast
                }
                project.logger.lifecycle("Kastle Plugin Configuration:")
                project.logger.lifecycle("Versions Catalog: ${extension.versionsCatalog.get().asFile.absolutePath}")
                project.logger.lifecycle("Repository Path: ${extension.repositoryPath.get().asFile.absolutePath}")
                project.logger.lifecycle("Output Path: ${extension.outputPath.get().asFile.absolutePath}")
            }
        }
    }

}

private fun resolveModules(
    project: Project,
    extension: KastleExtension
): PackRepository? {
    val repositoryDir = extension.repositoryPath.get().asFile
    if (!repositoryDir.exists() || !repositoryDir.isDirectory) {
        project.logger.warn("Repository path doesn't exist or is not a directory: ${repositoryDir.absolutePath}")
        return null
    }
    val repository = LocalPackRepository(repositoryDir.absolutePath)
    val kotlinExtension = project.kotlinExtension as? KotlinMultiplatformExtension
        ?: error("Kastle Gradle plugin requires Kotlin Multiplatform plugin")
    val targets = kotlinExtension.targets.associateBy {
        it.platformType.toKastlePlatform()
    }
    // remove existing source sets
    kotlinExtension.sourceSets.forEach { ss ->
        ss.kotlin.srcDirs.clear()
    }

    runBlocking {
        val versionsCatalog = repository.versions()

        val packs = repository.all().toList()
        for (pack in packs) {
            project.logger.info("Pack: ${pack.id}")
            val modulesSortedByDeps = pack.sourceModules.sortedBy {
                it.dependencies.values.flatten()
                    .filterIsInstance<ModuleDependency>()
                    .size
            }
            for (module in modulesSortedByDeps) {
                val modulePath = module.fullPath(pack.id)
                val dir = repositoryDir.resolve(modulePath)
                project.logger.info("  Module $modulePath")

                if (module.platforms.isEmpty()) {
                    project.logger.info("Module $modulePath has no platforms; skip compilation")
                    continue
                }

                val isSinglePlatform = module.platforms.size == 1
                val platforms = if (isSinglePlatform) listOf(module.platforms.single()) else module.platforms + Platform.COMMON

                for (platform in platforms) {
                    // TODO required packs
                    // TODO test sources
                    val sourceSetName = modulePath.let { path ->
                        if (isSinglePlatform || platform == Platform.COMMON) path
                        else "$path/${platform.code}"
                    }.camelCase()

                    project.logger.info("    SourceSet $sourceSetName")

                    val (srcDir, resourcesDir) = if (isSinglePlatform) {
                        "src" to "resources"
                    } else {
                        platform.srcDir to platform.resourcesDir
                    }

                    val sourceSet = kotlinExtension.sourceSets.create(sourceSetName) { sourceSet ->
                        sourceSet.kotlin.srcDir(dir.resolve(srcDir))
                        sourceSet.resources.srcDir(dir.resolve(resourcesDir))
                    }

                    project.includeDependencies(
                        sourceSet,
                        module,
                        platform,
                        versionsCatalog,
                        modulePath
                    )

                    if (platform != Platform.COMMON) {
                        if (Platform.COMMON !in module.platforms) {
                            val commonMain = targets[Platform.COMMON]?.compilations
                                ?.getByName("main")
                                ?.defaultSourceSet
                            if (commonMain != null)
                                sourceSet.dependsOn(commonMain)
                        }
                        val defaultSourceSet = targets[platform]?.compilations
                            ?.getByName("main")
                            ?.defaultSourceSet
                        defaultSourceSet?.dependsOn(sourceSet)
                    }
                }
            }
        }

        // Connect dependencies between packs
        // TODO multiple modules, platforms, etc.
        for (pack in packs) {
            if (pack.requires.isEmpty()) continue
            val module = pack.sourceModules.singleOrNull() ?: continue
            val sourceSetName = module.fullPath(pack.id).camelCase()
            val sourceSet = kotlinExtension.sourceSets.getByName(sourceSetName)
            for (requiredPackId in pack.requires) {
                val requiredPack = repository.get(requiredPackId) ?: continue
                val requiredModule = requiredPack.sourceModules.singleOrNull() ?: continue
                val requiredSourceSetName = requiredModule.fullPath(requiredPack.id).camelCase()
                val requiredSourceSet = kotlinExtension.sourceSets.getByName(requiredSourceSetName)
                sourceSet.dependsOn(requiredSourceSet)
            }
        }
    }

    return repository
}

private fun Project.includeDependencies(
    sourceSet: KotlinSourceSet,
    module: SourceModule,
    platform: Platform,
    versionsCatalog: VersionsCatalog,
    modulePath: String
) {
    // Always include templates
    dependencies.add(sourceSet.implementationConfigurationName, TEMPLATES_ARTIFACT)

    val requiredDependencies = module.dependencies[platform] ?: emptyList()
    val unresolved = mutableSetOf<Dependency>()
    for (dependency in requiredDependencies) {
        try {
            dependency(
                sourceSet,
                dependency,
                versionsCatalog,
                modulePath
            )
        } catch (e: Exception) {
            logger.debug("Cannot resolve {} for {}", dependency, modulePath, e)
            unresolved += dependency
        }
    }

    logger.info("Resolved {} dependencies for {}", requiredDependencies.size, modulePath)
    if (unresolved.isNotEmpty()) {
        logger.error("Missing dependencies for $modulePath:\n  ${unresolved.joinToString("\n  ") { it.toString() }}")
        logger.debug("Catalog libraries:\n  ${versionsCatalog.libraries.keys.joinToString("\n  ")}")
    }
}

private fun Project.dependency(
    sourceSet: KotlinSourceSet,
    dependency: Dependency,
    versionsCatalog: VersionsCatalog,
    modulePath: String
) {
    when (dependency) {
        is CatalogReference -> {
            val artifact = dependency.gradleFormat(versionsCatalog)
                ?: error("Failed to resolve catalog reference: ${dependency.key}")
            dependencies.add(sourceSet.apiConfigurationName, artifact)
        }
        is ModuleDependency -> {
            val requiredSourceSetName = dependency.sourceSet(modulePath)
            val requiredSourceSet = project.kotlinExtension.sourceSets.findByName(requiredSourceSetName)
                ?: error("Source set $requiredSourceSetName not found for module $modulePath")
            sourceSet.dependsOn(requiredSourceSet)
        }
        is ArtifactDependency -> {
            val artifact = "${dependency.group}:${dependency.artifact}:${dependency.version}"
            dependencies.add(sourceSet.apiConfigurationName, artifact)
        }
    }
}

private fun ModuleDependency.sourceSet(modulePath: String) =
    Path(modulePath).resolve(path).normalize().toString().camelCase()

private fun KotlinPlatformType.toKastlePlatform() = when (this) {
    KotlinPlatformType.common -> Platform.COMMON
    KotlinPlatformType.js -> Platform.WASM
    KotlinPlatformType.wasm -> Platform.WASM
    KotlinPlatformType.jvm -> Platform.JVM
    KotlinPlatformType.androidJvm -> Platform.ANDROID
    KotlinPlatformType.native -> Platform.NATIVE
}