package com.fsk.threadwire.session

/**
 * [isTransportLevel] distinguishes a genuine connection failure (an uncaught
 * [com.fsk.threadwire.transport.ChatTransportException] from `streamEvents`, e.g. retry
 * exhaustion) from an in-band `{"type":"error",...}` protocol event - both are folded
 * into [ChatState.lastError] the same way (design doc §2.7 graceful degradation applied
 * at the stream level), but callers may want to tell them apart.
 */
data class ChatErrorInfo(
    val code: String?,
    val message: String?,
    val isTransportLevel: Boolean,
)

/**
 * The observable state of one [ChatSession] - design doc §5: `ChatSession state machine
 * (StateFlow<ChatState>)`. This shape isn't specified anywhere in the design doc beyond
 * that one line; it's designed from scratch in M1, folded by [ChatStateReducer].
 */
data class ChatState(
    val phase: SessionPhase = SessionPhase.AiActive,
    val messages: List<ChatMessage> = emptyList(),
    val isAwaitingResponse: Boolean = false,
    val lastError: ChatErrorInfo? = null,
    val lastHandoffEndReason: String? = null,
)
