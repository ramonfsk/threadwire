package com.fsk.threadwire.transport

import com.fsk.threadwire.protocol.ChatEvent
import com.fsk.threadwire.protocol.ChatEventParser
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * SSE implementation of [ChatTransport] - the AI-phase default transport (design doc
 * §6). Reconnects on a dropped connection using `Last-Event-ID`, tracked from each
 * frame's own `id` field; that is the entire reconnection mechanism (no custom
 * seq/resume handshake here - that's WebSocket-only, M6, per design doc §6.1).
 *
 * NOTE: the exact `HttpClient.sse(...)` call shape below (in particular, driving a POST
 * request through it and reading `HttpMethod`/body from the request builder) should be
 * double-checked against the actual Ktor version pinned in the version catalog once
 * dependencies resolve - written against the general SSE client plugin API, not
 * verified against a specific Ktor release.
 */
class SseChatTransport(
    private val httpClient: HttpClient,
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
) : ChatTransport {

    override fun streamEvents(request: ChatRequest): Flow<ChatEvent> = flow {
        var lastEventId: String? = null
        var attempt = 0
        var finished = false

        while (!finished) {
            try {
                httpClient.sse({
                    url(request.url)
                    method = HttpMethod.Post
                    request.headers.forEach { (key, value) -> header(key, value) }
                    lastEventId?.let { header("Last-Event-ID", it) }
                    setBody(request.body)
                }) {
                    incoming.collect { serverSentEvent ->
                        serverSentEvent.id?.let { lastEventId = it }
                        val event = ChatEventParser.parse(serverSentEvent.data.orEmpty())
                        emit(event)
                        if (event is ChatEvent.Finish) {
                            finished = true
                        }
                    }
                }

                if (finished) {
                    attempt = 0
                } else {
                    // Connection closed cleanly but without a Finish event - treat like a drop.
                    attempt++
                    reconnectOrThrow(attempt, cause = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                reconnectOrThrow(attempt, cause = e)
            }
        }
    }

    private suspend fun reconnectOrThrow(attempt: Int, cause: Exception?) {
        val isClientError = (cause as? ResponseException)?.response?.status?.value?.let { it in 400..499 } ?: false
        val transportError = ChatTransportException(
            message = cause?.message ?: "SSE stream ended before a finish event",
            isRetryable = !isClientError,
            cause = cause,
        )

        if (attempt > reconnectPolicy.maxAttempts || !reconnectPolicy.isRetryable(transportError)) {
            throw transportError
        }
        delay(reconnectPolicy.delayFor(attempt))
    }
}
