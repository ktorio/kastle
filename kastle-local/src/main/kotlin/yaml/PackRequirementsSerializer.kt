package org.jetbrains.kastle.yaml

import com.charleskorn.kaml.YamlInput
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlScalar
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import org.jetbrains.kastle.PackId
import org.jetbrains.kastle.PackRequirement

/**
 * Allows for writing pack requirements in a more convenient mapping way.
 */
class PackRequirementYamlSerializer: KSerializer<PackRequirement> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor(
        "PackRequirement",
        SerialKind.CONTEXTUAL
    )

    private val modulesSerializer = MapSerializer(String.serializer(), String.serializer())

    override fun serialize(
        encoder: Encoder,
        value: PackRequirement
    ) {
        if (value.modules.isEmpty()) {
            encoder.encodeString(value.packId.toString())
        } else {
            val singleEmptyKey = value.modules.size == 1 && value.modules.containsKey("")
            val mapDescriptor = buildClassSerialDescriptor("PackRequirement.Object") {
                if (singleEmptyKey) {
                    element(value.packId.toString(), String.serializer().descriptor)
                } else {
                    element(value.packId.toString(), modulesSerializer.descriptor)
                }
            }
            val composite = encoder.beginStructure(mapDescriptor)
            if (singleEmptyKey) {
                composite.encodeStringElement(mapDescriptor, 0, value.modules.getValue(""))
            } else {
                composite.encodeSerializableElement(mapDescriptor, 0, modulesSerializer, value.modules)
            }
            composite.endStructure(mapDescriptor)
        }
    }

    override fun deserialize(decoder: Decoder): PackRequirement {
        if (decoder is YamlInput) {
            return when (val node = decoder.node) {
                is YamlScalar -> PackRequirement(
                    packId = PackId.parse(node.content),
                    modules = emptyMap(),
                )
                is YamlMap -> {
                    val entry = node.entries.entries.singleOrNull()
                        ?: error("Expected a single key for PackRequirement, got: ${node.entries.keys}")
                    val packId = PackId.parse(entry.key.content)
                    val modules: Map<String, String> = when (val value = entry.value) {
                        is YamlScalar -> mapOf("" to value.content)
                        is YamlMap -> value.entries.entries.associate {
                            it.key.content to it.value.yamlScalar.content
                        }
                        else -> error("Expected a string or map for PackRequirement value, got: $value")
                    }
                    PackRequirement(packId = packId, modules = modules)
                }
                else -> error("Expected a string or map for PackRequirement, got: $node")
            }
        }
        // Fallback: try to decode as a string first, then as a structure
        return try {
            PackRequirement(
                packId = PackId.parse(decoder.decodeString()),
                modules = emptyMap(),
            )
        } catch (_: Exception) {
            val structDescriptor = buildClassSerialDescriptor("PackRequirement.Object") {
                element("packId", String.serializer().descriptor)
                element("modules", modulesSerializer.descriptor)
            }
            var packId: PackId? = null
            var modules: Map<String, String> = emptyMap()
            decoder.decodeStructure(structDescriptor) {
                while (true) {
                    when (val index = decodeElementIndex(structDescriptor)) {
                        0 -> packId = PackId.parse(decodeStringElement(structDescriptor, index))
                        1 -> modules = decodeSerializableElement(
                            structDescriptor, index, modulesSerializer
                        )
                        kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE -> break
                        else -> error("Unexpected index: $index")
                    }
                }
            }
            PackRequirement(
                packId = requireNotNull(packId) { "PackRequirement.packId is required" },
                modules = modules,
            )
        }
    }
}
