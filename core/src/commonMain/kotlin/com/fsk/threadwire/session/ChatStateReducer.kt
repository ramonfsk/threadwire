package com.fsk.threadwire.session

import com.fsk.threadwire.protocol.ChatEvent

/**
 * Pure fold from ([ChatState], [ChatEvent]) to the next [ChatState]. Isolated from
 * [ChatSession] so the mapping rules below can be unit tested without coroutines/a
 * transport. One `streamEvents()` turn = one assistant/agent [ChatMessage] - text/tool/
 * card parts are interleaved parts of a single logical turn (design doc §4.3/§4.4;
 * `ChatEvent.Finish` is turn-scoped with no id, which is the concrete evidence for this).
 */
object ChatStateReducer {

    fun reduce(state: ChatState, event: ChatEvent, nowMillis: Long = 0L): ChatState = when (event) {
        is ChatEvent.TextStart -> state.appendPart(MessagePart.Text(event.id, "", isComplete = false), nowMillis)

        is ChatEvent.TextDelta -> state.updateInProgressPart(
            matches = { it is MessagePart.Text && it.id == event.id },
            transform = { (it as MessagePart.Text).copy(text = it.text + event.delta) },
        )

        is ChatEvent.TextEnd -> state.updateInProgressPart(
            matches = { it is MessagePart.Text && it.id == event.id },
            transform = { (it as MessagePart.Text).copy(isComplete = true) },
        )

        is ChatEvent.ToolInputStart -> state.appendPart(
            MessagePart.ToolCall(event.toolCallId, inputText = "", input = null, isComplete = false), nowMillis
        )

        is ChatEvent.ToolInputDelta -> state.updateInProgressPart(
            matches = { it is MessagePart.ToolCall && it.toolCallId == event.toolCallId },
            transform = { (it as MessagePart.ToolCall).copy(inputText = it.inputText + event.inputTextDelta) },
        )

        is ChatEvent.ToolInputAvailable -> state.updateInProgressPart(
            matches = { it is MessagePart.ToolCall && it.toolCallId == event.toolCallId },
            transform = { (it as MessagePart.ToolCall).copy(input = event.input, isComplete = true) },
        )

        is ChatEvent.CardStart -> state.appendPart(
            MessagePart.Card(event.id, event.version, body = null, isComplete = false), nowMillis
        )

        // Full replace, not merge - same ID-reconciliation philosophy as M0's parser
        // and design doc §4.3 ("resending the same ID replaces the previous part").
        is ChatEvent.CardUpdate -> {
            val hasMatch = state.inProgressMessage()?.parts.orEmpty()
                .any { it is MessagePart.Card && it.id == event.id }
            if (hasMatch) {
                state.updateInProgressPart(
                    matches = { it is MessagePart.Card && it.id == event.id },
                    transform = { (it as MessagePart.Card).copy(body = event.body) },
                )
            } else {
                // Defensive: a card-update with no matching card-start is out-of-order/
                // malformed input, but synthesizing a part beats silently dropping data.
                state.appendPart(MessagePart.Card(event.id, version = 1, body = event.body, isComplete = false), nowMillis)
            }
        }

        is ChatEvent.CardEnd -> state.updateInProgressPart(
            matches = { it is MessagePart.Card && it.id == event.id },
            transform = { (it as MessagePart.Card).copy(isComplete = true) },
        )

        // Reconciled by (type, id) exactly like cards; a null id has nothing to
        // reconcile against, so it always appends a new part.
        is ChatEvent.Custom -> {
            val hasMatch = event.id != null && state.inProgressMessage()?.parts.orEmpty()
                .any { it is MessagePart.Custom && it.type == event.type && it.id == event.id }
            if (hasMatch) {
                state.updateInProgressPart(
                    matches = { it is MessagePart.Custom && it.type == event.type && it.id == event.id },
                    transform = { (it as MessagePart.Custom).copy(raw = event.raw) },
                )
            } else {
                state.appendPart(MessagePart.Custom(event.type, event.id, event.raw), nowMillis)
            }
        }

        // Forward-compat catch-all - always appended, never dropped, so a newer BFF
        // emitting an event type this client doesn't recognize can't crash it.
        is ChatEvent.Unknown -> state.appendPart(MessagePart.Unknown(event.type, event.raw), nowMillis)

        is ChatEvent.Finish -> state.finalizeInProgressMessage().copy(isAwaitingResponse = false)

        is ChatEvent.HandoffStart -> state.copy(phase = SessionPhase.HandoffPending(event.reason))

        is ChatEvent.HandoffAgentJoined -> {
            val reason = (state.phase as? SessionPhase.HandoffPending)?.reason.orEmpty()
            state.copy(phase = SessionPhase.HandoffActive(reason, event.agentName))
        }

        is ChatEvent.HandoffEnd -> state.copy(phase = SessionPhase.AiActive, lastHandoffEndReason = event.reason)

        is ChatEvent.Error -> state.finalizeInProgressMessage().markLastUserMessageFailed().copy(
            lastError = ChatErrorInfo(event.code, event.message, isTransportLevel = false),
            isAwaitingResponse = false,
        )
    }

    /**
     * Folds an uncaught transport-level failure (e.g. [com.fsk.threadwire.transport.ChatTransportException]
     * from retry exhaustion) the same way as an in-band [ChatEvent.Error] - design doc
     * §2.7's graceful-degradation posture applied consistently at the stream level.
     */
    fun reduceTransportError(state: ChatState, message: String?): ChatState =
        state.finalizeInProgressMessage().markLastUserMessageFailed().copy(
            lastError = ChatErrorInfo(code = null, message = message, isTransportLevel = true),
            isAwaitingResponse = false,
        )

    /**
     * Finalizes state after [ChatSession.cancelCurrentTurn] - an intentional host
     * action, not a failure, so unlike [reduceTransportError] this never touches
     * [ChatState.lastError] and never flags the user message as failed.
     */
    fun reduceCancelled(state: ChatState): ChatState =
        state.finalizeInProgressMessage().copy(isAwaitingResponse = false)

    /**
     * Clears the failed flag on the last failed USER message and re-arms
     * [ChatState.isAwaitingResponse] - the reducer half of [ChatSession.retryLastFailedTurn].
     * No-op if nothing is currently flagged failed.
     */
    fun reduceRetryStarted(state: ChatState): ChatState {
        val idx = state.messages.indexOfLast { it.author == MessageAuthor.USER && it.deliveryFailed }
        if (idx < 0) return state
        return state.copy(
            messages = state.messages.mapIndexed { i, m -> if (i == idx) m.copy(deliveryFailed = false) else m },
            lastError = null,
            isAwaitingResponse = true,
        )
    }

    /**
     * Prepends a fetched history page (design doc M2.5 - server-fetched history, no
     * local persistence). De-dupes by [HistoryMessage.remoteId] against messages
     * already loaded, since a page boundary can overlap with what's already present.
     * Prepending is safe even if a live [ChatSession.sendMessage] races ahead of the
     * async history fetch - history is definitionally prior to any live turn
     * regardless of arrival order.
     */
    fun reduceHistoryPageLoaded(state: ChatState, page: ChatHistoryPage): ChatState {
        val existingIds = state.messages.mapTo(mutableSetOf()) { it.localId }
        val newMessages = page.messages
            .filter { it.remoteId !in existingIds }
            .map { hm -> ChatMessage(localId = hm.remoteId, author = hm.author, parts = hm.parts, isComplete = true) }
        return state.copy(
            messages = newMessages + state.messages,
            historyCursor = page.nextCursor,
            hasMoreHistory = page.nextCursor != null,
            isLoadingHistory = false,
            historyError = null,
        )
    }

    private fun ChatState.inProgressMessage(): ChatMessage? = messages.lastOrNull()?.takeIf { !it.isComplete }

    /** Flags the last USER message as failed - the triggering message of whichever turn just ended in error. */
    private fun ChatState.markLastUserMessageFailed(): ChatState {
        val idx = messages.indexOfLast { it.author == MessageAuthor.USER }
        if (idx < 0) return this
        return copy(messages = messages.mapIndexed { i, m -> if (i == idx) m.copy(deliveryFailed = true) else m })
    }

    /** Starts a new in-progress message if none exists, then appends [part] to it.
     *  [nowMillis] stamps the freshly-created message (the assistant/agent turn's first
     *  chunk); appends to an existing in-progress message leave its original stamp. */
    private fun ChatState.appendPart(part: MessagePart, nowMillis: Long): ChatState {
        val last = inProgressMessage()
        return if (last != null) {
            copy(messages = messages.dropLast(1) + last.copy(parts = last.parts + part))
        } else {
            val fresh = ChatMessage(
                localId = "live_$nextLocalMessageSeq",
                author = phase.authorForNextMessage(),
                parts = listOf(part),
                isComplete = false,
                timestampMillis = nowMillis,
            )
            copy(messages = messages + fresh, nextLocalMessageSeq = nextLocalMessageSeq + 1)
        }
    }

    /** No-ops if there's no matching part in the in-progress message (defensive default - no doc precedent). */
    private fun ChatState.updateInProgressPart(
        matches: (MessagePart) -> Boolean,
        transform: (MessagePart) -> MessagePart,
    ): ChatState {
        val last = inProgressMessage() ?: return this
        if (last.parts.none(matches)) return this
        val newParts = last.parts.map { if (matches(it)) transform(it) else it }
        return copy(messages = messages.dropLast(1) + last.copy(parts = newParts))
    }

    private fun ChatState.finalizeInProgressMessage(): ChatState {
        val last = inProgressMessage() ?: return this
        val finishedParts = last.parts.map { it.markComplete() }
        return copy(messages = messages.dropLast(1) + last.copy(parts = finishedParts, isComplete = true))
    }

    private fun MessagePart.markComplete(): MessagePart = when (this) {
        is MessagePart.Text -> copy(isComplete = true)
        is MessagePart.ToolCall -> copy(isComplete = true)
        is MessagePart.Card -> copy(isComplete = true)
        is MessagePart.Custom -> this
        is MessagePart.Unknown -> this
    }
}
