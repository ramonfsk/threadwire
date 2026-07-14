package com.fsk.threadwire.session

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * One logical turn's worth of interleaved parts (design doc §4.3/§4.4 - text/tool/card
 * parts are interleaved parts of a single turn, not separate messages). [localId] is
 * assigned by [ChatStateReducer], not carried on the wire.
 */
data class ChatMessage(
    val localId: String,
    val author: MessageAuthor,
    val parts: List<MessagePart>,
    val isComplete: Boolean,
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
