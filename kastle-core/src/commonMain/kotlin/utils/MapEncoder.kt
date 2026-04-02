package org.jetbrains.kastle.utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Convert any serializable type into `Map<String, Any?>`, which can be used with
 * our little scripting engine.
 */
inline fun <reified T: Any> T.encodeToMap(
    serializersModule: SerializersModule = EmptySerializersModule()
): Map<String, Any?> = encodeToMap(serializersModule, typeOf<T>(), this)

fun encodeToMap(serializersModule: SerializersModule, type: KType, item: Any): Map<String, Any?> {
    val serializer = serializersModule.serializer(type)
    val encoder = MapEncoder(serializersModule)
    encoder.encodeSerializableValue(serializer, item)
    return encoder.result()
}

@OptIn(ExperimentalSerializationApi::class)
class MapEncoder(override val serializersModule: SerializersModule) : AbstractEncoder() {
    private sealed interface Container {
        val value: Any?
        fun put(key: String?, value: Any?)

        data class Object(override val value: MutableMap<String, Any?>) : Container {
            override fun put(key: String?, value: Any?) {
                if (key == null) error("Missing property name while encoding")
                this.value[key] = value
            }
        }
        data class Map(override val value: MutableMap<String, Any?>) : Container {
            var key: String? = null
            override fun put(key: String?, value: Any?) {
                if (this.key == null) {
                    this.key = value.toString()
                } else {
                    this.value[this.key!!] = value
                    this.key = null
                }
            }
        }
        data class List(override val value: MutableList<Any?>) : Container {
            override fun put(key: String?, value: Any?) {
                this.value += value
            }
        }
    }

    private val stack = ArrayDeque<Container>()
    private var currentName: String? = null
    private var root: Any? = null

    @Suppress("UNCHECKED_CAST")
    fun result(): Map<String, Any?> =
        root as? Map<String, Any?> ?: emptyMap()

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        // initialize the new container
        val container = when (descriptor.kind) {
            StructureKind.LIST -> Container.List(mutableListOf())
            StructureKind.CLASS,
            StructureKind.OBJECT -> Container.Object(mutableMapOf())
            StructureKind.MAP -> Container.Map(mutableMapOf())
            else -> error("Unsupported structure kind: ${descriptor.kind}")
        }
        // link it to the parent, as map value or list element
        stack.lastOrNull()?.put(currentName, container.value)
        // pop it onto the stack
        stack.addLast(container)
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        val value = stack.removeLast().value
        if (stack.isEmpty()) {
            root = value
            return
        }
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        currentName = descriptor.getElementName(index)
        return true
    }

    override fun encodeNull() = putValue(null)
    override fun encodeBoolean(value: Boolean) = putValue(value)
    override fun encodeByte(value: Byte) = putValue(value)
    override fun encodeShort(value: Short) = putValue(value)
    override fun encodeInt(value: Int) = putValue(value)
    override fun encodeLong(value: Long) = putValue(value)
    override fun encodeFloat(value: Float) = putValue(value)
    override fun encodeDouble(value: Double) = putValue(value)
    override fun encodeChar(value: Char) = putValue(value)
    override fun encodeString(value: String) = putValue(value)
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) = putValue(enumDescriptor.getElementName(index))

    override fun encodeInline(descriptor: SerialDescriptor): AbstractEncoder = this

    private fun putValue(value: Any?) {
        // skip encoding nulls
        if (value == null) return

        if (stack.isEmpty()) {
            root = value
            return
        }
        stack.last().put(currentName, value)
    }
}
