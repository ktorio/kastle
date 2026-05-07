package org.jetbrains.kastle.gen

import org.jetbrains.kastle.*
import org.jetbrains.kastle.utils.LocalVariables
import org.jetbrains.kastle.utils.Variables

/**
 * Replace full variable ID keys with local variable names for referencing from template.
 */
internal fun Project.resolvedVariables(modulePath: String?): Variables {
    fun Map<VariableId, PropertyInstance>.toMap(): Map<VariableId, Any?> =
        entries.mapNotNull { (variableId, propertyInstance) ->
            if (propertyInstance !is ResolvedProperty) return@mapNotNull null
            variableId to propertyInstance.value
        }.toMap()

    val rootScope = properties[PropertyScope.Root]?.toMap()
    val moduleScope = modulePath?.let {
        properties[PropertyScope.Module(modulePath)]?.toMap()
    }
    return Variables(listOfNotNull(rootScope, moduleScope))
}

// TODO relativize dynamic variableIds
internal fun Project.dynamicVariables(
    modulePath: String?,
    variables: LocalVariables,
): Variables {
    val resolved = LocalVariables(variables.packIds, Variables())
    val dynamicProperties = listOfNotNull(
        properties[PropertyScope.Root]?.entries,
        modulePath?.let { properties[PropertyScope.Module(modulePath)] }?.entries,
    ).flatten().mapNotNull { (variableId, propertyInstance) ->
        if (propertyInstance !is DynamicProperty) return@mapNotNull null
        variableId to propertyInstance
    }.toMutableList()
    var evaluationFailed = false

    fun DynamicProperty.evaluate(
        assignment: PropertyAssignment,
        type: PropertyType = descriptor.type
    ): Any? {
        return when(assignment) {
            is ValueAssignment -> type.parse(assignment.value)
            is ExpressionAssignment -> type.cast(assignment.expression.evaluate(
                (variables + resolved).relativeTo(assignment.packId)
            ))
        }
    }

    // to allow resolution of other values in the current map,
    // keep trying to resolve properties until no progress is made
    while (dynamicProperties.isNotEmpty()) {
        val initialSize = dynamicProperties.size
        val iterator = dynamicProperties.listIterator()
        while (iterator.hasNext()) {
            val (variableId, property) = iterator.next()
            val evalResult = try {
                // lists should accept multiple element assignments or a single list assignment
                // TODO leverage the type system better here, this is messy
                if (property.descriptor.type.isList()) {
                    property.assignments.map { assignment ->
                        property.evaluate(assignment, property.descriptor.type.elementType!!)
                    }.let { result ->
                        result.flatMap { elem ->
                            when(elem) {
                                is List<*> -> elem
                                else -> listOf(elem)
                            }
                        }
                    }
                } else {
                    property.evaluate(
                        property.assignments.singleOrNull()
                            ?: error("Multiple values supplied for property ${property.descriptor.key}")
                    )
                }
            } catch (e: Exception) {
                if (evaluationFailed) throw e
                continue
            }
            resolved[variableId] = evalResult
            iterator.remove()
        }
        evaluationFailed = initialSize == dynamicProperties.size
    }

    return resolved
}

/**
 * Builds a [Variables] from resolved and simple value-assigned properties,
 * sufficient for evaluating module-level conditions before full variable resolution.
 *
 * Expression-assigned properties are also evaluated iteratively: if all variables
 * referenced by an expression are resolvable from the eager set, the result is included.
 * Expressions that cannot be resolved are silently skipped.
 */
internal fun eagerVariables(
    properties: Map<PropertyScope, Map<VariableId, PropertyInstance>>
): Variables {
    val resolved = mutableMapOf<VariableId, Any?>()
    val pending = mutableListOf<Triple<VariableId, PropertyDescriptor, ExpressionAssignment>>()

    properties[PropertyScope.Root]?.forEach { (variableId, instance) ->
        when (instance) {
            is ResolvedProperty -> resolved[variableId] = instance.value
            is DynamicProperty -> when (val assignment = instance.assignments.singleOrNull()) {
                is ValueAssignment -> resolved[variableId] = instance.descriptor.type.parse(assignment.value)
                is ExpressionAssignment -> pending.add(Triple(variableId, instance.descriptor, assignment))
                else -> {}
            }
            else -> {}
        }
    }

    var progress = true
    while (pending.isNotEmpty() && progress) {
        progress = false
        val iterator = pending.listIterator()
        while (iterator.hasNext()) {
            val (variableId, descriptor, assignment) = iterator.next()
            val result = try {
                descriptor.type.cast(
                    assignment.expression.evaluate(Variables(resolved).relativeTo(assignment.packId))
                )
            } catch (_: Exception) {
                // skip silently
                continue
            }
            resolved[variableId] = result
            iterator.remove()
            progress = true
        }
    }

    return Variables(listOf(resolved))
}
