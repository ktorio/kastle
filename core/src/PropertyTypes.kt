package org.jetbrains.kastle

import kotlinx.serialization.Serializable
import org.jetbrains.kastle.utils.trimAngleBrackets
import org.jetbrains.kastle.utils.trimBraces

@Serializable
data class Property(
    val key: String,
    val type: PropertyType = PropertyType.String,
    val default: String? = null,
    val description: String? = null,
)

@Serializable(PropertyTypeSerializer::class)
sealed interface PropertyType {
    companion object {
        fun parse(text: kotlin.String): PropertyType {
            val word = text.split(Regex("\\W"), 2).firstOrNull()
                ?: throw IllegalArgumentException("Invalid property type: $text")
            val details by lazy { text.removePrefix(word).trim() }

            return when(word.lowercase()) {
                "string" -> String
                "boolean" -> Boolean
                "int" -> Int
                "long" -> Long
                "float" -> Float
                "double" -> Double
                "enum" -> Enum(details.trimBraces().split(Regex(",\\s*")))
                "list" -> List(parse(details.trimAngleBrackets()))
                "type" -> Type(details.trimAngleBrackets())
                else -> throw IllegalArgumentException("Invalid property type: $text")
            }
        }
    }

    data object String: PropertyType {
        override fun toString() = "string"
    }

    data object Boolean: PropertyType {
        override fun toString() = "boolean"
    }

    data object Int: PropertyType {
        override fun toString() = "int"
    }

    data object Long: PropertyType {
        override fun toString() = "long"
    }

    data object Float: PropertyType {
        override fun toString() = "float"
    }

    data object Double: PropertyType {
        override fun toString() = "double"
    }

    data class List(val elementType: PropertyType): PropertyType {
        override fun toString() = "list<$elementType>"
    }

    data class Enum(val values: Collection<kotlin.String>): PropertyType {
        override fun toString() = "enum{${values.joinToString(", ")}}"
    }

    data class Type(val qualifiedName: QualifiedName): PropertyType {
        override fun toString() = "type<$qualifiedName>"
    }
}