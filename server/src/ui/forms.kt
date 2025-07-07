package org.jetbrains.kastle.server.ui

import kotlinx.html.*
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.Property
import org.jetbrains.kastle.PropertyType
import org.jetbrains.kastle.utils.isTruthy

fun HTML.packPropertiesHtml(pack: PackDescriptor) {
    body {
        val properties = pack.properties.filter { !it.hidden }
        if (properties.isNotEmpty()) {
            div("properties ${properties.shirtSize}") {
                id = "properties/${pack.id}"
                h3 { +pack.name }
                for (property in properties)
                    propertyInputAndLabel(pack, property)
            }
        }
    }
}

private val List<*>.shirtSize: String get() = when (size) {
    0 -> "xs"
    in 1..4 -> "sm"
    in 5..8 -> "md"
    else -> "lg"
}

private fun FlowContent.propertyInputAndLabel(pack: PackDescriptor, property: Property) {
    val inputId = "property/${pack.id}/${property.key}"

    div("field") {
        label {
            htmlFor = inputId
            +(property.label ?: property.key)
        }
        propertyInput(
            property.type,
            property.default,
            inputId
        )
    }
}

private fun DIV.propertyInput(
    type: PropertyType,
    default: Any?,
    inputId: String
) {
    when (type) {
        PropertyType.Long,
        PropertyType.Int,
        PropertyType.Float,
        PropertyType.Double -> input(InputType.number) {
            id = inputId
            default?.let { value = it.toString() }
        }

        PropertyType.String -> input(InputType.text) {
            id = inputId
            default?.let { value = it.toString() }
        }

        PropertyType.Boolean -> input(InputType.checkBox) {
            id = inputId
            checked = default.isTruthy()
        }

        is PropertyType.Enum -> select {
            id = inputId
            for (value in type.values) {
                option {
                    +value
                    selected = value == default
                }
            }
        }

        is PropertyType.Nullable -> {
            propertyInput(type.type, default, inputId)
        }

        is PropertyType.List -> {
            div {
                +"TODO"
            }
        }

        is PropertyType.Object -> {
            div {
                +"TODO"
            }
        }
    }
}