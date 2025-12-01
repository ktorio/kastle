package org.jetbrains.kastle.structure

import org.jetbrains.kastle.PackId
import org.jetbrains.kastle.Platform
import org.jetbrains.kastle.SourceTemplate
import org.jetbrains.kastle.StaticSource
import org.jetbrains.kastle.gen.ProjectMapping
import org.jetbrains.kastle.map
import org.jetbrains.kastle.utils.protocol
import org.jetbrains.kastle.utils.capitalizeFirst
import org.jetbrains.kastle.utils.fileName

internal val GRADLE_PACK_ID = PackId("org.gradle", "gradle")
internal val SOURCE_OR_RESOURCE_FOLDER_REGEX = Regex("(src|test|resources|testResources|res|composeResources)(?:@(\\w+))?/")
internal val SOURCE_FOLDER_REGEX = Regex("(src|test)(?:@(\\w+))?/")
internal val RESOURCE_FOLDER_REGEX = Regex("(resources|testResources|res|composeResources)(?:@(\\w+))?/")
internal val UNCATEGORIZED_FILES = setOf("AndroidManifest.xml")

/**
 * Transforms Amper source structure with Gradle.
 */
val GradleSourceMapping = ProjectMapping { project ->
    if (project.packs.none { it.id == GRADLE_PACK_ID })
        return@ProjectMapping project

    project.copy(
        moduleSources = project.moduleSources.map { module ->
            module.copy(
                sources = module.sources.map { source ->
                    if (source.target.protocol == "file" && source.target.contains(SOURCE_OR_RESOURCE_FOLDER_REGEX)) {
                        val newTarget = source.target.replace(SOURCE_OR_RESOURCE_FOLDER_REGEX) { match ->
                            val sourceRoot = match.groups[1]!!.value
                            val mainOrTest = (if (sourceRoot in setOf("test", "testResources")) "test" else "main").capitalizeFirst()
                            val fileCategory = when {
                                source.target.fileName in UNCATEGORIZED_FILES -> ""
                                sourceRoot in setOf("res", "composeResources") -> "$sourceRoot/"
                                sourceRoot in setOf("resources", "testResources") -> "resources/"
                                else -> "kotlin/"
                            }
                            when (val target = match.groups[2]?.value) {
                                null -> when(val platform = module.platforms.singleOrNull()) {
                                    null -> "src/common$mainOrTest/$fileCategory"
                                    // only when using the kotlin jvm plugin
                                    Platform.JVM -> "src/main/$fileCategory"
                                    else -> "src/${platform.code}$mainOrTest/$fileCategory"
                                }
                                else -> "src/$target$mainOrTest/$fileCategory"
                            }
                        }
                        when(source) {
                            is StaticSource -> source.copy(target = newTarget)
                            is SourceTemplate -> source.copy(target = newTarget)
                        }
                    }
                    else source
                }
            )
        }
    )
}