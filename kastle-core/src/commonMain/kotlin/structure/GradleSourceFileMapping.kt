package org.jetbrains.kastle.structure

import org.jetbrains.kastle.Platform
import org.jetbrains.kastle.SourceTemplate
import org.jetbrains.kastle.StaticSource
import org.jetbrains.kastle.gen.ProjectMapping
import org.jetbrains.kastle.structure.BuildToolModules.GRADLE_PACK_ID
import org.jetbrains.kastle.structure.BuildToolModules.MAVEN_PACK_ID
import org.jetbrains.kastle.utils.StringLiteral
import org.jetbrains.kastle.utils.capitalizeFirst
import org.jetbrains.kastle.utils.fileName
import org.jetbrains.kastle.utils.protocol

internal val SOURCE_OR_RESOURCE_FOLDER_REGEX = Regex("(src|test|resources|testResources|res|composeResources)(?:@(\\w+))?/")
internal val SOURCE_FOLDER_REGEX = Regex("(src|test)(?:@(\\w+))?/")
internal val UNCATEGORIZED_FILES = setOf("AndroidManifest.xml")

/**
 * Transforms Amper source structure with Gradle.
 */
val GradleSourceMapping = ProjectMapping { project ->
    if (project.packs.none { it.id == GRADLE_PACK_ID || it.id == MAVEN_PACK_ID })
        return@ProjectMapping project

    project.copy(
        moduleSources = project.moduleSources.map { module ->
            module.copy(
                sources = module.sources.map { source ->
                    // TODO support string templates
                    if (source.target.protocol == "file" && source.target.toString().contains(SOURCE_OR_RESOURCE_FOLDER_REGEX)) {
                        val newTarget = source.target.toString().replace(SOURCE_OR_RESOURCE_FOLDER_REGEX) { match ->
                            val sourceRoot = match.groups[1]!!.value
                            val mainOrTest = if (sourceRoot in setOf("test", "testResources")) "test" else "main"
                            val fileCategory = when {
                                source.target.fileName in UNCATEGORIZED_FILES -> ""
                                sourceRoot in setOf("res", "composeResources") -> "$sourceRoot/"
                                sourceRoot in setOf("resources", "testResources") -> "resources/"
                                source.target.fileName.endsWith(".proto") -> "proto/"
                                else -> "kotlin/"
                            }
                            when (val target = match.groups[2]?.value) {
                                null -> when(val platform = module.platforms.singleOrNull()) {
                                    null -> "src/common${mainOrTest.capitalizeFirst()}/$fileCategory"
                                    // only when using the kotlin jvm plugin
                                    Platform.JVM -> "src/$mainOrTest/$fileCategory"
                                    else -> "src/${platform.code}${mainOrTest.capitalizeFirst()}/$fileCategory"
                                }
                                else -> "src/$target${mainOrTest.capitalizeFirst()}/$fileCategory"
                            }
                        }
                        when(source) {
                            is StaticSource -> source.copy(target = StringLiteral(newTarget))
                            is SourceTemplate -> source.copy(target = StringLiteral(newTarget))
                        }
                    }
                    else source
                }
            )
        }
    )
}
