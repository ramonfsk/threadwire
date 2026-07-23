package com.fsk.threadwire.session

import com.fsk.threadwire.transport.SseChatTransport
import com.fsk.threadwire.transport.ReconnectPolicy
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * M2.5 - "reconnection validation": [SseChatTransport]'s `Last-Event-ID` reconnection
 * (M0) is already unit-tested at the transport level
 * ([com.fsk.threadwire.transport.SseChatTransportTest]'s `midStreamDropReconnectsWithLastEventId`),
 * which only asserts the flat [com.fsk.threadwire.protocol.ChatEvent] list. This drives
 * a full [ChatSession] through the same drop-and-resume, over a REAL embedded server
 * (same reasoning as `SseChatTransportTest`'s doc comment - `MockEngine` can't produce a
 * real SSE session), asserting the *folded* [ChatState] has no duplicated or missing
 * text across the reconnect boundary.
 */
class ChatSessionReconnectionTest {

    private lateinit var server: EmbeddedServer<*, *>

    @AfterTest
    fun tearDown() {
        if (::server.isInitialized) server.stop(gracePeriodMillis = 0, timeoutMillis = 200)
    }

    private fun startServer(handler: suspend RoutingContext.() -> Unit): String {
        server = embeddedServer(CIO, port = 0) {
            routing { post("/chat") { handler() } }
        }.start(wait = false)
        val port = runBlocking { server.engine.resolvedConnectors().first().port }
        return "http://127.0.0.1:$port/chat"
    }

    private fun sseFrame(id: Int, data: String): String = "id: $id\ndata: $data\n\n"

    private object NoopContextProvider : ChatContextProvider {
        override suspend fun headers(request: ChatRequest): Map<String, String> = emptyMap()
        override suspend fun contextPayload(request: ChatRequest): Map<String, Any?> = emptyMap()
    }

    private suspend fun awaitIdle(session: ChatSession) = withTimeout(5_000) {
        while (session.state.value.isAwaitingResponse) delay(5)
    }

    @Test
    fun sessionSurvivesMidStreamDropWithNoDuplicatedOrMissingText() = runBlocking {
        val url = startServer {
            val lastEventId = call.request.header("Last-Event-ID")
            if (lastEventId == null) {
                // Cuts off mid-message, no finish event - simulates a dropped connection.
                call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                    writeStringUtf8(sseFrame(1, """{"type":"text-start","id":"m1"}"""))
                    writeStringUtf8(sseFrame(2, """{"type":"text-delta","id":"m1","delta":"Hello, "}"""))
                }
            } else {
                call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                    writeStringUtf8(sseFrame(3, """{"type":"text-delta","id":"m1","delta":"world!"}"""))
                    writeStringUtf8(sseFrame(4, """{"type":"text-end","id":"m1"}"""))
                    writeStringUtf8(sseFrame(5, """{"type":"finish"}"""))
                }
            }
        }

        val httpClient = HttpClient(OkHttp) { install(SSE) }
        val transport = SseChatTransport(httpClient, reconnectPolicy = ReconnectPolicy(initialDelayMs = 1, maxDelayMs = 1))
        val config = ChatConfig(baseUrl = url, contextProvider = NoopContextProvider)
        val session = ChatSession(transport, config, sessionId = "s1", scope = this)

        session.sendMessage("hi")
        awaitIdle(session)

        val state = session.state.value
        assertEquals(2, state.messages.size)
        val reply = state.messages[1]
        assertEquals(MessageAuthor.AI, reply.author)
        assertEquals(true, reply.isComplete)
        val text = assertIs<MessagePart.Text>(reply.parts.single())
        assertEquals("Hello, world!", text.text)
        assertEquals(false, state.isAwaitingResponse)
    }
}
