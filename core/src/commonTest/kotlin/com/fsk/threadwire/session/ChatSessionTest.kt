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

private fun testConfig(provider: ChatContextProvider) =
    ChatConfig(baseUrl = "https://example.test/chat", contextProvider = provider)

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
