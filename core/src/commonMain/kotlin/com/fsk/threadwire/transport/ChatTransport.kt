package com.fsk.threadwire.transport

import com.fsk.threadwire.protocol.ChatEvent
import kotlinx.coroutines.flow.Flow

/**
 * A single request to open a streamed chat turn against the integrator's BFF. [body] is
 * an opaque, already-serialized payload (design doc §2 principle 6) - this layer never
 * parses or interprets business content, it only transports it.
 *
 * Deliberately has no dependency on `ChatConfig`/`ChatContextProvider` (M1, not built
 * yet) - those will be responsible for constructing a [ChatRequest] later, so this
 * contract doesn't need to change when M1 lands.
 */
data class ChatRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String,
)

/**
 * Transports one chat turn and exposes it as a stream of parsed [ChatEvent]s. Kept as a
 * single-method interface so a future WebSocket implementation (M6, human handoff) can
 * plug in without callers changing - design doc §6: "ChatTransport is a single
 * interface; switching between the two implementations is internal and invisible to
 * the UI." No WebSocket code is added here; only the shape stays compatible with it.
 */
interface ChatTransport {

    /**
     * Opens (or reopens, on reconnect) the connection for [request] and emits every
     * parsed [ChatEvent] as it arrives. The returned [Flow] is cold: collecting it
     * starts the request. It completes normally after a [ChatEvent.Finish] event, or
     * throws (see [ChatTransportException]) if the connection fails and can't be
     * recovered per the implementation's reconnection policy.
     *
     * ID-based reconciliation of repeated parts (e.g. folding several `card-update`s
     * with the same id into one logical card) is NOT done here - that's the future
     * session state machine's job (M1); this only emits the raw parsed sequence.
     */
    fun streamEvents(request: ChatRequest): Flow<ChatEvent>
}
