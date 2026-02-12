package org.jetbrains.kastle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.write
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

            for (source in moduleSources) {
                val path = getActualPath(source, module, this)
                val packId = source.packId ?: run {
                    log.warn { "Skipping ${source.target}; missing pack ID" }
                    continue
                }
                val variables = collectVariables(this, packId, module)

                source.condition?.evaluate(variables)?.let { conditionResult ->
                    if (!conditionResult.isTruthy()) {
                        log.debug { "Skipping ${source.target}; ${source.condition} = $conditionResult" }
                        continue
                    } else {
                        log.trace { "Include ${source.target}; ${source.condition} = $conditionResult" }
                    }
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

    private fun getActualPath(source: SourceFile, module: SourceModule, project: Project): String {
        val sourcePackId = source.packId
        val variables = sourcePackId
            ?.takeIf { source.target is StringTemplate }
            ?.let { collectVariables(project, sourcePackId, module) }
            ?: Variables()
        val evaluatedTarget = source.target.evaluate(variables)
        val relativePath = evaluatedTarget.toString().relativeFile
        return Path(module.path, relativePath).normalize().toString()
    }

    private fun collectVariables(project: Project, packId: PackId, module: SourceModule): Stack<Map<String, Any?>> {
        val pack = project.packs.find { it.id == packId } ?: throw MissingPackException(packId)
        val baseVariables = project.getVariables(pack) +
                project.toVariableEntry() +
                module.toVariableEntry() +
                module.slotsVariableEntry(project, packId)
        return baseVariables + loadDynamicProperties(project, baseVariables)
    }

    private fun loadDynamicProperties(project: Project, variables: Variables): Map<String, Any?> {
        require(project.properties.values.none { it is UnresolvedProperty }) {
            "Undefined properties: ${project.properties.values.filterIsInstance<UnresolvedProperty>().map { it.descriptor.key }}"
        }
        val resolved = project.properties.values
            .filterIsInstance<ResolvedProperty>()
            .associate { it.descriptor.key to it.value }
            .toMutableMap()
        val dynamicProperties = project.properties.values
            .filterIsInstance<DynamicProperty>()
            .toMutableList()
        var evaluationFailed = false

        fun DynamicProperty.evaluate(assignment: PropertyAssignment, type: PropertyType = descriptor.type): Any? =
            when(assignment) {
                is ValueAssignment -> type.parse(assignment.value)
                is ExpressionAssignment -> type.cast(assignment.expression.evaluate(variables + resolved))
            }

        // to allow resolution of other values in the current map,
        // keep trying to resolve properties until no progress is made
        while (dynamicProperties.isNotEmpty()) {
            val initialSize = dynamicProperties.size
            val iterator = dynamicProperties.listIterator()
            while (iterator.hasNext()) {
                val property = iterator.next()
                val evalResult = try {
                    if (property.descriptor.type.isList()) {
                        property.assignments.map {
                            property.evaluate(it, property.descriptor.type.elementType!!)
                        }
                    } else {
                        property.evaluate(
                            property.assignments.singleOrNull()
                                ?: error("Multiple values supplied for property ${property.descriptor.key}")
                        )
                    }
                } catch (e: Exception) {
                    if (evaluationFailed) throw e
                    log.debug { "Failed property value evaluation ${property.descriptor.key}: ${e.message}; will try again" }
                    continue
                }
                resolved[property.descriptor.key] = evalResult
                iterator.remove()
            }
            evaluationFailed = initialSize == dynamicProperties.size
        }

        return resolved
    }

}
