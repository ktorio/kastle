package org.jetbrains.kastle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.files.Path
import org.jetbrains.kastle.gen.*
import org.jetbrains.kastle.structure.GradleSourceMapping
import org.jetbrains.kastle.logging.ConsoleLogger
import org.jetbrains.kastle.logging.Logger
import org.jetbrains.kastle.structure.NestedPackagingMapping
import org.jetbrains.kastle.utils.*
import kotlin.text.ifEmpty

class ProjectGenerator(
    private val repository: PackRepository,
    private val projectResolver: ProjectResolver = DefaultResolver,
    private val log: Logger = ConsoleLogger(),
) {
    companion object {
        val DefaultResolver = ProjectResolver.BaseImpl +
                NestedPackagingMapping +
                GradleSourceMapping
    }

    private val templateEvaluator = TemplateEvaluator(log)

    suspend fun generate(projectDescriptor: ProjectDescriptor): Flow<SourceFileEntry> {
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

        // 1. Dry run to validate configuration
        project.forEachTemplate { template ->
            if (template is SourceTemplateIR.Parameters)
                templateEvaluator.evaluateTo(template, DevNull)
        }

        // 2. Write actual source file entries
        return flow {
            project.forEachTemplate { template ->
                emit(SourceFileEntry(template.path) {
                    templateEvaluator.evaluateToBuffer(template)
                })
            }
        }
    }

    private suspend fun Project.forEachTemplate(action: suspend (SourceTemplateIR) -> Unit) {
        for (module in moduleSources.modules.sortedBy { it.path.ifEmpty { "zz-top" } }) {
            val moduleSources = buildList {
                addAll((module.sources.filter { it.target.protocol == "file" }))
                addAll(commonSources)
            }
            val outputtedPaths = mutableSetOf<String>()
            val slotSources = slotSources + module.slotSources
            val visitedPaths = mutableSetOf<String>()

            for (source in moduleSources) {
                val path = getActualPath(source, module)
                val packId = source.packId ?: run {
                    log.warn { "Skipping ${source.target}; missing pack ID" }
                    continue
                }
                val variables = collectVariables(packId, module)
                if (source.condition != null && !source.condition?.evaluate(variables).isTruthy()) {
                    log.debug { "Skipping ${source.target}; ${source.condition} = ${source.condition?.evaluate(variables)}" }
                    continue
                } else if (!visitedPaths.add(path)) {
                    log.debug { "Skipping ${source.target}; duplicate path $path" }
                    continue
                } else {
                    log.trace { "Include ${source.target}; ${source.condition} = ${source.condition?.evaluate(variables)}" }
                }

                if (source !is SourceTemplate) {
                    check(source is StaticSource) {
                        "Unsupported source type: ${source::class.simpleName}"
                    }
                    log.debug { "Include ${source.target}; skip templating" }
                    action(SourceTemplateIR.Static(path, source.contents))
                    continue
                }

                if (!outputtedPaths.add(path)) {
                    log.debug { "Skipping ${source.target}; duplicate path $path" }
                    continue
                }

                action(
                    SourceTemplateIR.Parameters(
                        path = path,
                        template = source,
                        groupId = group,
                        packId = packId,
                        variables = variables,
                        slots = slotSources,
                    )
                )
            }
        }
    }

    private fun Project.getActualPath(source: SourceFile, module: SourceModule): String {
        val sourcePackId = source.packId
        val variables = sourcePackId
            ?.takeIf { source.target is StringTemplate }
            ?.let { collectVariables(sourcePackId, module) }
            ?: Variables()
        val evaluatedTarget = source.target.evaluate(variables)
        val relativePath = evaluatedTarget.toString().relativeFile

        return Path(module.path, relativePath).normalize().toString()
    }

    private fun Project.collectVariables(packId: PackId, module: SourceModule): Stack<Map<String, Any?>> {
        val pack = packs.find { it.id == packId } ?: throw MissingPackException(packId)
        val modulePath = module.originalPath.takeIf { it.isNotEmpty() }
        val baseVariables = resolvedVariables(pack, modulePath) +
            toVariableEntry() +
            module.toVariableEntry() +
            module.slotsVariableEntry(packId)
        return baseVariables + dynamicVariables(pack, modulePath, baseVariables)
    }

}
