package org.jetbrains.kastle.server.ui

import kotlinx.html.*
import kotlinx.html.consumers.delayed
import kotlinx.html.stream.HTMLStreamBuilder
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
            name = inputId
            default?.let { value = it.toString() }
        }

        PropertyType.String -> input(InputType.text) {
            name = inputId
            default?.let { value = it.toString() }
        }

        PropertyType.Boolean -> input(InputType.checkBox) {
            name = inputId
            checked = default.isTruthy()
        }

        is PropertyType.Enum -> select {
            name = inputId
            for (value in type.values) {
                option {
                    selected = value == default
                    +value
                }
            }
        }

        is PropertyType.Nullable -> {
            propertyInput(type.type, default, inputId)
        }

        is PropertyType.List -> {
            addRemove {
                propertyInput(type.elementType, null, inputId)
            }
        }

        is PropertyType.Object -> {
            for ((key, elementType) in type.properties)
                propertyInput(elementType, null, "$inputId/$key")
        }
    }
}

fun FlowContent.addRemove(content: DIV.() -> Unit) {
    val elementHtml = buildString {
        HTMLStreamBuilder(this, prettyPrint = false, xhtmlCompatible = false).delayed().apply {
            div {
                content()
                button {
                    onClick = "event.target.parentElement.remove();"
                    +"Remove"
                }
            }
        }
    }
    div("add-remove") {
        button {
            attributes["onclick"] = "event.preventDefault(); event.target.parentElement.insertAdjacentHTML('beforeend', '$elementHtml')"
            +"Add"
        }
    }
}