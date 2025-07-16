package org.jetbrains.kastle.gradle

import org.jetbrains.kastle.PackId
import org.jetbrains.kastle.SourceModuleType
import org.jetbrains.kastle.SourceTemplate
import org.jetbrains.kastle.StaticSource
import org.jetbrains.kastle.gen.ProjectMapping
import org.jetbrains.kastle.map
import org.jetbrains.kastle.utils.protocol
import org.jetbrains.kastle.utils.capitalizeFirst

private val GRADLE_PACK_ID = PackId("org.gradle", "gradle")
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
                    if (source.target.protocol == "file" && source.target.contains(regex)) {
                        val newTarget = source.target.replace(regex) { match ->
                            val sourceRoot = match.groups[1]!!.value
                            val mainOrTest = if (sourceRoot in setOf("test", "testResources")) "test" else "main"
                            val kotlinOrResources = if (sourceRoot in setOf("resources", "testResources")) "resources" else "kotlin"
                            when (val target = match.groups[2]?.value) {
                                null -> when (module.platforms.singleOrNull()) {
                                    null -> "src/common${mainOrTest.capitalizeFirst()}/$kotlinOrResources/"
                                    else -> "src/main/$kotlinOrResources/"
                                }
                                else -> "src/${target}${mainOrTest.capitalizeFirst()}/$kotlinOrResources/"
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