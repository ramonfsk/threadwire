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
    /** Position-independent id source for live messages (M2.5) - decoupled from
     *  `messages.size` specifically so prepending a history page never shifts or
     *  collides with an already-assigned [ChatMessage.localId]. */
    val nextLocalMessageSeq: Int = 0,
    val isLoadingHistory: Boolean = false,
    /** Defaults false, not true: a session with no [ChatConfig.historyProvider] never
     *  runs a history fetch, so this must stay false forever for it - only a session
     *  that actually has a provider ever flips this via [ChatStateReducer.reduceHistoryPageLoaded]. */
    val hasMoreHistory: Boolean = false,
    val historyCursor: String? = null,
    /** Kept separate from [lastError] - a failed history fetch shouldn't surface
     *  through the turn-retry error banner. */
    val historyError: ChatErrorInfo? = null,
)
