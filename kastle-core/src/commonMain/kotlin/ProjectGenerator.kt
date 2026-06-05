package org.jetbrains.kastle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.files.Path
import org.jetbrains.kastle.gen.*
import org.jetbrains.kastle.structure.GradleSourceMapping
import org.jetbrains.kastle.logging.ConsoleLogger
import org.jetbrains.kastle.logging.Logger
import org.jetbrains.kastle.structure.ToolchainSourceMapping
import org.jetbrains.kastle.structure.FlattenModuleDependencies
import org.jetbrains.kastle.structure.MavenSourceMapping
import org.jetbrains.kastle.structure.NestedPackagingMapping
import org.jetbrains.kastle.utils.*
import kotlin.collections.buildMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.text.ifEmpty

class ProjectGenerator(
    private val repository: PackRepository,
    private val projectResolver: ProjectResolver = DefaultResolver,
    private val log: Logger = ConsoleLogger(),
) {
    companion object {
        val DefaultResolver = ProjectResolver.BaseImpl +
                FlattenModuleDependencies +
                NestedPackagingMapping +
                GradleSourceMapping +
                ToolchainSourceMapping +
                MavenSourceMapping
    }

    private val templateEvaluator = TemplateEvaluator(log)

    suspend fun generate(projectDescriptor: ProjectDescriptor): Flow<SourceFileEntry> {
        val project = projectResolver.resolve(projectDescriptor, repository)
        log.trace {
            buildString {
                appendLine("### ${project.name}")
                append(generateSequence { '=' }.take(project.name.length + 4).joinToString(""))
            }
        }
        log.trace {
            buildString {
                appendLine("  Properties:")
                for ((key, value) in project.properties[PropertyScope.Root].orEmpty()) {
                    appendLine("    $key = $value")
                }
                for ((scope, properties) in project.properties) {
                    if (scope !is PropertyScope.Module) continue
                    appendLine("  Module \"${scope.path}\"")
                    for ((key, value) in properties) {
                        appendLine("    $key = $value")
                    }
                }
            }
        }
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
        val eagerVars = eagerVariables(properties)
        for (module in moduleSources.modules.sortedBy { it.path.ifEmpty { "zz-top" } }) {
            val condition = module.condition
            if (condition != null) {
                if (!condition.expression.evaluate(eagerVars.relativeTo(condition.packId)).isTruthy()) {
                    log.debug { "Skipping module ${module.path}; condition ${condition.expression} = false" }
                    continue
                }
            }

            val moduleSources = buildList {
                addAll((module.sources.filter { it.target.protocol == "file" }))
                addAll(commonSources)
            }
            val outputtedPaths = mutableSetOf<String>()
            val slotSources = slotSources + module.slotSources
            val visitedPaths = mutableSetOf<String>()

            for (source in moduleSources) {
                val packId = source.packId ?: run {
                    log.warn { "Skipping ${source.target}; missing pack ID" }
                    continue
                }
                val variables = collectVariables(packId, module)
                val path = getActualPath(source, module, variables)
                val conditionExpression = source.condition
                if (conditionExpression != null && !conditionExpression.evaluate(variables).isTruthy()) {
                    log.debug { "Skipping ${source.target}; $conditionExpression = ${conditionExpression.evaluate(variables)}" }
                    continue
                } else if (!visitedPaths.add(path)) {
                    log.debug { "Skipping ${source.target}; duplicate path $path" }
                    continue
                } else {
                    log.trace { "Include ${source.target}; $conditionExpression = ${conditionExpression?.evaluate(variables)}" }
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

    private fun getActualPath(
        source: SourceFile,
        module: SourceModule,
        variables: LocalVariables
    ): String {
        val evaluatedTarget = source.target.evaluate(variables)
        val relativePath = evaluatedTarget.relativeFile
        return Path(module.path, relativePath).normalize().toString()
    }

    private fun Project.collectVariables(packId: PackId, module: SourceModule): LocalVariables {
        val modulePath = module.originalPath.takeIf { it.isNotEmpty() }
        val slotSources = slotSources + module.slotSources
        val variables = resolvedVariables(modulePath).relativeTo(packId).also { variables ->
            variables["_project"] = this.asTemplateMap()
            variables["_module"] = module.asTemplateMap()
            variables["_slots"] = buildMap {
                for ((url, value) in slotSources) {
                    // relative url
                    if (packId.toString() in url)
                        put(url.relativeFile.removePrefix(packId.toString()).trimStart('/'), value)
                    // absolute url
                    put(url, value.map { sourceFile ->
                        mapOf(
                            "target" to sourceFile.target.evaluate(variables),
                            "condition" to sourceFile.condition.toString(),
                            "pack" to sourceFile.packId,
                        )
                    })
                }
            }
        }
        val dynamicValues =
            dynamicVariables(modulePath, variables).relativeTo(packId)

        return variables + dynamicValues
    }

}
