package org.jetbrains.kastle.ui

import kotlinx.html.*
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.Property
import org.jetbrains.kastle.PropertyType
import org.jetbrains.kastle.utils.isTruthy

fun HTML.packPropertiesHtml(pack: PackDescriptor) {
    body {
        if (pack.properties.isNotEmpty()) {
            div("properties ${pack.properties.shirtSize}") {
                id = "properties/${pack.id}"
                h3 { +pack.name }
                for (property in pack.properties)
                    propertyInput(pack, property)
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

private fun FlowContent.propertyInput(pack: PackDescriptor, property: Property) {
    val inputId = "property/${pack.id}/${property.key}"
    div("field") {
        label {
            htmlFor = inputId
            +property.key
        }
        when (val type = property.type) {
            PropertyType.Long,
            PropertyType.Int,
            PropertyType.Float,
            PropertyType.Double -> input(InputType.number) {
                id = inputId
                property.default?.let { value = it.toString() }
            }

            PropertyType.String -> input(InputType.text) {
                id = inputId
                property.default?.let { value = it }
            }

            PropertyType.Boolean -> input(InputType.checkBox) {
                id = inputId
                checked = property.default.isTruthy()
            }

            is PropertyType.Enum -> select {
                id = inputId
                for (value in type.values) {
                    option {
                        +value
                        selected = value == property.default
                    }
                }
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
}