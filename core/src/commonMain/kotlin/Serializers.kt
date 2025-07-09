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
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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
class CatalogVersionSerializer: CustomParserSerializer<CatalogVersion>(CatalogVersion::class, CatalogVersion::parse)


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