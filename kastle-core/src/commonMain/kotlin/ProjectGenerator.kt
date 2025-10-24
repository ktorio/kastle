package org.jetbrains.kastle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.Buffer
import kotlinx.io.write
import org.jetbrains.kastle.gen.*
import org.jetbrains.kastle.structure.GradleSourceMapping
import org.jetbrains.kastle.logging.ConsoleLogger
import org.jetbrains.kastle.logging.Logger
import org.jetbrains.kastle.structure.NestedPackagingMapping
import org.jetbrains.kastle.utils.*

interface ProjectGenerator {
    companion object {
        fun fromRepository(
            repository: PackRepository,
            projectResolver: ProjectResolver =
                ProjectResolver.Default + NestedPackagingMapping + GradleSourceMapping,
            log: Logger = ConsoleLogger(),
        ): ProjectGenerator = ProjectGeneratorImpl(repository, projectResolver, log)
    }

    fun generate(projectDescriptor: ProjectDescriptor): Flow<SourceFileEntry>
}

class ProjectGeneratorImpl(
    private val repository: PackRepository,
    private val projectResolver: ProjectResolver,
    private val log: Logger,
) : ProjectGenerator {
    val templateEvaluator = TemplateEvaluator(log)

    override fun generate(projectDescriptor: ProjectDescriptor): Flow<SourceFileEntry> = flow {
        val project = projectResolver.resolve(projectDescriptor, repository)
        log.trace { project.name }
        log.trace {
            buildString {
                for (module in project.moduleSources.modules) {
                    appendLine("  ${module.path.ifEmpty { "<root>" }}")
                    append("    dependencies:")
                    if (module.dependencies.isEmpty()) appendLine(" <none>") else appendLine()
                    module.dependencies.forEach { appendLine("      - $it") }
                    append("    testDependencies:")
                    if (module.testDependencies.isEmpty()) appendLine(" <none>") else appendLine()
                    module.testDependencies.forEach { appendLine("      - $it") }
                }
            }
        }
        for (module in project.moduleSources.modules) {
            val moduleSources = buildList {
                addAll((module.sources.filter { it.target.protocol == "file" }))
                addAll(project.commonSources)
            }
            val outputtedPaths = mutableSetOf<String>()
            val slotSources = project.slotSources + module.slotSources

            for (source in moduleSources) {
                val path = module.path.appendPath(source.target.relativeFile)
                if (source !is SourceTemplate) {
                    if (source !is StaticSource)
                        error("Unsupported source type: ${source::class.simpleName}")

                    log.debug { "Include ${source.target}; skip templating" }
                    emit(SourceFileEntry(path) {
                        Buffer().apply {
                            write(source.contents)
                        }
                    })
                    continue
                }

                val packId = source.packId
                if (packId == null) {
                    log.warn { "Skipping ${source.target}; missing pack ID" }
                    continue
                }
                val pack = project.packs.find { it.id == packId } ?: throw MissingPackException(packId)
                val variables = project.getVariables(pack) +
                        project.toVariableEntry() +
                        module.toVariableEntry() +
                        module.slotsVariableEntry(project, packId) +
                        module.loadPropertyValues(project)

                if (source.condition != null) {
                    val conditionValue = source.condition.evaluate(variables)
                    if (!conditionValue.isTruthy()) {
                        log.debug { "Skipping ${source.target}; ${source.condition} = $conditionValue" }
                        continue
                    }
                }

                if (!outputtedPaths.add(path)) {
                    log.debug { "Skipping ${source.target}; duplicate path $path" }
                    continue
                }

                emit(SourceFileEntry(path) {
                    templateEvaluator.evaluateToBuffer(
                        template = source,
                        groupId = project.group,
                        packId = pack.id,
                        variables = variables,
                        slots = slotSources,
                    )
                })
            }
        }
    }

    private fun SourceModule.loadPropertyValues(project: Project): Map<String, Any?> =
        propertyValues.mapNotNull {
            project.propertyDescriptors[it.key]?.let { property ->
                property.key to property.type.parse(it.value)
            }
        }.toMap()

}

class MissingPackException(pack: PackId) : Exception("Missing pack: $pack")
class FailedToReadPackException(pack: PackId, cause: Throwable) : Exception("Failed to read pack: $pack", cause)