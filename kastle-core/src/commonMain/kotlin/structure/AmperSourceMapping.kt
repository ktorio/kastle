package org.jetbrains.kastle.structure

import org.jetbrains.kastle.gen.ProjectMapping
import org.jetbrains.kastle.gradlePlugins

val AmperSourceMapping = ProjectMapping { project ->
    if (project.packs.none { it.id == BuildToolModules.AMPER_PACK_ID })
        return@ProjectMapping project

    for (module in project.moduleSources.modules) {
        // we assume that when amper is configured, then gradle plugins won't cause trouble
        if (module.amper.isNotEmpty())
            continue
        require(module.gradlePlugins.isEmpty()) {
            "Project has plugins that require the Gradle build system"
        }
    }
    project
}
