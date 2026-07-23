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
 *
 * Secondary constructors (not just the primary's default values) cover Swift/Obj-C
 * callers: Kotlin default parameter values don't bridge there, so without these,
 * `ChatConfig(baseUrl:contextProvider:)` would fail to compile from Swift with
 * "missing argument for parameter 'httpClientCustomizer'" (same class of issue as
 * `ChatSession.companion.create`). Swift disambiguates the two 3-arg overloads below by
 * argument label, not arity, so there's no ambiguity. A caller needing both
 * `httpClientCustomizer` and `historyProvider` calls the primary 4-arg constructor and
 * supplies every argument explicitly, same as any Swift call site must.
 *
 * Flagged, not solved here: this constructor count grows with each future optional
 * field (M3+ will likely want `actionHandler`/`telemetrySink`, per the class doc above)
 * - if a third or fourth optional dependency shows up, consider collapsing them into a
 * single "extras" value object instead, so only one more overload is ever needed
 * regardless of how many optional fields it holds.
 */
data class ChatConfig(
    val baseUrl: String,
    val contextProvider: ChatContextProvider,
    val httpClientCustomizer: (HttpClientConfig<*>) -> Unit = {},
    /** M2.5 - server-fetched session history/pagination; null means the host has no history endpoint. */
    val historyProvider: ChatHistoryProvider? = null,
) {
    constructor(baseUrl: String, contextProvider: ChatContextProvider) :
        this(baseUrl, contextProvider, {}, null)

    constructor(baseUrl: String, contextProvider: ChatContextProvider, httpClientCustomizer: (HttpClientConfig<*>) -> Unit) :
        this(baseUrl, contextProvider, httpClientCustomizer, null)

    constructor(baseUrl: String, contextProvider: ChatContextProvider, historyProvider: ChatHistoryProvider?) :
        this(baseUrl, contextProvider, {}, historyProvider)
}
