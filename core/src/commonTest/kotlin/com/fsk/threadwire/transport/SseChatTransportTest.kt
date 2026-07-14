package com.fsk.threadwire.transport

import com.fsk.threadwire.protocol.ChatEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exercises [SseChatTransport] against Ktor's [MockEngine] - no real network involved.
 * Complements the maintainer's own manual verification against the fake SSE server
 * (`tools/fake-sse-server`), which is the hands-on check for the real HTTP stack.
 */
class SseChatTransportTest {

    private fun sseFrame(id: Int, data: String): String = "id: $id\ndata: $data\n\n"

    private fun sseResponse(body: String) = { _: io.ktor.client.request.HttpRequestData ->
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
        )
    }

    @Test
    fun happyPathEmitsAllEventsUntilFinish() = runTest {
        val body = buildString {
            append(sseFrame(1, """{"type":"text-start","id":"m1"}"""))
            append(sseFrame(2, """{"type":"text-delta","id":"m1","delta":"Hi"}"""))
            append(sseFrame(3, """{"type":"text-end","id":"m1"}"""))
            append(sseFrame(4, """{"type":"finish"}"""))
        }
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount++
            sseResponse(body)(request)
        }
        val client = HttpClient(engine) { install(SSE) }
        val transport = SseChatTransport(client)

        val events = transport.streamEvents(TransportRequest(url = "https://example.test/chat", body = "{}")).toList()

        assertEquals(1, requestCount)
        assertEquals(
            listOf(
                ChatEvent.TextStart("m1"),
                ChatEvent.TextDelta("m1", "Hi"),
                ChatEvent.TextEnd("m1"),
                ChatEvent.Finish,
            ),
            events,
        )
    }

    @Test
    fun midStreamDropReconnectsWithLastEventId() = runTest {
        var requestCount = 0
        var lastEventIdOnRetry: String? = null

        val engine = MockEngine { request ->
            requestCount++
            if (requestCount == 1) {
                // Cuts off after id=2, no finish event - simulates a dropped connection.
                val body = buildString {
                    append(sseFrame(1, """{"type":"text-start","id":"m1"}"""))
                    append(sseFrame(2, """{"type":"text-delta","id":"m1","delta":"Hi"}"""))
                }
                sseResponse(body)(request)
            } else {
                lastEventIdOnRetry = request.headers["Last-Event-ID"]
                val body = buildString {
                    append(sseFrame(3, """{"type":"text-end","id":"m1"}"""))
                    append(sseFrame(4, """{"type":"finish"}"""))
                }
                sseResponse(body)(request)
            }
        }
        val client = HttpClient(engine) { install(SSE) }
        val transport = SseChatTransport(client, reconnectPolicy = ReconnectPolicy(initialDelayMs = 1, maxDelayMs = 1))

        val events = transport.streamEvents(TransportRequest(url = "https://example.test/chat", body = "{}")).toList()

        assertEquals(2, requestCount)
        assertEquals("2", lastEventIdOnRetry)
        assertEquals(
            listOf(
                ChatEvent.TextStart("m1"),
                ChatEvent.TextDelta("m1", "Hi"),
                ChatEvent.TextEnd("m1"),
                ChatEvent.Finish,
            ),
            events,
        )
    }

    @Test
    fun nonRecoverableHttpErrorThrowsImmediatelyWithoutRetry() = runTest {
        var requestCount = 0
        val engine = MockEngine { _ ->
            requestCount++
            respondError(HttpStatusCode.Unauthorized)
        }
        val client = HttpClient(engine) { install(SSE) }
        val transport = SseChatTransport(client)

        assertFailsWith<ChatTransportException> {
            transport.streamEvents(TransportRequest(url = "https://example.test/chat", body = "{}")).toList()
        }
        assertEquals(1, requestCount)
    }

    @Test
    fun retryExhaustionThrowsAfterMaxAttempts() = runTest {
        var requestCount = 0
        val engine = MockEngine { _ ->
            requestCount++
            respondError(HttpStatusCode.ServiceUnavailable)
        }
        val client = HttpClient(engine) { install(SSE) }
        val policy = ReconnectPolicy(initialDelayMs = 1, maxDelayMs = 1, maxAttempts = 3)
        val transport = SseChatTransport(client, reconnectPolicy = policy)

        assertFailsWith<ChatTransportException> {
            transport.streamEvents(TransportRequest(url = "https://example.test/chat", body = "{}")).toList()
        }
        assertTrue(requestCount > 1)
        assertEquals(policy.maxAttempts + 1, requestCount)
    }
}
