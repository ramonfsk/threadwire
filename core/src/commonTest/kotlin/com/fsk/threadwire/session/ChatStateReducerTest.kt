package com.fsk.threadwire.session

import com.fsk.threadwire.protocol.ChatEvent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatStateReducerTest {

    private fun reduceAll(events: List<ChatEvent>, initial: ChatState = ChatState()): ChatState =
        events.fold(initial) { state, event -> ChatStateReducer.reduce(state, event) }

    @Test
    fun accumulatesTextIntoOneCompleteAiMessage() {
        val state = reduceAll(
            listOf(
                ChatEvent.TextStart(id = "msg_1"),
                ChatEvent.TextDelta(id = "msg_1", delta = "Hello, "),
                ChatEvent.TextDelta(id = "msg_1", delta = "world!"),
                ChatEvent.TextEnd(id = "msg_1"),
                ChatEvent.Finish,
            ),
        )

        assertEquals(1, state.messages.size)
        val message = state.messages.single()
        assertEquals(MessageAuthor.AI, message.author)
        assertTrue(message.isComplete)
        val text = assertIs<MessagePart.Text>(message.parts.single())
        assertEquals("Hello, world!", text.text)
        assertTrue(text.isComplete)
        assertEquals(false, state.isAwaitingResponse)
    }

    @Test
    fun cardUpdateFullyReplacesBodyNotMerge() {
        val bodyA = buildJsonObject { put("status", JsonPrimitive("processing")) }
        val bodyB = buildJsonObject { put("status", JsonPrimitive("done")) }

        val state = reduceAll(
            listOf(
                ChatEvent.CardStart(id = "card_1", version = 1),
                ChatEvent.CardUpdate(id = "card_1", body = bodyA),
                ChatEvent.CardUpdate(id = "card_1", body = bodyB),
                ChatEvent.CardEnd(id = "card_1"),
            ),
        )

        val card = assertIs<MessagePart.Card>(state.messages.single().parts.single())
        assertEquals(bodyB, card.body)
        assertTrue(card.isComplete)
    }

    @Test
    fun cardUpdateWithNoMatchingStartSynthesizesCardDefensively() {
        val body = buildJsonObject { put("status", JsonPrimitive("processing")) }
        val state = reduceAll(listOf(ChatEvent.CardUpdate(id = "card_orphan", body = body)))

        val card = assertIs<MessagePart.Card>(state.messages.single().parts.single())
        assertEquals("card_orphan", card.id)
        assertEquals(1, card.version)
        assertEquals(body, card.body)
    }

    @Test
    fun customPartReconciledBySameTypeAndId() {
        val rawA = buildJsonObject { put("v", JsonPrimitive(1)) }
        val rawB = buildJsonObject { put("v", JsonPrimitive(2)) }

        val state = reduceAll(
            listOf(
                ChatEvent.Custom(type = "data-weather", id = "w1", raw = rawA),
                ChatEvent.Custom(type = "data-weather", id = "w1", raw = rawB),
            ),
        )

        val custom = assertIs<MessagePart.Custom>(state.messages.single().parts.single())
        assertEquals(rawB, custom.raw)
    }

    @Test
    fun customPartWithNullIdAlwaysAppends() {
        val raw = buildJsonObject { put("v", JsonPrimitive(1)) }

        val state = reduceAll(
            listOf(
                ChatEvent.Custom(type = "data-log", id = null, raw = raw),
                ChatEvent.Custom(type = "data-log", id = null, raw = raw),
            ),
        )

        assertEquals(2, state.messages.single().parts.size)
    }

    @Test
    fun unknownEventAppearsAsUnknownPartWithoutThrowing() {
        val raw = buildJsonObject { put("type", JsonPrimitive("future-event")) }
        val state = reduceAll(listOf(ChatEvent.Unknown(type = "future-event", raw = raw)))

        val unknown = assertIs<MessagePart.Unknown>(state.messages.single().parts.single())
        assertEquals("future-event", unknown.type)
    }

    @Test
    fun errorMidStreamFinalizesMessageWithoutChangingPhase() {
        val state = reduceAll(
            listOf(
                ChatEvent.TextStart(id = "msg_1"),
                ChatEvent.TextDelta(id = "msg_1", delta = "partial"),
                ChatEvent.Error(code = "boom", message = "something broke"),
            ),
        )

        assertTrue(state.messages.single().isComplete)
        assertEquals(SessionPhase.AiActive, state.phase)
        assertEquals("boom", state.lastError?.code)
        assertEquals(false, state.lastError?.isTransportLevel)
        assertEquals(false, state.isAwaitingResponse)
    }

    @Test
    fun transportErrorFinalizesMessageAsTransportLevel() {
        val mid = reduceAll(listOf(ChatEvent.TextStart(id = "msg_1")))
        val state = ChatStateReducer.reduceTransportError(mid, "connection reset")

        assertTrue(state.messages.single().isComplete)
        assertEquals(true, state.lastError?.isTransportLevel)
        assertEquals("connection reset", state.lastError?.message)
    }

    @Test
    fun cancelledFinalizesMessageWithoutSettingError() {
        val mid = reduceAll(listOf(ChatEvent.TextStart(id = "msg_1")))
        val state = ChatStateReducer.reduceCancelled(mid)

        assertTrue(state.messages.single().isComplete)
        assertNull(state.lastError)
        assertEquals(false, state.isAwaitingResponse)
    }

    private fun userMessage(text: String, deliveryFailed: Boolean = false) = ChatMessage(
        localId = "live_0",
        author = MessageAuthor.USER,
        parts = listOf(MessagePart.Text(id = "user_0", text = text, isComplete = true)),
        isComplete = true,
        deliveryFailed = deliveryFailed,
    )

    @Test
    fun transportErrorFlagsTheTriggeringUserMessageAsFailed() {
        val withUserMessage = ChatState(messages = listOf(userMessage("hello")))
        val mid = ChatStateReducer.reduce(withUserMessage, ChatEvent.TextStart(id = "msg_1"))
        val state = ChatStateReducer.reduceTransportError(mid, "connection reset")

        assertEquals(true, state.messages.first().deliveryFailed)
    }

    @Test
    fun inBandErrorFlagsTheTriggeringUserMessageAsFailed() {
        val withUserMessage = ChatState(messages = listOf(userMessage("hello")))
        val mid = ChatStateReducer.reduce(withUserMessage, ChatEvent.TextStart(id = "msg_1"))
        val state = ChatStateReducer.reduce(mid, ChatEvent.Error(code = "boom", message = "oops"))

        assertEquals(true, state.messages.first().deliveryFailed)
    }

    @Test
    fun cancelledDoesNotFlagTheUserMessageAsFailed() {
        val withUserMessage = ChatState(messages = listOf(userMessage("hello")))
        val mid = ChatStateReducer.reduce(withUserMessage, ChatEvent.TextStart(id = "msg_1"))
        val state = ChatStateReducer.reduceCancelled(mid)

        assertEquals(false, state.messages.first().deliveryFailed)
    }

    @Test
    fun retryStartedClearsFailedFlagAndErrorAndReArmsAwaitingResponse() {
        val failedState = ChatState(
            messages = listOf(userMessage("hello", deliveryFailed = true)),
            lastError = ChatErrorInfo(code = null, message = "boom", isTransportLevel = true),
        )

        val state = ChatStateReducer.reduceRetryStarted(failedState)

        assertEquals(false, state.messages.single().deliveryFailed)
        assertNull(state.lastError)
        assertTrue(state.isAwaitingResponse)
    }

    @Test
    fun retryStartedIsNoOpWhenNothingIsFlaggedFailed() {
        val state = ChatState(messages = listOf(userMessage("hello")))

        val result = ChatStateReducer.reduceRetryStarted(state)

        assertEquals(state, result)
    }

    @Test
    fun liveMessageIdsAreCounterBasedNotPositionDerived() {
        val state = reduceAll(
            listOf(
                ChatEvent.TextStart(id = "m1"),
                ChatEvent.TextEnd(id = "m1"),
                ChatEvent.Finish,
            ),
        )

        assertEquals("live_0", state.messages.single().localId)
        assertEquals(1, state.nextLocalMessageSeq)
    }

    @Test
    fun historyPageLoadedPrependsDeDupesAndUpdatesCursor() {
        val existing = ChatState(messages = listOf(userMessage("newest")))
        val page = ChatHistoryPage(
            messages = listOf(
                HistoryMessage(
                    remoteId = "remote_1",
                    author = MessageAuthor.AI,
                    parts = listOf(MessagePart.Text(id = "t1", text = "older reply", isComplete = true)),
                ),
            ),
            nextCursor = "cursor_2",
        )

        val state = ChatStateReducer.reduceHistoryPageLoaded(existing, page)

        assertEquals(2, state.messages.size)
        assertEquals("remote_1", state.messages.first().localId)
        assertEquals("live_0", state.messages.last().localId)
        assertEquals("cursor_2", state.historyCursor)
        assertTrue(state.hasMoreHistory)
        assertEquals(false, state.isLoadingHistory)
    }

    @Test
    fun historyPageLoadedDeDupesAgainstAlreadyLoadedRemoteIds() {
        val existing = ChatState(
            messages = listOf(
                ChatMessage(localId = "remote_1", author = MessageAuthor.AI, parts = emptyList(), isComplete = true),
            ),
        )
        val page = ChatHistoryPage(
            messages = listOf(
                HistoryMessage(remoteId = "remote_1", author = MessageAuthor.AI, parts = emptyList()),
                HistoryMessage(remoteId = "remote_0", author = MessageAuthor.AI, parts = emptyList()),
            ),
            nextCursor = null,
        )

        val state = ChatStateReducer.reduceHistoryPageLoaded(existing, page)

        assertEquals(2, state.messages.size)
        assertEquals("remote_0", state.messages.first().localId)
        assertEquals(false, state.hasMoreHistory)
    }

    @Test
    fun fullHandoffCycleFlipsAuthorshipAndReturnsToAiActive() {
        var state = ChatState()

        // AI turn before handoff.
        state = reduceAll(
            listOf(
                ChatEvent.TextStart(id = "m1"),
                ChatEvent.TextDelta(id = "m1", delta = "How can I help?"),
                ChatEvent.TextEnd(id = "m1"),
                ChatEvent.Finish,
            ),
            initial = state,
        )
        assertEquals(MessageAuthor.AI, state.messages.last().author)
        assertEquals(SessionPhase.AiActive, state.phase)

        // Handoff kicks off.
        state = ChatStateReducer.reduce(state, ChatEvent.HandoffStart(reason = "user_requested"))
        assertEquals(SessionPhase.HandoffPending("user_requested"), state.phase)

        state = ChatStateReducer.reduce(state, ChatEvent.HandoffAgentJoined(agentName = "Jane"))
        assertEquals(SessionPhase.HandoffActive("user_requested", "Jane"), state.phase)

        // Human agent's turn.
        state = reduceAll(
            listOf(
                ChatEvent.TextStart(id = "m2"),
                ChatEvent.TextDelta(id = "m2", delta = "I can help with that."),
                ChatEvent.TextEnd(id = "m2"),
                ChatEvent.Finish,
            ),
            initial = state,
        )
        assertEquals(MessageAuthor.HUMAN_AGENT, state.messages.last().author)

        // Handoff ends, session returns to AI.
        state = ChatStateReducer.reduce(state, ChatEvent.HandoffEnd(reason = "agent_resolved"))
        assertEquals(SessionPhase.AiActive, state.phase)
        assertEquals("agent_resolved", state.lastHandoffEndReason)

        // Back to an AI turn.
        state = reduceAll(
            listOf(
                ChatEvent.TextStart(id = "m3"),
                ChatEvent.TextDelta(id = "m3", delta = "Glad that's sorted!"),
                ChatEvent.TextEnd(id = "m3"),
                ChatEvent.Finish,
            ),
            initial = state,
        )
        assertEquals(MessageAuthor.AI, state.messages.last().author)
    }
}
