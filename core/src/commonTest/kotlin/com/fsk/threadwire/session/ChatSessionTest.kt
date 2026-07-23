package com.fsk.threadwire.session

import com.fsk.threadwire.protocol.ChatEvent
import com.fsk.threadwire.transport.ChatTransport
import com.fsk.threadwire.transport.ChatTransportException
import com.fsk.threadwire.transport.TransportRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class ScriptedTransport(private val scriptFor: (TransportRequest) -> Flow<ChatEvent>) : ChatTransport {
    val requests = mutableListOf<TransportRequest>()
    override fun streamEvents(request: TransportRequest): Flow<ChatEvent> {
        requests.add(request)
        return scriptFor(request)
    }
}

/** Returns a different header value on each call - proves headers aren't cached across turns. */
private class RecordingContextProvider : ChatContextProvider {
    var callCount = 0
    override suspend fun headers(request: ChatRequest): Map<String, String> {
        callCount++
        return mapOf("call" to callCount.toString())
    }
    override suspend fun contextPayload(request: ChatRequest): Map<String, Any?> = emptyMap()
}

private fun testConfig(provider: ChatContextProvider, historyProvider: ChatHistoryProvider? = null) =
    ChatConfig(baseUrl = "https://example.test/chat", contextProvider = provider, historyProvider = historyProvider)

private class ScriptedHistoryProvider(private val pageFor: (String?) -> ChatHistoryPage) : ChatHistoryProvider {
    val requestedCursors = mutableListOf<String?>()
    override suspend fun fetchHistory(request: ChatHistoryRequest): ChatHistoryPage {
        requestedCursors.add(request.cursor)
        return pageFor(request.cursor)
    }
}

private class FailingHistoryProvider(private val message: String) : ChatHistoryProvider {
    override suspend fun fetchHistory(request: ChatHistoryRequest): ChatHistoryPage = throw RuntimeException(message)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionTest {

    @Test
    fun contextProviderIsCalledFreshOnEveryRequest() = runTest {
        val provider = RecordingContextProvider()
        val transport = ScriptedTransport { flow { emit(ChatEvent.Finish) } }
        val session = ChatSession(transport, testConfig(provider), sessionId = "s1", scope = this)

        session.sendMessage("first")
        advanceUntilIdle()
        session.sendMessage("second")
        advanceUntilIdle()

        assertEquals(2, transport.requests.size)
        assertEquals("1", transport.requests[0].headers["call"])
        assertEquals("2", transport.requests[1].headers["call"])
    }

    @Test
    fun sendMessageWhileAwaitingResponseIsANoOp() = runTest {
        val transport = ScriptedTransport { flow { awaitCancellation() } }
        val session = ChatSession(transport, testConfig(RecordingContextProvider()), sessionId = "s1", scope = this)

        session.sendMessage("first")
        advanceUntilIdle()
        session.sendMessage("second")
        advanceUntilIdle()

        assertEquals(1, transport.requests.size)

        session.cancelCurrentTurn()
        advanceUntilIdle()
    }

    @Test
    fun fullSendMessageUpdatesStateEndToEnd() = runTest {
        val transport = ScriptedTransport {
            flow {
                emit(ChatEvent.TextStart(id = "m1"))
                emit(ChatEvent.TextDelta(id = "m1", delta = "Hi!"))
                emit(ChatEvent.TextEnd(id = "m1"))
                emit(ChatEvent.Finish)
            }
        }
        val session = ChatSession(transport, testConfig(RecordingContextProvider()), sessionId = "s1", scope = this)

        session.sendMessage("hello")
        advanceUntilIdle()

        val state = session.state.value
        assertEquals(2, state.messages.size)
        assertEquals(MessageAuthor.USER, state.messages[0].author)
        assertEquals(MessageAuthor.AI, state.messages[1].author)
        assertTrue(state.messages[1].isComplete)
        assertEquals(false, state.isAwaitingResponse)
    }

    @Test
    fun transportExceptionMidStreamIsCaughtAndFoldedIntoState() = runTest {
        val transport = ScriptedTransport {
            flow {
                emit(ChatEvent.TextStart(id = "m1"))
                throw ChatTransportException("boom", isRetryable = false)
            }
        }
        val session = ChatSession(transport, testConfig(RecordingContextProvider()), sessionId = "s1", scope = this)

        session.sendMessage("hello")
        advanceUntilIdle()

        val state = session.state.value
        assertEquals(true, state.lastError?.isTransportLevel)
        assertEquals(false, state.isAwaitingResponse)
    }

    @Test
    fun cancelCurrentTurnFinalizesStateForNeverCompletingTransport() = runTest {
        val transport = ScriptedTransport {
            flow {
                emit(ChatEvent.TextStart(id = "m1"))
                awaitCancellation()
            }
        }
        val session = ChatSession(transport, testConfig(RecordingContextProvider()), sessionId = "s1", scope = this)

        session.sendMessage("hello")
        advanceUntilIdle()
        assertEquals(true, session.state.value.isAwaitingResponse)

        session.cancelCurrentTurn()
        advanceUntilIdle()

        val state = session.state.value
        assertEquals(false, state.isAwaitingResponse)
        assertNull(state.lastError)
        assertTrue(state.messages.last().isComplete)
    }

    @Test
    fun observeStateEmitsCurrentValueImmediatelyThenFollowsUpdates() = runTest {
        val transport = ScriptedTransport {
            flow {
                emit(ChatEvent.TextStart(id = "m1"))
                emit(ChatEvent.TextEnd(id = "m1"))
                emit(ChatEvent.Finish)
            }
        }
        val session = ChatSession(transport, testConfig(RecordingContextProvider()), sessionId = "s1", scope = this)
        val received = mutableListOf<ChatState>()
        val subscription = session.observeState { received.add(it) }
        advanceUntilIdle()

        // StateFlow.collect semantics: subscribing emits the current value immediately,
        // before any new state change - the whole reason observeState is usable from
        // Swift without a separate "get initial value" call.
        assertEquals(1, received.size)
        assertTrue(received.first().messages.isEmpty())

        session.sendMessage("hello")
        advanceUntilIdle()

        assertTrue(received.size > 1)
        assertEquals(2, received.last().messages.size)
        assertEquals(false, received.last().isAwaitingResponse)

        subscription.close()
    }

    @Test
    fun retryLastFailedTurnResendsWithoutAppendingASecondMessage() = runTest {
        var shouldFail = true
        val transport = ScriptedTransport {
            flow {
                if (shouldFail) {
                    shouldFail = false
                    throw ChatTransportException("boom", isRetryable = false)
                } else {
                    emit(ChatEvent.TextStart(id = "m1"))
                    emit(ChatEvent.TextEnd(id = "m1"))
                    emit(ChatEvent.Finish)
                }
            }
        }
        val session = ChatSession(transport, testConfig(RecordingContextProvider()), sessionId = "s1", scope = this)

        session.sendMessage("hello")
        advanceUntilIdle()
        assertEquals(true, session.state.value.messages.first().deliveryFailed)
        assertEquals(1, transport.requests.size)

        session.retryLastFailedTurn()
        advanceUntilIdle()

        val state = session.state.value
        // The point of retryLastFailedTurn: still exactly one USER message (no
        // duplicate bubble) - the AI's successful reply legitimately adds a second
        // message, which is expected, not a regression.
        assertEquals(1, state.messages.count { it.author == MessageAuthor.USER })
        assertEquals(2, state.messages.size)
        assertEquals(false, state.messages.first().deliveryFailed)
        assertEquals(false, state.isAwaitingResponse)
        assertEquals(2, transport.requests.size)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun retryLastFailedTurnIsNoOpWhenNothingFailed() = runTest {
        val transport = ScriptedTransport { flow { emit(ChatEvent.Finish) } }
        val session = ChatSession(transport, testConfig(RecordingContextProvider()), sessionId = "s1", scope = this)

        session.sendMessage("hello")
        advanceUntilIdle()

        session.retryLastFailedTurn()
        advanceUntilIdle()

        assertEquals(1, transport.requests.size)
    }

    @Test
    fun historyProviderAutoLoadsFirstPageOnConstruction() = runTest {
        val historyProvider = ScriptedHistoryProvider {
            ChatHistoryPage(
                messages = listOf(
                    HistoryMessage(
                        remoteId = "remote_1",
                        author = MessageAuthor.AI,
                        parts = listOf(MessagePart.Text(id = "t1", text = "older reply", isComplete = true)),
                    ),
                ),
                nextCursor = "cursor_2",
            )
        }
        val transport = ScriptedTransport { flow { emit(ChatEvent.Finish) } }
        val session = ChatSession(
            transport,
            testConfig(RecordingContextProvider(), historyProvider),
            sessionId = "s1",
            scope = this,
        )
        advanceUntilIdle()

        val state = session.state.value
        assertEquals(1, state.messages.size)
        assertEquals("remote_1", state.messages.single().localId)
        assertEquals("cursor_2", state.historyCursor)
        assertEquals(false, state.isLoadingHistory)
        assertEquals(listOf<String?>(null), historyProvider.requestedCursors)
    }

    @Test
    fun loadOlderHistoryFetchesNextPageAndNoOpsWhenExhausted() = runTest {
        val historyProvider = ScriptedHistoryProvider { cursor ->
            if (cursor == null) {
                ChatHistoryPage(messages = emptyList(), nextCursor = "cursor_2")
            } else {
                ChatHistoryPage(messages = emptyList(), nextCursor = null)
            }
        }
        val transport = ScriptedTransport { flow { emit(ChatEvent.Finish) } }
        val session = ChatSession(
            transport,
            testConfig(RecordingContextProvider(), historyProvider),
            sessionId = "s1",
            scope = this,
        )
        advanceUntilIdle()

        session.loadOlderHistory()
        advanceUntilIdle()
        assertEquals(false, session.state.value.hasMoreHistory)

        session.loadOlderHistory()
        advanceUntilIdle()

        assertEquals(listOf(null, "cursor_2"), historyProvider.requestedCursors)
    }

    @Test
    fun historyFetchFailureSetsHistoryErrorWithoutTouchingLastError() = runTest {
        val transport = ScriptedTransport { flow { emit(ChatEvent.Finish) } }
        val session = ChatSession(
            transport,
            testConfig(RecordingContextProvider(), FailingHistoryProvider("history down")),
            sessionId = "s1",
            scope = this,
        )
        advanceUntilIdle()

        val state = session.state.value
        assertEquals("history down", state.historyError?.message)
        assertNull(state.lastError)
        assertEquals(false, state.isLoadingHistory)
    }

    @Test
    fun observeStateStopsDeliveringAfterSubscriptionClosed() = runTest {
        val transport = ScriptedTransport { flow { emit(ChatEvent.Finish) } }
        val session = ChatSession(transport, testConfig(RecordingContextProvider()), sessionId = "s1", scope = this)
        val received = mutableListOf<ChatState>()
        val subscription = session.observeState { received.add(it) }
        advanceUntilIdle()

        subscription.close()
        val countAtClose = received.size

        session.sendMessage("hello")
        advanceUntilIdle()

        assertEquals(countAtClose, received.size)
    }
}
