package org.jetbrains.kastle.gen

import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.ProjectDescriptor

fun interface ProjectMapping {
    operator fun invoke(project: Project): Project
}

operator fun ProjectResolver.plus(mapping: ProjectMapping): ProjectResolver =
    ProjectMappingResolver(this, mapping)

class ProjectMappingResolver(
    val base: ProjectResolver,
    val mapping: ProjectMapping,
): ProjectResolver {
    override suspend fun resolve(
        descriptor: ProjectDescriptor,
        repository: PackRepository
    ): Project {
        return mapping(base.resolve(descriptor, repository))
    }
}