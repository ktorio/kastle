package org.jetbrains.kastle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import kotlinx.coroutines.runBlocking
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmDependency
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmDependency.Scope

internal const val REPOSITORY_PROPERTY = "kastle.repository"
internal const val PACK_PROPERTY = "kastle.pack"
internal const val SOURCE_MODULE_PROPERTY = "kastle.sourceModule"
internal const val VERSIONS_PROPERTY = "kastle.versions"

private const val TEMPLATES_ARTIFACT = "org.jetbrains:kastle-templates:1.0.0-SNAPSHOT"

abstract class KastlePackPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val repository: PackRepository = project.extraProperties[REPOSITORY_PROPERTY] as? PackRepository ?: error("Repository property is not set")
        val pack: PackMetadata = project.extraProperties[PACK_PROPERTY] as? PackMetadata ?: error("Pack property is not set")
        val module: SourceModuleMetadata = project.extraProperties[SOURCE_MODULE_PROPERTY] as? SourceModuleMetadata ?: error("Module property is not set")
        val versions: VersionsCatalog = project.extraProperties[VERSIONS_PROPERTY] as? VersionsCatalog ?: error("Module property is not set")

        project.logger.lifecycle("Pack: ${pack.name}")

        when(module.buildStrategy) {
            ModuleBuildStrategy.JVM -> {
                configureKotlinJvm(project, module, pack, repository)
                applyCustomPlugins(module, project, pack, versions)
            }
            ModuleBuildStrategy.ANDROID_APP -> {
                configureAndroidApp(project, module, pack, repository)
                applyCustomPlugins(module, project, pack, versions)
            }
            ModuleBuildStrategy.KMP -> {
                // IMPORTANT: apply Android/KMP-related plugins BEFORE configuring KMP targets.
                // This prevents "compileSdk version is not set" when using com.android.kotlin.multiplatform.library
                configureKotlinMultiplatform(project, module, pack, repository)
                applyCustomPlugins(module, project, pack, versions)
            }
        }
    }

    private fun applyCustomPlugins(
        module: SourceModuleMetadata,
        project: Project,
        pack: PackMetadata,
        versionsCatalog: VersionsCatalog,
    ) {
        for (pluginAlias in module.gradlePlugins) {
            try {
                val lookupKey = pluginAlias.tomlKey.removePrefix("plugins-")
                val (pluginId, _) = versionsCatalog.plugins[lookupKey] ?: continue
                when (pluginId) {
                    // TODO get sdk versions from catalog
                    "com.android.application" -> {
                        project.plugins.apply(pluginId)
                        project.extensions.configure(ApplicationExtension::class.java) { app ->
                            app.namespace = pack.id.toString().replace(Regex("\\W+"), ".")
                            app.compileSdk { version = release(36) }
                        }
                    }

                    "com.android.kotlin.multiplatform.library" -> {
                        project.plugins.apply(pluginId)

                        // Configure the SDK where the plugin actually expects it: kotlin { android { ... } }
                        project.pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
                            project.extensions.configure(KotlinMultiplatformExtension::class.java) { kotlinExt ->
                                kotlinExt.extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("androidLibrary") { target ->
                                    target.namespace = pack.id.toString().replace(Regex("\\W+"), ".")
                                    target.compileSdk { version = release(36) }
                                    target.minSdk = 21
                                }
                            }
                        }
                    }

                    else -> {
                        if (!project.plugins.hasPlugin(pluginId)) {
                            project.plugins.apply(pluginId)
                        }
                    }
                }
            } catch (e: Exception) {
                project.logger.warn("Cannot apply {} in {}", pluginAlias, project.path, e)
            }
        }
    }

    private fun configureKotlinJvm(
        project: Project,
        module: SourceModuleMetadata,
        pack: PackMetadata,
        repository: PackRepository,
    ) {
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")

        project.afterEvaluate {
            project.extensions.configure(KotlinJvmProjectExtension::class.java) { kotlinExt ->
                kotlinExt.jvmToolchain(21)

                kotlinExt.sourceSets.apply {
                    val main = findByName("main") ?: error("Missing Kotlin JVM source set: main")
                    val test = findByName("test") ?: error("Missing Kotlin JVM source set: test")

                    main.apply {
                        // Keep JVM layout consistent with the single-platform KMP layout
                        kotlin.srcDir("src")
                        resources.srcDir("resources")

                        project.afterEvaluate {
                            // Always include templates
                            project.dependencies.add(implementationConfigurationName, TEMPLATES_ARTIFACT)

                            val requiredDependencies = module.dependencies[Platform.JVM] ?: emptyList()
                            val fullModulePath = module.fullPath(pack.id)

                            project.logger.lifecycle(
                                "Add {} dependencies to {}",
                                requiredDependencies.size,
                                main
                            )

                            // Inter-pack dependencies
                            // We guess the correct module to require here based on the platforms.
                            for (packRequirement in pack.requires) {
                                project.addPackDependency(
                                    pack.id,
                                    module,
                                    apiConfigurationName,
                                    repository.readPackBlocking(packRequirement)
                                )
                            }

                            // Explicit artifact/module dependencies
                            for (dependency in requiredDependencies) {
                                try {
                                    project.dependency(apiConfigurationName, dependency, fullModulePath)
                                } catch (e: Exception) {
                                    project.logger.error("Cannot resolve {}", dependency, e)
                                }
                            }
                        }
                    }

                    test.apply {
                        kotlin.srcDir("test")

                        project.afterEvaluate {
                            project.dependencies.add(
                                implementationConfigurationName,
                                "org.jetbrains.kotlin:kotlin-test"
                            )

                            val requiredTestDependencies = module.testDependencies[Platform.JVM] ?: emptyList()
                            val fullModulePath = module.fullPath(pack.id)

                            project.logger.lifecycle(
                                "Add {} test dependencies to {}",
                                requiredTestDependencies.size,
                                test
                            )

                            for (dependency in requiredTestDependencies) {
                                try {
                                    project.dependency(implementationConfigurationName, dependency, fullModulePath)
                                    } catch (e: Exception) {
                                        project.logger.error("Cannot resolve test dependency {}", dependency, e)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    private fun configureAndroidApp(
        project: Project,
        module: SourceModuleMetadata,
        pack: PackMetadata,
        repository: PackRepository,
    ) {
        project.pluginManager.apply("com.android.application")

        project.pluginManager.withPlugin("com.android.application") {
            project.extensions.configure(ApplicationExtension::class.java) { android ->
                android.namespace = pack.id.toString().replace(Regex("\\W+"), ".")
                android.compileSdk = 36

                android.defaultConfig {
                    minSdk = 21
                    targetSdk = 36
                }

                android.sourceSets.named("main") { main ->
                    main.java.srcDir("src")
                }
                android.sourceSets.named("test") { test ->
                    test.java.srcDir("test")
                }
            }

            // Kotlin Android config
            project.extensions.configure(KotlinAndroidProjectExtension::class.java) { kotlin ->
                kotlin.compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }

        project.afterEvaluate {
            // Always include templates
            project.dependencies.add("implementation", TEMPLATES_ARTIFACT)

            val requiredDependencies = module.dependencies[Platform.ANDROID] ?: emptyList()
            val fullModulePath = module.fullPath(pack.id)

            project.logger.lifecycle(
                "Add {} dependencies to Android app {}",
                requiredDependencies.size,
                project.path
            )

            // Inter-pack dependencies
            for (packRequirement in pack.requires) {
                project.addPackDependency(
                    pack.id,
                    module,
                    "implementation",
                    repository.readPackBlocking(packRequirement)
                )
            }

            // Explicit artifact/module dependencies
            for (dependency in requiredDependencies) {
                try {
                    project.dependency("implementation", dependency, fullModulePath)
                } catch (e: Exception) {
                    project.logger.error("Cannot resolve {}", dependency, e)
                }
            }
        }
    }

    private fun PackRepository.readPackBlocking(requirement: PackRequirement): PackDescriptor {
        return runBlocking { read(requirement.packId) } ?: error("Pack ${requirement.packId} is missing")
    }

    private fun Project.addPackDependency(
        currentPackId: PackId,
        module: SourceModuleMetadata,
        configurationName: String,
        requiredPack: PackDescriptor
    ) {
        val packRequirement = requiredPack.id
        try {
            val requiredModule = when (val requiredPackModules = requiredPack.sources.modules) {
                is ProjectModules.Single -> requiredPackModules.module.takeIf { it.platforms.containsAll(module.platforms) }
                is ProjectModules.Multi -> requiredPackModules.modules.find { it.platforms.containsAll(module.platforms) }
                is ProjectModules.Empty -> null
            }
            if (requiredModule == null) {
                logger.lifecycle("Skipping $packRequirement for ${currentPackId}/${module.path}; no applicable module")
            } else {
                val projectRef = packRequirement.toProjectRef(requiredModule.path)
                dependencies.add(configurationName, project.project(projectRef))
            }
        } catch (e: Exception) {
            logger.error("Cannot resolve {} for $currentPackId", packRequirement, e)
        }
    }

    private fun configureKotlinMultiplatform(
        project: Project,
        module: SourceModuleMetadata,
        pack: PackMetadata,
        repository: PackRepository
    ) {
        project.plugins.apply(KotlinMultiplatformPluginWrapper::class.java)

        // Do NOT delay target/source set creation to afterEvaluate:
        // - other plugins may look for tasks (like jvmJar) during configuration
        // - Android KMP plugin wants compileSdk set during configuration
        project.extensions.configure(KotlinMultiplatformExtension::class.java) { kotlinExt ->
            val isSinglePlatform = module.platforms.size == 1
            val platforms =
                if (isSinglePlatform) listOf(module.platforms.single())
                else listOf(Platform.COMMON) + module.platforms

            for (platform in platforms) {
                kotlinExt.configurePlatform(platform)

                kotlinExt.sourceSets.apply {
                    val mainSourceSet = findByName(platform.kotlinSourceSetName)
                    val testSourceSet = findByName(platform.kotlinTestSourceSetName)

                    mainSourceSet?.apply {
                        if (isSinglePlatform || platform == Platform.COMMON) {
                            kotlin.srcDir("src")
                            resources.srcDir("resources")
                        } else {
                            kotlin.srcDir(platform.srcDir)
                            resources.srcDir(platform.resourcesDir)
                        }

                        project.afterEvaluate {
                            project.dependencies.add(implementationConfigurationName, TEMPLATES_ARTIFACT)

                            val requiredDependencies = module.dependencies[platform] ?: emptyList()
                            val fullModulePath = module.fullPath(pack.id)

                            project.logger.lifecycle(
                                "Add {} dependencies to {}",
                                requiredDependencies.size,
                                this
                            )

                            for (packRequirement in pack.requires) {
                                project.addPackDependency(
                                    pack.id,
                                    module,
                                    apiConfigurationName,
                                    repository.readPackBlocking(packRequirement)
                                )
                            }

                            for (dependency in requiredDependencies) {
                                try {
                                    project.dependency(this, dependency, fullModulePath)
                                } catch (e: Exception) {
                                    project.logger.error("Cannot resolve {}", dependency, e)
                                }
                            }
                        }
                    } ?: project.logger.error("Missing source set ${platform.kotlinSourceSetName}")

                    testSourceSet?.apply {
                        if (isSinglePlatform || platform == Platform.COMMON) {
                            kotlin.srcDir("test")
                        } else {
                            kotlin.srcDir("test@${platform.name.lowercase()}")
                        }

                        project.afterEvaluate {
                            project.dependencies.add(
                                implementationConfigurationName,
                                "org.jetbrains.kotlin:kotlin-test"
                            )

                            val requiredTestDependencies = module.testDependencies[platform] ?: emptyList()
                            val fullModulePath = module.fullPath(pack.id)

                            project.logger.lifecycle(
                                "Add {} test dependencies to {}",
                                requiredTestDependencies.size,
                                this
                            )

                            for (dependency in requiredTestDependencies) {
                                try {
                                    project.dependency(this, dependency, fullModulePath)
                                } catch (e: Exception) {
                                    project.logger.error("Cannot resolve test dependency {}", dependency, e)
                                }
                            }
                        }
                    } ?: project.logger.error("Missing source set ${platform.kotlinTestSourceSetName}")
                }
            }
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    private fun KotlinMultiplatformExtension.configurePlatform(platform: Platform) {
        when (platform) {
            Platform.COMMON -> {}
            Platform.ANDROID -> {} // configured in specific plugin
            Platform.JVM -> jvm()
            Platform.WASM -> wasmJs()
            Platform.JS -> js()
            Platform.WEB -> wasmJs()
            Platform.NATIVE -> linuxX64()
            Platform.IOS -> iosArm64()

        }
    }

    private val Platform.kotlinSourceSetName
        get() =
            when (this) {
                Platform.COMMON -> "commonMain"
                Platform.JVM -> "jvmMain"
                Platform.ANDROID -> "androidMain"
                Platform.WASM -> "wasmJsMain"
                Platform.NATIVE -> "nativeMain"
                Platform.IOS -> "iosArm64Main"
                Platform.JS -> "jsMain"
                Platform.WEB -> "wasmJsMain"
            }

    private val Platform.kotlinTestSourceSetName
        get() =
            when (this) {
                Platform.COMMON -> "commonTest"
                Platform.JVM -> "jvmTest"
                Platform.ANDROID -> "androidUnitTest"
                Platform.WASM -> "wasmJsTest"
                Platform.NATIVE -> "nativeTest"
                Platform.IOS -> "iosArm64Test"
                Platform.JS -> "jsTest"
                Platform.WEB -> "wasmJsTest"
            }

    private fun Project.dependency(
        configurationName: String,
        dependency: Dependency,
        modulePath: String,
    ) {
        when (dependency) {
            is CatalogReference -> {
                val catalog = extensions.getByType(VersionCatalogsExtension::class.java).named(dependency.catalog)
                val provider = catalog.findLibrary(dependency.keyInCatalog).orElseThrow()
                dependencies.add(configurationName, provider)
            }

            is ModuleDependency -> {
                val projectRef = dependency.toProjectRef(modulePath)
                dependencies.add(configurationName, project(projectRef))
            }

            is ArtifactDependency -> {
                val artifact = "${dependency.group}:${dependency.artifact}:${dependency.version}"
                dependencies.add(configurationName, artifact)
            }

            is FunctionDependency -> {
                error("Unsupported function dependency ${dependency.functionName} for JVM module")
            }
        }
    }

    private fun Project.dependency(
        sourceSet: KotlinSourceSet,
        dependency: Dependency,
        modulePath: String
    ) {
        when (dependency) {
            is CatalogReference -> {
                val keys = dependency.key.removePrefix("$").split(".").toMutableList()
                val catalog = extensions.getByType(VersionCatalogsExtension::class.java)
                    .named(keys.removeFirst())
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
                when (dependency.functionName) {
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
