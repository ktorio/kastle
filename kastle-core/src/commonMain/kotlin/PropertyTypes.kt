package org.jetbrains.kastle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.utils.Expression
import org.jetbrains.kastle.utils.trimAngleBrackets
import org.jetbrains.kastle.utils.trimBraces
import kotlin.toString

sealed interface PropertyInstance {
    val descriptor: PropertyDescriptor
}

data class ResolvedProperty(
    override val descriptor: PropertyDescriptor,
    val value: Any?
) : PropertyInstance {
    override fun toString(): String = value.toString()
}

data class DynamicProperty(
    override val descriptor: PropertyDescriptor,
    val assignments: List<PropertyAssignment>
) : PropertyInstance {
    override fun toString(): String =
        when(val assignment = assignments.singleOrNull()) {
            null -> "[${assignments.joinToString(", ") { it.toString() }}]"
            else -> assignment.toString()
        }
}

data class UnresolvedProperty(
    override val descriptor: PropertyDescriptor,
) : PropertyInstance {
    override fun toString(): String = "???"
}

@Serializable
sealed interface  PropertyAssignment {
    val packId: PackId
    val key: VariableId
}

@Serializable
data class ExpressionAssignment(
    override val packId: PackId,
    override val key: VariableId,
    val expression: Expression,
) : PropertyAssignment {
    override fun toString() =
        expression.toString()
}

@Serializable
data class ValueAssignment(
    override val packId: PackId,
    override val key: VariableId,
    val value: String,
) : PropertyAssignment {
    override fun toString() = value
}

@Serializable
data class PropertyDescriptor(
    val key: String,
    val type: PropertyType = PropertyType.String,
    val default: String? = null,
    val label: String? = null,
    val hidden: Boolean = false,
) {
    override fun toString(): String = buildString {
        append("$key: $type")
        if (default != null) append(" = $default")
        if (label != null) append(" /* $label */")
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
private const val MAP = "map"

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
                ENUM -> Enum(details.trimBraces().trim().split(Regex(",\\s*")))
                LIST -> List(parse(details.trimAngleBrackets().trim()))
                MAP -> {
                    val (keyType, valueType) = details.trimAngleBrackets().split(Regex("\\s*,\\s*"), 2)
                    Map(parse(keyType), parse(valueType))
                }
                OBJECT -> Object(Json.decodeFromString(details.trimBraces()))
                else -> throw IllegalArgumentException("Invalid property type: $text")
            }
        }
    }

    fun parse(text: kotlin.String): Any?

    // TODO currently a no-op
    //      but we should actually cast here
    fun cast(value: Any?) = value

    fun isList() = this is List
    fun isNullable() = this is Nullable

    val elementType: PropertyType? get() = null

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

    data class List(override val elementType: PropertyType): PropertyType {
        override fun parse(text: kotlin.String) = text.split(Regex("\\s*,\\s*")).map(elementType::parse)
        override fun toString() = "list<$elementType>"
    }

    data class Enum(val values: Collection<kotlin.String>): PropertyType {
        override fun parse(text: kotlin.String): kotlin.String =
            if (text in values) text
            else throw IllegalArgumentException("Invalid enum value: $text, expected one of $values")
        override fun toString() = "enum{${values.joinToString(", ")}}"
    }

    // TODO string, parse
    data class Map(val keyType: PropertyType, val valueType: PropertyType): PropertyType {
        override fun parse(text: kotlin.String) =
            text.split(Regex("\\s*,\\s*")).associate {
                val (key, value) = it.split("=")
                keyType.parse(key) to valueType.parse(value)
            }
        override fun toString() = "map<$keyType, $valueType>"
    }

    data class Object(val properties: kotlin.collections.Map<String, PropertyType>): PropertyType {
        override fun parse(text: kotlin.String): Any =
            Json.decodeFromString<kotlin.collections.Map<String, String>>(text).let { stringMap ->
                stringMap.mapValues { (key, value) ->
                    properties[key]?.parse(value.toString())
                }
            }
        override fun toString() = "object{${properties.entries.joinToString(", ") { (key, value) -> 
            "$key: $value"}
        }}"
    }

    data class Nullable(val type: PropertyType): PropertyType {
        override fun parse(text: kotlin.String): Any? = when(text) {
            "null" -> null
            else -> type.parse(text)
        }

        override fun isList(): kotlin.Boolean =
            type.isList()

        override val elementType: PropertyType? =
            type.elementType

        override fun toString(): kotlin.String = "$type?"
    }
}
