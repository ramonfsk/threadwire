package com.fsk.threadwire.session

import com.fsk.threadwire.transport.ChatTransport
import com.fsk.threadwire.transport.SseChatTransport
import com.fsk.threadwire.transport.TransportRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
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
import kotlin.time.Clock

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

    init {
        // Server-fetched history only, no local persistence (M2.5, resolved scope
        // decision). Auto-loads the most recent page on construction so a reopened
        // session isn't empty; further pages are explicit via [loadOlderHistory].
        config.historyProvider?.let { provider ->
            _state.update { it.copy(isLoadingHistory = true) }
            scope.launch { loadHistoryPage(provider, cursor = null) }
        }
    }

    /**
     * Sends a text-only user turn (attachments/audio are M3 scope, not modeled here).
     * A no-op while a turn is already in flight (provisional default - no doc
     * precedent; revisit if product wants queuing instead).
     */
    fun sendMessage(text: String) {
        if (_state.value.isAwaitingResponse) return

        _state.update { state ->
            val userMessage = ChatMessage(
                localId = "live_${state.nextLocalMessageSeq}",
                author = MessageAuthor.USER,
                parts = listOf(MessagePart.Text(id = "user_${state.nextLocalMessageSeq}", text = text, isComplete = true)),
                isComplete = true,
                timestampMillis = Clock.System.now().toEpochMilliseconds(),
            )
            state.copy(
                messages = state.messages + userMessage,
                isAwaitingResponse = true,
                nextLocalMessageSeq = state.nextLocalMessageSeq + 1,
                // A stale error from a prior failed turn shouldn't linger once the host
                // moves on to a genuinely new message (as opposed to a retry).
                lastError = null,
            )
        }
        beginTurn(text)
    }

    /**
     * Re-sends the last failed turn's user message in place, instead of the UI-layer
     * pattern (both sample UIs used, pre-M2.5) of re-calling [sendMessage] with the same
     * text - which always appended a second bubble. No-op if nothing is currently
     * flagged failed, or a turn is already in flight. Zero parameters, so it needs no
     * Swift-facing overload.
     */
    fun retryLastFailedTurn() {
        if (_state.value.isAwaitingResponse) return
        val failed = _state.value.messages.lastOrNull { it.author == MessageAuthor.USER && it.deliveryFailed } ?: return
        val text = failed.parts.filterIsInstance<MessagePart.Text>().firstOrNull()?.text ?: return
        _state.update { ChatStateReducer.reduceRetryStarted(it) }
        beginTurn(text)
    }

    private fun beginTurn(text: String) {
        currentTurn = scope.launch {
            try {
                // Called fresh on every request (design doc §7: "covers cases like token
                // refresh") - never cached across calls.
                val contextRequest = ChatRequest(sessionId = sessionId, message = text)
                val headers = config.contextProvider.headers(contextRequest)
                val contextPayload = config.contextProvider.contextPayload(contextRequest)
                val transportRequest = TransportRequest(
                    url = config.baseUrl,
                    headers = headers,
                    body = buildRequestBody(text, contextPayload),
                )

                transport.streamEvents(transportRequest).collect { event ->
                    _state.update { ChatStateReducer.reduce(it, event, Clock.System.now().toEpochMilliseconds()) }
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

    /**
     * Explicit "load older messages" pagination call (M2.5). No-op if the host supplied
     * no [ChatHistoryProvider], a fetch is already in flight, or [ChatState.hasMoreHistory]
     * is already false. Zero parameters, so it needs no Swift-facing overload.
     */
    fun loadOlderHistory() {
        val provider = config.historyProvider ?: return
        val snapshot = _state.value
        if (snapshot.isLoadingHistory || !snapshot.hasMoreHistory) return
        _state.update { it.copy(isLoadingHistory = true) }
        scope.launch { loadHistoryPage(provider, cursor = snapshot.historyCursor) }
    }

    private suspend fun loadHistoryPage(provider: ChatHistoryProvider, cursor: String?) {
        try {
            val page = provider.fetchHistory(ChatHistoryRequest(sessionId, cursor))
            _state.update { ChatStateReducer.reduceHistoryPageLoaded(it, page) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update {
                it.copy(isLoadingHistory = false, historyError = ChatErrorInfo(code = null, message = e.message, isTransportLevel = true))
            }
        }
    }

    /** Cancels the whole session. Idempotent. Hosts must call this on teardown. */
    fun close() {
        scope.cancel()
    }

    /**
     * Callback-based alternative to collecting [state] directly - primarily for Swift
     * consumers, where collecting a raw `StateFlow`/suspend `Flow` isn't ergonomic
     * without extra tooling (no SKIE or equivalent is used in this project). Call
     * [ChatSubscription.close] to stop receiving updates; does not affect [state] or
     * the session itself.
     */
    fun observeState(onChange: (ChatState) -> Unit): ChatSubscription {
        val job = scope.launch { state.collect { onChange(it) } }
        return ChatSubscription { job.cancel() }
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

    companion object {
        /**
         * Convenience factory building a [ChatSession] with a default [SseChatTransport]
         * over a platform-appropriate [HttpClient]. `core/build.gradle.kts` scopes
         * exactly one client engine per target (OkHttp on Android, Darwin on iOS), so a
         * plain `HttpClient { }` call here resolves to the right engine automatically -
         * no expect/actual needed. Installs [SSE], then hands the engine config to
         * [ChatConfig.httpClientCustomizer] (design doc §7 - "allows mTLS/pinning")
         * before finalizing.
         *
         * A `companion object` function (not a top-level one) deliberately, so it has a
         * predictable Kotlin/Native Swift-export shape (`ChatSession.companion.create(...)`)
         * rather than relying on Kotlin's file-facade naming for top-level functions,
         * which isn't verified against a real Xcode build in this project.
         *
         * Hosts needing full control over transport/DI (tests, custom engines) can still
         * use the primary constructor directly.
         *
         * Two explicit overloads, not one `scope` parameter with a default value:
         * Kotlin default parameter values don't bridge to Swift/Obj-C (every argument
         * must be supplied explicitly at the call site), so `ChatSession.companion.create(config:sessionId:)`
         * would otherwise fail to compile from Swift with "missing argument for parameter 'scope'".
         */
        fun create(config: ChatConfig, sessionId: String): ChatSession =
            create(config, sessionId, CoroutineScope(SupervisorJob() + Dispatchers.Default))

        fun create(config: ChatConfig, sessionId: String, scope: CoroutineScope): ChatSession {
            val httpClient = HttpClient {
                install(SSE)
                config.httpClientCustomizer(this)
            }
            return ChatSession(SseChatTransport(httpClient), config, sessionId, scope)
        }
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
