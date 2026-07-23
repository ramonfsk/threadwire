package com.fsk.threadwire.session

/**
 * Host-supplied session-history fetch capability (M2.5) - mirrors [ChatContextProvider]'s
 * shape and philosophy exactly: `:core` never makes the actual network call itself and
 * never assumes a wire format (design doc §2 principle 6). The host implements
 * [fetchHistory] against whatever REST/GraphQL endpoint their BFF already exposes and
 * hands back an already-parsed [ChatHistoryPage]. Server-fetched only, by design - no
 * local on-device persistence/KV layer in `:core`.
 */
interface ChatHistoryProvider {
    suspend fun fetchHistory(request: ChatHistoryRequest): ChatHistoryPage
}

/** [cursor] is opaque, from a previous [ChatHistoryPage.nextCursor]; null requests the most recent page. */
data class ChatHistoryRequest(
    val sessionId: String,
    val cursor: String?,
)

/** One page of history, prepended in front of whatever's already loaded (oldest-first overall). */
data class ChatHistoryPage(
    val messages: List<HistoryMessage>,
    /** Null means there are no older pages left. */
    val nextCursor: String?,
)

/**
 * [remoteId] must be a globally-stable, server-assigned id, distinct from a live
 * [ChatMessage.localId] (which uses a reserved `"live_"` prefix specifically so it can
 * never collide with a [remoteId] - see [ChatMessage.localId]'s doc).
 */
data class HistoryMessage(
    val remoteId: String,
    val author: MessageAuthor,
    val parts: List<MessagePart>,
)
