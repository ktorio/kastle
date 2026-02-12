package org.jetbrains.kastle

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import kotlinx.coroutines.runBlocking
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradleSubplugin
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmDependency
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmDependency.Scope

internal const val REPOSITORY_PROPERTY = "kastle.repository"
internal const val PACK_PROPERTY = "kastle.pack"
internal const val SOURCE_MODULE_PROPERTY = "kastle.sourceModule"
private const val TEMPLATES_ARTIFACT = "org.jetbrains:kastle-templates:1.0.0-SNAPSHOT"

abstract class KastlePackPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val repository: PackRepository = project.extraProperties[REPOSITORY_PROPERTY] as? PackRepository ?: error("Repository property is not set")
        val pack: PackMetadata = project.extraProperties[PACK_PROPERTY] as? PackMetadata ?: error("Pack property is not set")
        val module: SourceModuleMetadata = project.extraProperties[SOURCE_MODULE_PROPERTY] as? SourceModuleMetadata ?: error("Module property is not set")

        project.logger.lifecycle("Pack: ${pack.name}")

        /**
         * Manually apply plugins based on metadata
         */
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

        /**
         * Include all source sets and dependencies.
         */
        project.afterEvaluate {
            project.extensions.configure(KotlinMultiplatformExtension::class.java) { kotlinExt ->
                val isSinglePlatform = module.platforms.size == 1
                val platforms =
                    if (isSinglePlatform) listOf(module.platforms.single())
                    else listOf(Platform.COMMON) + module.platforms

                for (platform in platforms) {
                    kotlinExt.configurePlatform(platform)

                    kotlinExt.sourceSets.apply {
                        findByName(platform.kotlinSourceSetName)?.apply {
                            // Configure source directories
                            if (isSinglePlatform || platform == Platform.COMMON) {
                                kotlin.srcDir("src")
                                resources.srcDir("resources")
                            } else {
                                kotlin.srcDir(platform.srcDir)
                                resources.srcDir(platform.resourcesDir)
                            }

                            // Always include templates
                            project.dependencies.add(implementationConfigurationName, TEMPLATES_ARTIFACT)

                            val requiredDependencies = module.dependencies[platform] ?: emptyList()
                            val fullModulePath = module.fullPath(pack.id)
                            project.logger.lifecycle("Add {} dependencies to {}", requiredDependencies.size, this)

                            // Inter-pack dependencies
                            for (packId in pack.requires) {
                                try {
                                    // TODO support direct module references for multi-module packs
                                    val module = runBlocking { repository.read(packId) }?.sourceModules?.singleOrNull()
                                    if (module == null) {
                                        project.logger.error("Pack $packId could not be imported; it must be present and only have ONE module")
                                        continue
                                    }
                                    val projectRef = packId.toProjectRef(module.path)
                                    project.dependencies.add(apiConfigurationName, project.project(projectRef))
                                } catch (e: Exception) {
                                    project.logger.error("Cannot resolve {}", packId, e)
                                }
                            }

                            // Explicit artifact dependencies
                            for (dependency in requiredDependencies) {
                                try {
                                    project.dependency(this, dependency, fullModulePath)
                                } catch (e: Exception) {
                                    project.logger.error("Cannot resolve {}", dependency, e)
                                }
                            }
                        } ?: project.logger.error("Missing source set ${platform.kotlinSourceSetName}")
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
            Platform.JS -> js()
            Platform.WEB -> wasmJs()
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
            Platform.IOS -> "iosArm64Main"
            Platform.JS -> "jsMain"
            Platform.WEB -> "wasmJsMain"
        }

    private fun Project.dependency(
        sourceSet: KotlinSourceSet,
        dependency: Dependency,
        modulePath: String
    ) {
        when (dependency) {
            is CatalogReference -> {
                val keys = dependency.key.removePrefix("$").split(".").toMutableList()
                val catalog = extensions.getByType(VersionCatalogsExtension::class.java).named(keys.removeFirst())
                val provider = catalog.findLibrary(keys.joinToString(".")).orElseThrow()
                dependencies.add(sourceSet.apiConfigurationName, provider)
            }
            is ModuleDependency -> {
                val projectRef = dependency.toProjectRef(modulePath)
                dependencies.add(sourceSet.apiConfigurationName, project(projectRef))
            }
            is ArtifactDependency -> {
                val artifact = "${dependency.group}:${dependency.artifact}:${dependency.version}"
                dependencies.add(sourceSet.apiConfigurationName, artifact)
            }
            is FunctionDependency -> {
                when(dependency.functionName) {
                    "npm" -> {
                        val (name, version) = dependency.args
                        dependencies.add(
                            sourceSet.apiConfigurationName,
                            NpmDependency(objects, Scope.NORMAL, name, version)
                        )
                    }
                    else -> error("Unsupported function dependency ${dependency.functionName}")
                }
            }
        }
    }
}