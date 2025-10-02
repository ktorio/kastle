package org.jetbrains.kastle

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import kotlinx.coroutines.runBlocking
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradleSubplugin
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.*

internal const val REPOSITORY_PROPERTY = "kastle.repository"
internal const val PACK_PROPERTY = "kastle.pack"
internal const val SOURCE_MODULE_PROPERTY = "kastle.sourceModule"
private const val TEMPLATES_ARTIFACT = "org.jetbrains.kastle:kastle-templates:1.0.0-SNAPSHOT"

abstract class KastlePackPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val repository: PackRepository = project.extraProperties[REPOSITORY_PROPERTY] as? PackRepository ?: error { "Repository property is not set" }
        val pack: PackDescriptor = project.extraProperties[PACK_PROPERTY] as? PackDescriptor ?: error { "Pack property is not set" }
        val module: SourceModule = project.extraProperties[SOURCE_MODULE_PROPERTY] as? SourceModule ?: error { "Module property is not set" }
        val versionsCatalog = runBlocking { repository.versions() }

        project.logger.lifecycle("Pack: ${pack.name}")

        project.plugins.apply(KotlinMultiplatformPluginWrapper::class.java)
        if (Platform.ANDROID in module.platforms) {
            project.plugins.apply(LibraryPlugin::class.java)
            project.extensions.configure(LibraryExtension::class.java) { android ->
                android.namespace = pack.id.toString().replace(Regex("\\W+"), ".")
                android.compileSdk = 36
                android.compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_11
                    targetCompatibility = JavaVersion.VERSION_11
                }
            }
        }
        if (module.amper.compose == "enabled")
            project.plugins.apply(ComposeCompilerGradleSubplugin::class.java)

        project.afterEvaluate {
            project.extensions.configure(KotlinMultiplatformExtension::class.java) { kotlinExt ->
                val isSinglePlatform = module.platforms.size == 1
                val platforms =
                    if (isSinglePlatform) listOf(module.platforms.single())
                    else listOf(Platform.COMMON) + module.platforms

                for (platform in platforms) {
                    kotlinExt.configurePlatform(platform)

                    kotlinExt.sourceSets.apply {
                        val platformSourceSet = findByName(platform.kotlinSourceSetName)
                            ?: error { "Missing source set ${platform.kotlinSourceSetName}" }
                        platformSourceSet.apply {
                            if (isSinglePlatform || platform == Platform.COMMON) {
                                kotlin.srcDir("src")
                                resources.srcDir("resources")
                            } else {
                                kotlin.srcDir(platform.srcDir)
                                resources.srcDir(platform.resourcesDir)
                            }

                            project.configureDependencies(repository, this, pack, module, platform, versionsCatalog)
                        }
                    }

                }
            }
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    private fun KotlinMultiplatformExtension.configurePlatform(platform: Platform) {
        when (platform) {
            Platform.COMMON -> {}
            Platform.JVM -> jvm()
            Platform.ANDROID -> androidTarget()
            Platform.WASM -> wasmJs()
            Platform.NATIVE -> linuxX64()
            Platform.IOS -> iosArm64()
        }
    }

    private val Platform.kotlinSourceSetName get() =
        when(this) {
            Platform.COMMON -> "commonMain"
            Platform.JVM -> "jvmMain"
            Platform.ANDROID -> "androidMain"
            Platform.WASM -> "wasmJsMain"
            Platform.NATIVE -> "nativeMain"
            Platform.IOS -> "iosMain"
        }

    private fun Project.configureDependencies(
        repository: PackRepository,
        sourceSet: KotlinSourceSet,
        pack: PackDescriptor,
        module: SourceModule,
        platform: Platform,
        versionsCatalog: VersionsCatalog
    ) {
        // Always include templates
        dependencies.add(sourceSet.implementationConfigurationName, TEMPLATES_ARTIFACT)

        val requiredDependencies = module.dependencies[platform] ?: emptyList()
        val fullModulePath = module.fullPath(pack.id)
        logger.lifecycle("Add {} dependencies to {}", requiredDependencies.size, sourceSet)
        // inter-pack dependencies
        for (packId in pack.requires) {
            try {
                runBlocking {
                    // TODO support direct module references for multi-module packs
                    val module = repository.get(packId)?.sourceModules?.singleOrNull() ?:
                        error("Pack $packId could not be imported; it must be present and only have ONE module")
                    val projectRef = packId.toProjectRef(module.path)
                    dependencies.add(sourceSet.apiConfigurationName, project(projectRef))
                }
            } catch (e: Exception) {
                logger.error("Cannot resolve {}", packId, e)
            }
        }
        // module dependencies
        for (dependency in requiredDependencies) {
            try {
                dependency(sourceSet, dependency, versionsCatalog,  fullModulePath)
            } catch (e: Exception) {
                logger.error("Cannot resolve {}", dependency, e)
            }
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
                val projectRef = dependency.toProjectRef(modulePath)
                dependencies.add(sourceSet.apiConfigurationName, project(projectRef))
            }
            is ArtifactDependency -> {
                val artifact = "${dependency.group}:${dependency.artifact}:${dependency.version}"
                dependencies.add(sourceSet.apiConfigurationName, artifact)
            }
        }
    }
}