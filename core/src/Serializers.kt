package org.jetbrains.kastle

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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