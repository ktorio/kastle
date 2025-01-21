package org.jetbrains.kastle

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class FeatureIdSerializer: KSerializer<FeatureId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FeatureId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: FeatureId) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): FeatureId =
        FeatureId.parse(decoder.decodeString())
}

class SlotIdSerializer: KSerializer<SlotId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SlotId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SlotId) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): SlotId =
        SlotId.parse(decoder.decodeString())
}

class SemanticVersionSerializer: KSerializer<SemanticVersion> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SemanticVersion", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SemanticVersion) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): SemanticVersion =
        SemanticVersion.parse(decoder.decodeString())
}

class SlotPositionSerializer: KSerializer<SourcePosition> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SlotPosition", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SourcePosition) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): SourcePosition =
        SourcePosition.parse(decoder.decodeString())
}