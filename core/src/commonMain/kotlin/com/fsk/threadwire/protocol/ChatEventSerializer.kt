package com.fsk.threadwire.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Dispatches each SSE `data:` payload to the right [ChatEvent] subtype based on its
 * `"type"` field. A fixed `@SerialName`-per-class discriminator (kotlinx.serialization's
 * default sealed-polymorphism mechanism) can't express the dynamic `"data-<suffix>"`
 * prefix used by custom parts (design doc §4.3), so this reads the raw JSON tree first
 * and picks a deserializer by hand instead.
 */
internal object ChatEventSerializer : JsonContentPolymorphicSerializer<ChatEvent>(ChatEvent::class) {

    private val literalDeserializers: Map<String, DeserializationStrategy<ChatEvent>> = mapOf(
        "text-start" to ChatEvent.TextStart.serializer(),
        "text-delta" to ChatEvent.TextDelta.serializer(),
        "text-end" to ChatEvent.TextEnd.serializer(),
        "tool-input-start" to ChatEvent.ToolInputStart.serializer(),
        "tool-input-delta" to ChatEvent.ToolInputDelta.serializer(),
        "tool-input-available" to ChatEvent.ToolInputAvailable.serializer(),
        "finish" to ChatEvent.Finish.serializer(),
        "card-start" to ChatEvent.CardStart.serializer(),
        "card-update" to ChatEvent.CardUpdate.serializer(),
        "card-end" to ChatEvent.CardEnd.serializer(),
        "handoff-start" to ChatEvent.HandoffStart.serializer(),
        "handoff-agent-joined" to ChatEvent.HandoffAgentJoined.serializer(),
        "handoff-end" to ChatEvent.HandoffEnd.serializer(),
        "error" to ChatEvent.Error.serializer(),
    )

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out ChatEvent> {
        val type = runCatching { element.jsonObject["type"]?.jsonPrimitive?.contentOrNull }.getOrNull()

        literalDeserializers[type]?.let { return it }

        return if (type != null && type.startsWith("data-")) CustomDeserializer else UnknownDeserializer
    }

    // Must be a full KSerializer, not just DeserializationStrategy: kotlinx.serialization's
    // JsonContentPolymorphicSerializer.deserialize() casts selectDeserializer()'s result to
    // KSerializer<T> internally, regardless of the narrower DeserializationStrategy type the
    // abstract method signature accepts - a DeserializationStrategy-only implementation
    // compiles fine but throws ClassCastException at runtime.
    private object CustomDeserializer : KSerializer<ChatEvent> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.fsk.threadwire.protocol.ChatEvent.Custom")

        override fun deserialize(decoder: Decoder): ChatEvent {
            val obj = decoder.decodeSerializableValue(JsonElement.serializer()).jsonObject
            val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "data-unknown"
            val id = obj["id"]?.jsonPrimitive?.contentOrNull
            return ChatEvent.Custom(type = type, id = id, raw = obj)
        }

        override fun serialize(encoder: Encoder, value: ChatEvent) {
            require(value is ChatEvent.Custom) { "CustomDeserializer can only serialize ChatEvent.Custom" }
            encoder.encodeSerializableValue(JsonObject.serializer(), value.raw)
        }
    }

    private object UnknownDeserializer : KSerializer<ChatEvent> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.fsk.threadwire.protocol.ChatEvent.Unknown")

        override fun deserialize(decoder: Decoder): ChatEvent {
            val obj = decoder.decodeSerializableValue(JsonElement.serializer()).jsonObject
            val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "unknown"
            return ChatEvent.Unknown(type = type, raw = obj)
        }

        override fun serialize(encoder: Encoder, value: ChatEvent) {
            require(value is ChatEvent.Unknown) { "UnknownDeserializer can only serialize ChatEvent.Unknown" }
            encoder.encodeSerializableValue(JsonObject.serializer(), value.raw)
        }
    }
}
