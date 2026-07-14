package com.fsk.threadwire.session

import com.fsk.threadwire.transport.ChatTransport
import com.fsk.threadwire.transport.TransportRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * A single chat session against the integrator's BFF - design doc §5:
 * `ChatSession state machine (StateFlow<ChatState>)`. Owns no persistent connection
 * (SSE is per-turn, design doc §6); [state] is available in its default form
 * immediately at construction.
 *
 * Depends on the [ChatTransport] *interface*, never a concrete transport - the
 * mechanism that keeps a future phase-aware SSE/WebSocket router (M6) a non-breaking
 * substitution, per design doc §6 ("switching... is internal and invisible to the UI").
 */
class ChatSession(
    private val transport: ChatTransport,
    private val config: ChatConfig,
    private val sessionId: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var currentTurn: Job? = null

    /**
     * Sends a text-only user turn (attachments/audio are M3 scope, not modeled here).
     * A no-op while a turn is already in flight (provisional default - no doc
     * precedent; revisit if product wants queuing instead).
     */
    fun sendMessage(text: String) {
        if (_state.value.isAwaitingResponse) return

        _state.update { state ->
            val userMessage = ChatMessage(
                localId = "msg_${state.messages.size}",
                author = MessageAuthor.USER,
                parts = listOf(MessagePart.Text(id = "user_${state.messages.size}", text = text, isComplete = true)),
                isComplete = true,
            )
            state.copy(messages = state.messages + userMessage, isAwaitingResponse = true)
        }

        currentTurn = scope.launch {
            try {
                // Called fresh on every request (design doc §7: "covers cases like token
                // refresh") - never cached across sendMessage calls.
                val contextRequest = ChatRequest(sessionId = sessionId, message = text)
                val headers = config.contextProvider.headers(contextRequest)
                val contextPayload = config.contextProvider.contextPayload(contextRequest)
                val transportRequest = TransportRequest(
                    url = config.baseUrl,
                    headers = headers,
                    body = buildRequestBody(text, contextPayload),
                )

                transport.streamEvents(transportRequest).collect { event ->
                    _state.update { ChatStateReducer.reduce(it, event) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { ChatStateReducer.reduceTransportError(it, e.message) }
            }
        }
    }

    /** Cancels only the in-flight turn (not the whole session) - design doc §14.1's "stop" affordance, no UI. */
    fun cancelCurrentTurn() {
        val turn = currentTurn ?: return
        currentTurn = null
        turn.cancel()
        _state.update { ChatStateReducer.reduceCancelled(it) }
    }

    /** Cancels the whole session. Idempotent. Hosts must call this on teardown. */
    fun close() {
        scope.cancel()
    }

    /**
     * `contextPayload` is wrapped, untouched, under the reserved `"context"` key
     * (design doc §7) - `:core` builds this envelope but never reads into its own
     * keys (principle 6). Exact envelope shape beyond that is a minimal, internal
     * proposal (see M1 plan's flagged decisions), not a documented wire contract.
     */
    private fun buildRequestBody(message: String, contextPayload: Map<String, Any?>): String {
        return buildJsonObject {
            put("message", JsonPrimitive(message))
            put("context", contextPayload.toJsonObject())
        }.toString()
    }
}

private fun Map<String, Any?>.toJsonObject(): JsonObject {
    val entries = this
    return buildJsonObject {
        for ((key, value) in entries) {
            put(key, value.toJsonElement())
        }
    }
}

/** Unrecognized value types fall back to `.toString()` rather than throwing - graceful degradation. */
private fun Any?.toJsonElement(): JsonElement = when (val value = this) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Map<*, *> -> buildJsonObject {
        for ((key, entryValue) in value) put(key.toString(), entryValue.toJsonElement())
    }
    is List<*> -> JsonArray(value.map { it.toJsonElement() })
    else -> JsonPrimitive(value.toString())
}
