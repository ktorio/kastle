package org.jetbrains.kastle.gen

import org.jetbrains.kastle.DynamicProperty
import org.jetbrains.kastle.ExpressionAssignment
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.PropertyAssignment
import org.jetbrains.kastle.PropertyInstance
import org.jetbrains.kastle.PropertyScope
import org.jetbrains.kastle.PropertyType
import org.jetbrains.kastle.ResolvedProperty
import org.jetbrains.kastle.ValueAssignment
import org.jetbrains.kastle.VariableId
import org.jetbrains.kastle.utils.Stack.Companion.toStack
import org.jetbrains.kastle.utils.Variables
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * Replace full variable ID keys with local variable names for referencing from template.
 */
internal fun Project.resolvedVariables(
    pack: PackDescriptor,
    modulePath: String?,
): Variables {
    fun Map<VariableId, PropertyInstance>.toMap(): Map<String, Any?> =
        entries.mapNotNull { (variableId, propertyInstance) ->
            if (propertyInstance !is ResolvedProperty) return@mapNotNull null
            variableId.relativeString(pack.id) to propertyInstance.value
        }.toMap()

    val rootScope = properties[PropertyScope.Pack]?.toMap()
    val moduleScope = modulePath?.let {
        properties[PropertyScope.Module(modulePath)]?.toMap()
    }
    return listOfNotNull(rootScope, moduleScope).toStack()
}

// TODO relativize dynamic variableIds
internal fun Project.dynamicVariables(
    modulePath: String?,
    variables: Variables,
): Map<String, Any?> {
    val resolved = mutableMapOf<String, Any?>()
    val dynamicProperties = listOfNotNull(
        properties[PropertyScope.Pack]?.entries,
        modulePath?.let { properties[PropertyScope.Module(modulePath)] }?.entries,
    ).flatten().mapNotNull { (_, propertyInstance) ->
        if (propertyInstance !is DynamicProperty) return@mapNotNull null
        propertyInstance
    }.toMutableList()
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
                // lists should accept multiple element assignments or a single list assignment
                // TODO leverage the type system better here, this is messy
                if (property.descriptor.type.isList()) {
                    property.assignments.map {
                        property.evaluate(it, property.descriptor.type.elementType!!)
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
            resolved[property.descriptor.key] = evalResult
            iterator.remove()
        }
        evaluationFailed = initialSize == dynamicProperties.size
    }

    return resolved
}
