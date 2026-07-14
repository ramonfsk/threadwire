package com.fsk.threadwire.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A single parsed part of the SSE event protocol (design doc §4). Reconciliation of
 * repeated IDs (e.g. a `card-update` replacing a prior `card-start`/`card-update` with
 * the same id) is intentionally not performed here - that's a stream-consumption
 * concern for the future session state machine (M1), not the transport/protocol layer.
 */
@Serializable(with = ChatEventSerializer::class)
sealed interface ChatEvent {

    @Serializable
    @SerialName("text-start")
    data class TextStart(val id: String) : ChatEvent

    @Serializable
    @SerialName("text-delta")
    data class TextDelta(val id: String, val delta: String) : ChatEvent

    @Serializable
    @SerialName("text-end")
    data class TextEnd(val id: String) : ChatEvent

    @Serializable
    @SerialName("tool-input-start")
    data class ToolInputStart(val toolCallId: String) : ChatEvent

    @Serializable
    @SerialName("tool-input-delta")
    data class ToolInputDelta(val toolCallId: String, val inputTextDelta: String) : ChatEvent

    // input stays an opaque JsonElement - the library never interprets tool payloads (design doc §2 principle 6).
    @Serializable
    @SerialName("tool-input-available")
    data class ToolInputAvailable(val toolCallId: String, val input: JsonElement) : ChatEvent

    @Serializable
    @SerialName("finish")
    data object Finish : ChatEvent

    // version/body stay opaque - card schema interpretation is M4 (design doc §8), not this layer.
    @Serializable
    @SerialName("card-start")
    data class CardStart(val id: String, val version: Int) : ChatEvent

    @Serializable
    @SerialName("card-update")
    data class CardUpdate(val id: String, val body: JsonObject) : ChatEvent

    @Serializable
    @SerialName("card-end")
    data class CardEnd(val id: String) : ChatEvent

    @Serializable
    @SerialName("handoff-start")
    data class HandoffStart(val reason: String) : ChatEvent

    @Serializable
    @SerialName("handoff-agent-joined")
    data class HandoffAgentJoined(val agentName: String) : ChatEvent

    // design doc §6.1: "handoff-end carries a reason field (agent_resolved | user_requested | timeout)".
    @Serializable
    @SerialName("handoff-end")
    data class HandoffEnd(val reason: String) : ChatEvent

    // Not in the design doc's concrete custom-event list, but §4.1 explicitly calls out an
    // `error` event as part of the prior art worth aligning with - a plausible in-band
    // protocol-level error, distinct from a transport-level connection failure.
    @Serializable
    @SerialName("error")
    data class Error(val code: String? = null, val message: String? = null) : ChatEvent

    /**
     * A `data-<suffix>` custom part (design doc §4.3) - reconciled by [id]. [type] retains
     * the full original string (e.g. "data-weather") since the suffix is not known ahead
     * of time and can't be a fixed `@SerialName`.
     */
    data class Custom(val type: String, val id: String?, val raw: JsonObject) : ChatEvent

    /**
     * Forward-compatible catch-all for any event type this client doesn't recognize yet,
     * so a newer BFF emitting a new event type never crashes an older client (same
     * graceful-degradation spirit as design doc §2.7 and the card schema's `version`
     * field in §8).
     */
    data class Unknown(val type: String, val raw: JsonObject) : ChatEvent
}
