package org.jetbrains.kastle.structure

import org.jetbrains.kastle.Platform
import org.jetbrains.kastle.gen.ProjectMapping

val MavenSourceMapping = ProjectMapping { project ->
    if (project.packs.none { it.id == BuildToolModules.MAVEN_PACK_ID })
        return@ProjectMapping project

    for (module in project.moduleSources.modules) {
        val nonJvmModules = module.platforms - Platform.JVM
        require(nonJvmModules.isEmpty()) {
            "Maven only supports JVM modules, but ${module.path} has ${nonJvmModules.joinToString()}"
        }
    }
    project
}
