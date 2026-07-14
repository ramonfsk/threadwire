package com.fsk.threadwire.session

/**
 * Minimal, pre-headers/pre-context view of an outgoing turn, handed to
 * [ChatContextProvider] so it can decide what this specific request needs (e.g. token
 * refresh). Deliberately not [com.fsk.threadwire.transport.TransportRequest] (the
 * fully-assembled HTTP request) - headers/contextPayload don't exist yet at this
 * point; producing them is [ChatContextProvider]'s job. Kept minimal on purpose -
 * attachments/audio are M3 scope, not modeled here.
 */
data class ChatRequest(
    val sessionId: String,
    val message: String,
)
