package com.fsk.threadwire.session

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * One logical turn's worth of interleaved parts (design doc §4.3/§4.4 - text/tool/card
 * parts are interleaved parts of a single turn, not separate messages). [localId] is
 * assigned by [ChatStateReducer], not carried on the wire - a live message gets a
 * `"live_<n>"` id from [ChatState.nextLocalMessageSeq] (M2.5; decoupled from list
 * position so prepending [ChatHistoryProvider] pages never shifts/collides with an
 * already-assigned id), while a history message reuses its server-assigned
 * [HistoryMessage.remoteId] directly - the reserved `"live_"` prefix makes collision
 * between the two impossible as long as no BFF hands out remote ids starting with it.
 */
data class ChatMessage(
    val localId: String,
    val author: MessageAuthor,
    val parts: List<MessagePart>,
    val isComplete: Boolean,
    /** Set on the triggering USER message when its turn ends in an error (M2.5) - lets a
     *  retry affordance re-send the same message instead of appending a new one. */
    val deliveryFailed: Boolean = false,
    /** Client-clock epoch millis stamped when the message first entered the UI stream
     *  (M2.6): the user's send time, or the assistant/agent message's first-chunk time.
     *  0 means "unknown" - history messages (no server-provided time yet) keep 0, and the
     *  UI shows no timestamp for those. A server-provided time is a future protocol
     *  concern; this is deliberately the local-clock v1. */
    val timestampMillis: Long = 0L,
)

/**
 * A single part within a [ChatMessage]. [Card]/[ToolCall] payloads stay opaque
 * ([JsonElement]/[JsonObject]) - interpreting them is M4's (card schema) job, not this
 * layer's (design doc §2 principle 6).
 */
sealed interface MessagePart {

    data class Text(val id: String, val text: String, val isComplete: Boolean) : MessagePart

    data class ToolCall(
        val toolCallId: String,
        val inputText: String,
        val input: JsonElement?,
        val isComplete: Boolean,
    ) : MessagePart

    /** [body] is a full replace on every `card-update`, never a merge - see [ChatStateReducer]. */
    data class Card(
        val id: String,
        val version: Int,
        val body: JsonObject?,
        val isComplete: Boolean,
    ) : MessagePart

    /** A `data-<suffix>` custom part, reconciled by [id] exactly like [Card]. */
    data class Custom(val type: String, val id: String?, val raw: JsonObject) : MessagePart

    /** Forward-compatible catch-all - never dropped, so a newer BFF can't crash an older client. */
    data class Unknown(val type: String, val raw: JsonObject) : MessagePart
}
