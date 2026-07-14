package com.fsk.threadwire.transport

import kotlin.math.min
import kotlin.math.pow

/**
 * Governs [SseChatTransport]'s reconnection behavior after a dropped connection.
 * Defaults are a sensible starting point, not a hard requirement from the design doc -
 * bounded retries with capped exponential backoff, all overridable per instance.
 */
class ReconnectPolicy(
    private val initialDelayMs: Long = 500,
    private val maxDelayMs: Long = 30_000,
    private val factor: Double = 2.0,
    val maxAttempts: Int = 5,
) {

    /** Delay before the [attempt]-th retry (1-indexed). */
    fun delayFor(attempt: Int): Long {
        val raw = initialDelayMs * factor.pow(attempt - 1)
        return min(raw, maxDelayMs.toDouble()).toLong()
    }

    /**
     * Whether [error] is worth retrying at all. A [ChatTransportException] carries its
     * own verdict (set by the transport based on what actually failed, e.g. a 4xx vs a
     * network I/O error); anything else defaults to retryable.
     */
    fun isRetryable(error: Throwable): Boolean = when (error) {
        is ChatTransportException -> error.isRetryable
        else -> true
    }
}

/**
 * Wraps a transport-level failure with an explicit retryability verdict, so
 * [ReconnectPolicy] doesn't need to know about Ktor's exception hierarchy directly.
 */
class ChatTransportException(
    message: String,
    val isRetryable: Boolean,
    cause: Throwable? = null,
) : Exception(message, cause)
