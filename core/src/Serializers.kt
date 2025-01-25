package org.jetbrains.kastle

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
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

class FeatureIdSerializer: CustomParserSerializer<FeatureId>(FeatureId::class, FeatureId::parse)
class SlotIdSerializer: CustomParserSerializer<SlotId>(SlotId::class, SlotId::parse)
class RevisionSerializer: CustomParserSerializer<Revision>(Revision::class, Revision::parse)
class VersionRangeSerializer: CustomParserSerializer<VersionRange>(VersionRange::class, VersionRange::parse)
class SemanticVersionSerializer: CustomParserSerializer<SemanticVersion>(SemanticVersion::class, SemanticVersion::parse)
class SourcePositionSerializer: CustomParserSerializer<SourcePosition>(SourcePosition::class, SourcePosition::parse)
class PropertyTypeSerializer: CustomParserSerializer<PropertyType>(PropertyType::class, PropertyType::parse)