package org.jetbrains.kastle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.utils.trimAngleBrackets
import org.jetbrains.kastle.utils.trimBraces

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

private const val STRING = "string"
private const val BOOLEAN = "boolean"
private const val INT = "int"
private const val LONG = "long"
private const val FLOAT = "float"
private const val DOUBLE = "double"
private const val ENUM = "enum"
private const val LIST = "list"
private const val OBJECT = "object"

@Serializable(PropertyTypeSerializer::class)
sealed interface PropertyType {
    companion object {
        fun parse(text: kotlin.String): PropertyType {
            if (text.endsWith('?'))
                return Nullable(parse(text.removeSuffix("?")))

            val word = text.split(Regex("\\W"), 2).firstOrNull()
                ?: throw IllegalArgumentException("Invalid property type: $text")
            val details by lazy { text.removePrefix(word).trim() }

            return when(word.lowercase()) {
                STRING -> String
                BOOLEAN -> Boolean
                INT -> Int
                LONG -> Long
                FLOAT -> Float
                DOUBLE -> Double
                ENUM -> Enum(details.trimBraces().split(Regex(",\\s*")))
                LIST -> List(parse(details.trimAngleBrackets()))
                OBJECT -> Object(Json.decodeFromString(details.trimBraces()))
                else -> throw IllegalArgumentException("Invalid property type: $text")
            }
        }
    }

    fun parse(text: kotlin.String): Any?

    data object String: PropertyType {
        override fun parse(text: kotlin.String) = text
        override fun toString() = STRING
    }

    data object Boolean: PropertyType {
        /**
         * Lenient parsing of property values
         */
        override fun parse(text: kotlin.String): Any =
            text.toBooleanStrictOrNull() ?:
            text.toIntOrNull()?.let { it != 0 } ?: text.isEmpty()
        override fun toString() = BOOLEAN
    }

    data object Int: PropertyType {
        override fun parse(text: kotlin.String) = text.toInt()
        override fun toString() = INT
    }

    data object Long: PropertyType {
        override fun parse(text: kotlin.String) = text.toLong()
        override fun toString() = LONG
    }

    data object Float: PropertyType {
        override fun parse(text: kotlin.String) = text.toFloat()
        override fun toString() = FLOAT
    }

    data object Double: PropertyType {
        override fun parse(text: kotlin.String): Any = text.toDouble()
        override fun toString() = DOUBLE
    }

    data class List(val elementType: PropertyType): PropertyType {
        override fun parse(text: kotlin.String) = text.split(Regex("\\s*,\\s*")).map(elementType::parse)
        override fun toString() = "list<$elementType>"
    }

    data class Enum(val values: Collection<kotlin.String>): PropertyType {
        override fun parse(text: kotlin.String): kotlin.String =
            if (text in values) text
            else throw IllegalArgumentException("Invalid enum value: $text, expected one of $values")
        override fun toString() = "enum{${values.joinToString(", ")}}"
    }

    data class Object(val properties: Map<String, PropertyType>): PropertyType {
        override fun parse(text: kotlin.String): Any =
            Json.decodeFromString<Map<String, String>>(text).let { stringMap ->
                stringMap.mapValues { (key, value) ->
                    properties[key]?.parse(value.toString())
                }
            }
        override fun toString() = "object${Json.encodeToString<Map<String, PropertyType>>(properties)}"
    }

    data class Nullable(val type: PropertyType): PropertyType {
        override fun parse(text: kotlin.String): Any? = when(text) {
            "null" -> null
            else -> type.parse(text)
        }

        override fun toString(): kotlin.String = "$type?"
    }
}