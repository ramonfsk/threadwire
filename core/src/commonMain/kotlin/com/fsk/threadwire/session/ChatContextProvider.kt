package com.fsk.threadwire.session

/**
 * Host-supplied auth/context injection, called fresh before every request (design doc
 * §7 - "covers cases like token refresh"). [contextPayload] is domain context: an
 * opaque blob the library never interprets (design doc §2 principle 6) - `ChatSession`
 * only wraps it under a reserved `"context"` key in the outgoing body.
 */
interface ChatContextProvider {

    /** Transport/session metadata (auth, correlation ID, language, app version). */
    suspend fun headers(request: ChatRequest): Map<String, String>

    /** Opaque domain context - never parsed or validated by the library. */
    suspend fun contextPayload(request: ChatRequest): Map<String, Any?>
}
