package com.fsk.threadwire.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Parses a single SSE `data:` payload into a [ChatEvent]. A malformed or unrecognized
 * frame must never break the stream (design doc §2.7, graceful degradation) - this
 * always returns *some* [ChatEvent], falling back to [ChatEvent.Unknown] instead of
 * throwing. Connection-level failures are the transport's concern, not this parser's.
 */
object ChatEventParser {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parse(rawData: String): ChatEvent {
        if (rawData.isBlank()) return ChatEvent.Unknown(type = "empty", raw = JsonObject(emptyMap()))

        return try {
            json.decodeFromString(ChatEventSerializer, rawData)
        } catch (e: SerializationException) {
            ChatEvent.Unknown(type = "parse-error", raw = rawPayload(rawData))
        } catch (e: IllegalArgumentException) {
            ChatEvent.Unknown(type = "malformed", raw = rawPayload(rawData))
        }
    }

    private fun rawPayload(rawData: String): JsonObject = buildJsonObject {
        put("raw", JsonPrimitive(rawData))
    }
}
