package org.jetbrains.kastle

import kotlinx.serialization.Serializable
import org.jetbrains.kastle.utils.trimAngleBrackets
import org.jetbrains.kastle.utils.trimBraces
import kotlin.text.toBooleanStrict

@Serializable
data class Property(
    val key: String,
    val type: PropertyType = PropertyType.String,
    val default: String? = null,
    val description: String? = null,
) {
    override fun toString(): String = buildString {
        append("$key: $type")
        if (default != null) append(" = $default")
        if (description != null) append(" /* $description */")
    }
}

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

    fun parse(text: kotlin.String): Any

    data object String: PropertyType {
        override fun parse(text: kotlin.String) = text
        override fun toString() = "string"
    }

    data object Boolean: PropertyType {
        override fun parse(text: kotlin.String): Any = text.toBooleanStrict()
        override fun toString() = "boolean"
    }

    data object Int: PropertyType {
        override fun parse(text: kotlin.String) = text.toInt()
        override fun toString() = "int"
    }

    data object Long: PropertyType {
        override fun parse(text: kotlin.String) = text.toLong()
        override fun toString() = "long"
    }

    data object Float: PropertyType {
        override fun parse(text: kotlin.String) = text.toFloat()
        override fun toString() = "float"
    }

    data object Double: PropertyType {
        override fun parse(text: kotlin.String): Any = text.toDouble()
        override fun toString() = "double"
    }

    data class List(val elementType: PropertyType): PropertyType {
        override fun parse(text: kotlin.String) = text.split(Regex("\\s*,\\s*")).map(elementType::parse)
        override fun toString() = "list<$elementType>"
    }

    data class Enum(val values: Collection<kotlin.String>): PropertyType {
        override fun parse(text: kotlin.String) =
            if (text in values) text
            else throw IllegalArgumentException("Invalid enum value: $text, expected one of $values")
        override fun toString() = "enum{${values.joinToString(", ")}}"
    }

    // TODO instead of qualified name we should use some serialization format
    data class Type(val qualifiedName: QualifiedName): PropertyType {
        override fun parse(text: kotlin.String): Any = TODO()
        override fun toString() = "type<$qualifiedName>"
    }
}