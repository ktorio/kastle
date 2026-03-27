package org.jetbrains.kastle

import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.reflect.KClass

open class CustomParserSerializer<T: Any>(
    override val descriptor: SerialDescriptor,
    private val parse: (String) -> T
): KSerializer<T> {
    constructor(
        type: KClass<T>,
        parse: (String) -> T
    ): this(
        PrimitiveSerialDescriptor(
            type.simpleName ?: "CustomParser",
            PrimitiveKind.STRING
        ),
        parse
    )

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): T =
        parse(decoder.decodeString())
}

class PackIdSerializer: CustomParserSerializer<PackId>(PackId::class, PackId::parse)
class SlotIdSerializer: CustomParserSerializer<SlotId>(SlotId::class, SlotId::parse)
class VariableIdSerializer: CustomParserSerializer<VariableId>(VariableId::class, VariableId::parse)
class RevisionSerializer: CustomParserSerializer<Revision>(Revision::class, Revision::parse)
class VersionRangeSerializer: CustomParserSerializer<VersionRange>(VersionRange::class, VersionRange::parse)
class DependencySerializer: CustomParserSerializer<Dependency>(Dependency::class, Dependency::parse)
class ArtifactDependencySerializer: CustomParserSerializer<ArtifactDependency>(ArtifactDependency::class, ArtifactDependency::parse)
class SemanticVersionSerializer: CustomParserSerializer<SemanticVersion>(SemanticVersion::class, SemanticVersion::parse)
//class SourcePositionSerializer: CustomParserSerializer<SourcePosition>(SourcePosition::class, SourcePosition::parse)
class BlockPositionSerializer: CustomParserSerializer<BlockPosition>(BlockPosition::class, BlockPosition::parse)
class PropertyTypeSerializer: CustomParserSerializer<PropertyType>(PropertyType::class, PropertyType::parse)
class SourceImportSerializer: CustomParserSerializer<SourceImport>(SourceImport::class, SourceImport::parse)
class CatalogReferenceSerializer: CustomParserSerializer<CatalogReference>(CatalogReference::class, CatalogReference::parse)

/**
 * Handles both { module } and { group, name }.
 */
class CatalogArtifactSerializer : KSerializer<CatalogArtifact> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("CatalogArtifact") {
            element<String>("module", isOptional = true)
            element<String>("group", isOptional = true)
            element<String>("name", isOptional = true)
            element<CatalogVersion>("version")
        }

    override fun serialize(encoder: Encoder, value: CatalogArtifact) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.module)
            encodeSerializableElement(descriptor, 3, CatalogVersionSerializer(), value.version)
            if (value.builtIn) {
                encodeBooleanElement(descriptor, 4, value.builtIn)
            }
        }
    }

    override fun deserialize(decoder: Decoder): CatalogArtifact {
        var module: String? = null
        var group: String? = null
        var name: String? = null
        var version: CatalogVersion? = null
        var builtIn = false

        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> module = decodeStringElement(descriptor, index)
                    1 -> group = decodeStringElement(descriptor, index)
                    2 -> name = decodeStringElement(descriptor, index)
                    3 -> version = decodeSerializableElement(
                        descriptor,
                        index,
                        CatalogVersionSerializer()
                    )
                    4 -> builtIn = decodeBooleanElement(descriptor, index)
                    kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
        }

        val finalModule = module ?: run {
            require(group != null && name != null) {
                "CatalogArtifact must contain either module or both group and name"
            }
            "$group:$name"
        }

        return CatalogArtifact(
            module = finalModule,
            version = requireNotNull(version) { "CatalogArtifact.version is required" },
            builtIn = builtIn
        )
    }
}

/**
 * Handles numbers and references for catalog versions.
 */
class CatalogVersionSerializer: KSerializer<CatalogVersion> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor(
        "CatalogVersion",
        SerialKind.CONTEXTUAL
    )
    private val refDescriptor: SerialDescriptor = buildClassSerialDescriptor("CatalogVersion.Ref") {
        element("ref", PrimitiveSerialDescriptor("CatalogVersion.Ref.ref", PrimitiveKind.STRING))
    }
    override fun serialize(
        encoder: Encoder,
        value: CatalogVersion
    ) {
        when (value) {
            is CatalogVersion.Number -> encoder.encodeString(value.number)
            is CatalogVersion.Ref -> {
                val compositeEncoder = encoder.beginStructure(refDescriptor)
                compositeEncoder.encodeStringElement(refDescriptor, 0, value.ref)
                compositeEncoder.endStructure(refDescriptor)
            }
        }
    }
    override fun deserialize(decoder: Decoder): CatalogVersion {
        return try {
            // Try decoding structure
            val compositeDecoder = decoder.beginStructure(refDescriptor)
            val index = compositeDecoder.decodeElementIndex(refDescriptor)
            val ref = compositeDecoder.decodeStringElement(refDescriptor, index)
            compositeDecoder.endStructure(refDescriptor)
            CatalogVersion.Ref(ref)
        } catch (e: Exception) {
            // If decoding as class fails, try to decode as a string
            CatalogVersion.Number(decoder.decodeString())
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
class ByteStringSerializer : KSerializer<ByteString> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ByteString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteString) {
        val base64 = Base64.encode(value.toByteArray())
        encoder.encodeString(base64)
    }

    override fun deserialize(decoder: Decoder): ByteString {
        val base64 = decoder.decodeString()
        val bytes = Base64.decode(base64)
        return ByteString(bytes)
    }
}
