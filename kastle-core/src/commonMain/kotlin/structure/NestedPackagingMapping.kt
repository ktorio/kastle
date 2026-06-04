package org.jetbrains.kastle.structure

import org.jetbrains.kastle.PackagingStyle
import org.jetbrains.kastle.SourceTemplate
import org.jetbrains.kastle.StaticSource
import org.jetbrains.kastle.gen.ProjectMapping
import org.jetbrains.kastle.utils.StringLiteral
import org.jetbrains.kastle.utils.protocol

/**
 * Uses the project group as the base package.
 */
val NestedPackagingMapping = ProjectMapping { project ->
    if (project.packaging != PackagingStyle.NESTED)
        return@ProjectMapping project

    val groupFolder = project.group.replace('.', '/')
    project.copy(
        moduleSources = project.moduleSources.map { module ->
            module.copy(
                sources = module.sources.map { source ->
                    // TODO support string templates
                    if (source.target.protocol == "file" && source.target.toString().contains(SOURCE_FOLDER_REGEX)) {
                        val newTarget = source.target.toString().replace(SOURCE_FOLDER_REGEX) { match ->
                            match.value + groupFolder + '/'
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
