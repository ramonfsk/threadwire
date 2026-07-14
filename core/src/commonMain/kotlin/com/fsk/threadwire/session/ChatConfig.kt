package com.fsk.threadwire.session

import io.ktor.client.HttpClientConfig

/**
 * Configuration for a [ChatSession] (design doc §7). Deliberately omits
 * `actionHandler`/`telemetrySink` from the doc's full snippet - `ChatActionHandler`
 * (M4) and `ChatTelemetrySink` (M5) don't exist yet; this is a known, intentional
 * future (additive) change, not an oversight.
 *
 * [httpClientCustomizer] isn't consumed by anything yet in M1 - no code builds an
 * `HttpClient` from a `ChatConfig` yet (see M1 plan's "flagged, provisional decisions" -
 * a default `ChatSession` factory is deferred to when M2's UI needs one). Kept now for
 * shape-fidelity with §7, to avoid a second breaking change later.
 */
data class ChatConfig(
    val baseUrl: String,
    val contextProvider: ChatContextProvider,
    val httpClientCustomizer: (HttpClientConfig<*>) -> Unit = {},
)
