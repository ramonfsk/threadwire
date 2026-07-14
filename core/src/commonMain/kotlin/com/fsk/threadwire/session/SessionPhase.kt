package com.fsk.threadwire.session

/**
 * Maps directly onto design doc §6.1's cycle:
 * ```
 * ai_active ──handoff-start──▶ handoff_pending ──agent-joined──▶ handoff_active
 *      ▲                                                               │
 *      └──────────────────── handoff-end (reason) ────────────────────┘
 * ```
 * The transport underneath stays SSE-only in M1 regardless of phase - no WebSocket, no
 * transport promotion/demotion (that's M6). This only models the *session* phase, which
 * `ChatSession` derives purely from the events M0 already parses.
 */
sealed interface SessionPhase {
    data object AiActive : SessionPhase
    data class HandoffPending(val reason: String) : SessionPhase
    data class HandoffActive(val reason: String, val agentName: String) : SessionPhase
}

/**
 * Authorship for a *newly starting* assistant/agent message, derived from the phase at
 * that instant. [MessageAuthor.USER] is never derived here - it's stamped directly by
 * [ChatSession.sendMessage] instead.
 */
fun SessionPhase.authorForNextMessage(): MessageAuthor = when (this) {
    is SessionPhase.AiActive -> MessageAuthor.AI
    // Defensive default: no message is expected to start mid-pending in the documented
    // flow, but degrade to AI rather than crash/throw if one somehow does.
    is SessionPhase.HandoffPending -> MessageAuthor.AI
    is SessionPhase.HandoffActive -> MessageAuthor.HUMAN_AGENT
}
