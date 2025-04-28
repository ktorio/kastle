package org.jetbrains.kastle.gradle

import org.jetbrains.kastle.PackId
import org.jetbrains.kastle.SourceModuleType
import org.jetbrains.kastle.gen.ProjectMapping
import org.jetbrains.kastle.map
import org.jetbrains.kastle.utils.protocol

private val GRADLE_PACK_ID = PackId("std", "gradle")
private val regex = Regex("(src|test|resources|testResources)(?:@(\\w+))?/")

/**
 * Transforms Amper source structure with Gradle.
 */
val GradleTransformation = ProjectMapping { project ->
    if (project.packs.none { it.id == GRADLE_PACK_ID })
        return@ProjectMapping project

    project.copy(
        moduleSources = project.moduleSources.map { module ->
            module.copy(
                sources = module.sources.map { source ->
                    if (source.target.protocol == "file" && source.target.contains(regex))
                        source.copy(target = source.target.replace(regex) { match ->
                            val sourceRoot = match.groups[1]!!.value
                            val mainOrTest = if (sourceRoot in setOf("test", "testResources")) "test" else "main"
                            val kotlinOrResources = if (sourceRoot in setOf("resources", "testResources")) "resources" else "kotlin"
                            when (val target = match.groups[2]?.value) {
                                null -> when (module.type) {
                                    SourceModuleType.JVM_APP,
                                    SourceModuleType.ANDROID_APP,
                                    SourceModuleType.IOS_APP -> "src/main/kotlin/"
                                    SourceModuleType.LIB -> "src/common${mainOrTest.capitalize()}/$kotlinOrResources/"
                                }
                                else -> "src/${target}${mainOrTest.capitalize()}/$kotlinOrResources/"
                            }
                        })
                    else source
                }
            )
        }
    )
}