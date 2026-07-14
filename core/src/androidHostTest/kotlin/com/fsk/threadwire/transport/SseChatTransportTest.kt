package com.fsk.threadwire.transport

import com.fsk.threadwire.protocol.ChatEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Exercises [SseChatTransport] against a REAL embedded HTTP server on loopback, not
 * Ktor's `MockEngine`. `MockEngine` cannot produce a proper `SSESession`: the response
 * adaptation that turns a raw byte response into an `SSESession` is implemented by
 * each concrete client engine (CIO/OkHttp/Darwin), not by `ktor-client-core` itself -
 * confirmed by inspecting the Ktor 3.5.1 sources directly (there is no caller of
 * `ResponseAdapter.adapt(...)` anywhere in `ktor-client-core`; `ktor-client-mock` never
 * references it either). JVM-only (this is `androidHostTest`, not `commonTest`) -
 * mirrors `tools/fake-sse-server`'s own approach, which the maintainer already
 * validated manually via curl.
 */
class SseChatTransportTest {

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

    private fun httpClient() = HttpClient(OkHttp) { install(SSE) }

    @Test
    fun happyPathEmitsAllEventsUntilFinish() = runBlocking {
        val url = startServer {
            call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                writeStringUtf8(sseFrame(1, """{"type":"text-start","id":"m1"}"""))
                writeStringUtf8(sseFrame(2, """{"type":"text-delta","id":"m1","delta":"Hi"}"""))
                writeStringUtf8(sseFrame(3, """{"type":"text-end","id":"m1"}"""))
                writeStringUtf8(sseFrame(4, """{"type":"finish"}"""))
            }
        }

        val transport = SseChatTransport(httpClient())
        val events = transport.streamEvents(TransportRequest(url = url, body = "{}")).toList()

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
    fun midStreamDropReconnectsWithLastEventId() = runBlocking {
        var lastEventIdOnRetry: String? = null
        val url = startServer {
            val lastEventId = call.request.header("Last-Event-ID")
            if (lastEventId == null) {
                // Cuts off after id=2, no finish event - simulates a dropped connection.
                call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                    writeStringUtf8(sseFrame(1, """{"type":"text-start","id":"m1"}"""))
                    writeStringUtf8(sseFrame(2, """{"type":"text-delta","id":"m1","delta":"Hi"}"""))
                }
            } else {
                lastEventIdOnRetry = lastEventId
                call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                    writeStringUtf8(sseFrame(3, """{"type":"text-end","id":"m1"}"""))
                    writeStringUtf8(sseFrame(4, """{"type":"finish"}"""))
                }
            }
        }

        val transport = SseChatTransport(httpClient(), reconnectPolicy = ReconnectPolicy(initialDelayMs = 1, maxDelayMs = 1))
        val events = transport.streamEvents(TransportRequest(url = url, body = "{}")).toList()

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
    fun nonRecoverableHttpErrorThrowsImmediatelyWithoutRetry() = runBlocking {
        var requestCount = 0
        val url = startServer {
            requestCount++
            call.respond(HttpStatusCode.Unauthorized)
        }

        val transport = SseChatTransport(httpClient())
        assertFailsWith<ChatTransportException> {
            transport.streamEvents(TransportRequest(url = url, body = "{}")).toList()
        }
        assertEquals(1, requestCount)
    }

    @Test
    fun retryExhaustionThrowsAfterMaxAttempts() = runBlocking {
        var requestCount = 0
        val url = startServer {
            requestCount++
            call.respond(HttpStatusCode.ServiceUnavailable)
        }

        val policy = ReconnectPolicy(initialDelayMs = 1, maxDelayMs = 1, maxAttempts = 3)
        val transport = SseChatTransport(httpClient(), reconnectPolicy = policy)
        assertFailsWith<ChatTransportException> {
            transport.streamEvents(TransportRequest(url = url, body = "{}")).toList()
        }
        assertEquals(policy.maxAttempts + 1, requestCount)
    }
}
