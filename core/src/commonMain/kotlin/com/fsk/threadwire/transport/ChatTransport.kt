package com.fsk.threadwire.transport

import com.fsk.threadwire.protocol.ChatEvent
import kotlinx.coroutines.flow.Flow

/**
 * A single, fully-assembled request to open a streamed chat turn against the
 * integrator's BFF. [body] is an opaque, already-serialized payload (design doc §2
 * principle 6) - this layer never parses or interprets business content, it only
 * transports it.
 *
 * Named `TransportRequest` (not `ChatRequest`) to avoid colliding with the
 * session-level `com.fsk.threadwire.session.ChatRequest` (M1) - that type represents
 * what's known *before* headers/context exist, since `ChatContextProvider` is what
 * produces them; `ChatSession` translates one into this once it has.
 */
data class TransportRequest(
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
     * with the same id into one logical card) is NOT done here - that's
     * `ChatStateReducer`'s job (M1); this only emits the raw parsed sequence.
     */
    fun streamEvents(request: TransportRequest): Flow<ChatEvent>
}
