# Design Doc — Native Chat SDK (iOS/Android) for LLM Integration

**Status:** Draft v0.1 — open for discussion
**Project name:** Threadwire
**Author:** Ramon (with market and architecture analysis support via Claude)

## 1. Executive summary

`Threadwire` is an open source, native (Swift/Kotlin) library for iOS and Android that provides a complete chat experience — streaming text with markdown, file upload, audio record/playback, dynamic cards, and human handoff — meant to be embedded into existing apps that need to talk to an LLM.

The library does not provide the LLM, does not talk directly to providers (OpenAI, Anthropic, Gemini, etc.), and does not maintain its own messaging infrastructure. It only talks to an HTTP backend controlled by the integrator (typically a BFF), and delegates every business decision — model orchestration, authorization of sensitive actions, human-handoff routing — to that backend.

Why this exists: most AI chat embedded in mobile apps today is built with React Native to speed up screen delivery, and runs into the bridge — which makes debugging and performance harder to reason about — exactly in the kind of app (banking, legacy, high-criticality) where that matters most. There isn't today a native library (not dependent on RN/Flutter), BYO-LLM and BYO-backend, with this feature scope, ready to be adopted.

## 2. Design principles (non-negotiable)

1. UI is always native. SwiftUI on iOS, Jetpack Compose on Android. Never rendered through a JS bridge or a cross-platform canvas.
2. The library never talks to LLM providers. It only talks to the HTTP endpoint the host configures (the integrator's BFF). It never stores an API key for any provider.
3. The library never executes sensitive actions. Taps on card actions (payment, authorization, etc.) only trigger a callback (`ChatActionHandler`) implemented by the host. The library makes no network call at that point — it treats the action as a black box.
4. Zero dependency on any telemetry/analytics vendor. The library exposes an event contract (`ChatTelemetrySink`); whoever sends events to Firebase, Datadog, or anything else, is the host.
5. KMP is scoped to the core only. Kotlin Multiplatform shares logic (transport, state, parsing) — never UI. This preserves the library's whole reason to exist: looking and performing like genuinely native.
6. Context is opaque. The library never interprets the business content the host injects into headers/body — it only transports it.
7. Graceful degradation. A network failure in any secondary configuration (UI config, for example) never prevents the chat from working — it falls back to cache or to a default.
8. Don't reinvent what's already a market standard. Where a widely adopted convention already exists (streaming protocol, card schema), the library aligns with it instead of building something from scratch — see section 4.

## 3. Market landscape (summary)

- The conversational AI market is growing at roughly 20–26% CAGR (estimates between US$14–19B in 2026, reaching the tens of billions by 2030–2035) — a strong demand driver for integration tooling.
- Chat platforms with an AI layer bolted on (Stream, Sendbird, CometChat) already address part of this, but as an extension of a full messaging infrastructure, priced per MAU/DAU — heavier than needed for "just one person talking to an LLM."
- AI chat component libraries (assistant-ui, Vercel AI SDK UI, llamaindex/chat-ui, Flutter AI Toolkit) solve this well, but are web-first (React) or Flutter — none is native Swift/Kotlin.
- Gap identified: there is no native equivalent today (without RN/Flutter) of what assistant-ui is for the web — UI-only, BYO-backend, with upload/audio/streaming ready to go.
- Most realistic monetization path: not the core itself (a commodity, as the market already treats it), but the human handoff layer (queueing, routing, SLA, agent dashboard) — a pattern already validated by Sendbird Desk and CometChat Agents.

## 4. Prior art — streaming protocols already in use (so we don't reinvent the wheel)

Before designing our own wire protocol, it's worth aligning with existing convention:

### 4.1 Anthropic Messages API (streaming)

SSE with named events and an indexed content block structure, allowing text and tool calls to be interleaved:

```
event: message_start      → opens the message (id, model, partial usage)
event: content_block_start → opens a block (index N, type text | tool_use)
event: content_block_delta → increments block N (text_delta | input_json_delta | signature_delta)
event: content_block_stop  → closes block N
event: message_delta       → message-level changes (stop_reason, final usage)
event: message_stop        → end
event: ping / event: error → keep-alive / error
```

Relevant lesson: block indexing allows multiple content types to coexist in the same response (text + tool call), and the recommended reconnection pattern is to resend the partial content already received as a prefix on the next attempt.

### 4.2 OpenAI Chat Completions (streaming)

Simpler and flatter: each event is a JSON chunk with `choices[].delta.content`, no indexed-block concept; the stream ends with a `data: [DONE]` sentinel.

### 4.3 Vercel AI SDK — UI Message Stream Protocol

The closest thing to a direct reference for what we need, and today a growing de facto standard in the JS/Next.js ecosystem (the `ai` library has large and growing adoption). Also SSE (migrated from a custom format to plain SSE specifically for robustness/debuggability), using typed parts with a start/delta/end lifecycle, identified by ID:

```
data: {"type":"text-start","id":"..."}
data: {"type":"text-delta","id":"...","delta":"Hello"}
data: {"type":"text-end","id":"..."}
data: {"type":"tool-input-start","toolCallId":"..."}
data: {"type":"tool-input-delta","toolCallId":"...","inputTextDelta":"..."}
data: {"type":"tool-input-available","toolCallId":"...","input":{...}}
data: {"type":"data-<custom>","id":"...", ...}   ← custom part, reconciled by ID
data: {"type":"finish"}
```

The most valuable detail here: custom parts (`data-*`) are reconciled by ID — resending the same ID replaces the previous part. This is exactly the mechanism we need for cards that update in place (e.g., "processing payment" → "payment confirmed"), without reinventing anything.

### 4.4 Design conclusion

`Threadwire` adopts an event protocol that is SSE, with typed start/delta/end parts and ID-based reconciliation, in the same spirit as the Vercel AI SDK (as the closest and most widely adopted pattern), adding Anthropic's indexed-block concept wherever interleaving types makes sense. This has a large practical advantage: any BFF that already emits the Vercel AI SDK protocol is only a few custom events away from being compatible with `Threadwire` — we're not asking the integrator to rewrite their backend from scratch.

Custom events we add (not present in the protocols above, specific to our scope):

```
data: {"type":"card-start","id":"card_123","version":1}
data: {"type":"card-update","id":"card_123","body":{...}}   ← reconciled by ID, covers "refresh"
data: {"type":"card-end","id":"card_123"}
data: {"type":"handoff-start","reason":"user_requested"}
data: {"type":"handoff-agent-joined","agentName":"..."}
data: {"type":"handoff-end"}
```

## 5. Layered architecture

```
┌─────────────────────────────────────────────────────────┐
│  Native UI (never shared)                                  │
│  SwiftUI (iOS)          │  Jetpack Compose (Android)      │
│  bubbles, markdown, audio, upload, card renderer            │
└───────────────────────────┬─────────────────────────────┘
                             │ observes StateFlow / calls methods
┌───────────────────────────▼─────────────────────────────┐
│  commonMain (Kotlin Multiplatform) — "Threadwire"           │
│                                                            │
│  ChatSession        state machine (StateFlow<ChatState>)  │
│  ChatTransport      SseChatTransport / WebSocketTransport │
│  ChatContextProvider  headers() / contextPayload()        │
│  ChatActionHandler    handle(actionId, payload) — host    │
│  ChatTelemetrySink    track(event) — host                 │
│  Card schema          parse/validate, never renders       │
│  ChatUIConfig         fetch + cache + default              │
└───────────────────────────┬─────────────────────────────┘
                             │ HTTPS (SSE / WebSocket)
┌───────────────────────────▼─────────────────────────────┐
│  Integrator's BFF (out of the library's scope)             │
│  decides: which LLM, which context, when to escalate       │
│  validates and executes sensitive actions                   │
└─────────────────────────────────────────────────────────┘
```

## 6. Transport: SSE + WebSocket across two phases

**AI phase (default, most of the session's lifetime): SSE.**

- Plain HTTP → passes through corporate proxies/WAFs with no special rule (relevant for banking/legacy apps).
- Native reconnection via `Last-Event-ID` — survives network switches/app suspension without any custom resume logic.
- No sticky state on the load balancer, scales like any long-lived HTTP response.
- Implementation: `URLSession` with byte streaming on iOS, `OkHttp` with an SSE parser on Android — neither platform has a native `EventSource`, so this is implemented once in `commonMain` via Ktor Client (which has multiplatform support for both SSE and WebSocket).

**Handoff phase (human takeover): WebSocket.**

- Genuinely bidirectional, needed for presence/typing indicators with multiple actors (user + agent + possibly a supervisor).
- Only activated once the `handoff-start` event arrives — no WS connection is left hanging on sessions that never escalate (the majority).

`ChatTransport` is a single interface; switching between the two implementations is internal and invisible to the UI, which only reacts to events.

### 6.1 Bidirectional handoff (AI ↔ human) and session continuity

Important correction to what was implicit so far: handoff is not a one-way path. Without the ability to return to the AI once a human agent resolves the issue, the feature loses its point — nobody wants to be stuck with a human for the rest of the conversation. `ChatSession` treats this as a cycle, not a fixed sequence:

```
ai_active ──handoff-start──▶ handoff_pending ──agent-joined──▶ handoff_active
     ▲                                                               │
     └──────────────────── handoff-end (reason) ────────────────────┘
```

The same session can go through this cycle more than once.

Points this forces us to design, which didn't exist before:

- **Symmetric transport switching.** Just as `ai_active → handoff` promotes SSE to WebSocket, `handoff → ai_active` does the reverse (closes the WS, reopens SSE). `ChatTransport` needs to support both directions — until now we had only designed the upgrade path.
- **Message authorship.** Every message needs an explicit `author: ai | human_agent | system` field. Without it, the UI doesn't know who's speaking after a switch, and a second handoff cycle becomes ambiguous.
- **Context continuity on the way back to AI** — a business decision (lives in the BFF, not the library), but worth recording as a requirement: without it, the AI "wakes up" with no idea what happened with the human, and the user feels like they're starting over, which kills the feature's usefulness. The BFF should inject a summary (or the transcript) of the human segment back into the model's context before resuming. The library only delivers the `handoff-end` event; what to do with the history is the BFF's responsibility.
- **Who decides to go back** could be the agent (marking it resolved), the user (asking to return to the bot), or agent inactivity via timeout — the protocol doesn't assume which; `handoff-end` carries a `reason` field (`agent_resolved | user_requested | timeout`), and the logic for each trigger belongs to the BFF.
- **WebSocket reconnection.** SSE already solves this with `Last-Event-ID`; WS has no native equivalent mechanism, and it matters more here — a dropped connection in the middle of a human conversation is worse than one in the middle of an AI response. This needs a per-session sequence number (`seq`) on every message plus a resume handshake on reconnect (`resume { lastSeq: N }`), so no message is duplicated or lost.

## 7. Integration / Context / BFF

```kotlin
interface ChatContextProvider {
    suspend fun headers(request: ChatRequest): Map<String, String>
    suspend fun contextPayload(request: ChatRequest): Map<String, Any?>
}

data class ChatConfig(
    val baseUrl: String,
    val contextProvider: ChatContextProvider,
    val actionHandler: ChatActionHandler,
    val telemetrySink: ChatTelemetrySink,
    val httpClientCustomizer: (HttpClientConfig) -> Unit = {}  // allows mTLS/pinning
)
```

- Headers: transport/session metadata (auth, correlation ID, language, app version).
- Body (`contextPayload`): domain context, treated as an opaque blob under a reserved key (`"context"`), never interpreted by the library.
- Called asynchronously before every request (covers cases like token refresh).
- The library never knows which LLM is on the other side — it only talks to `baseUrl`, which is the integrator's BFF.

## 8. Dynamic cards (own schema, inspired by Adaptive Cards)

Decision: do not adopt Adaptive Cards wholesale (large schema, geared toward the M365/Teams ecosystem, and there's a licensing note on the official binaries worth checking before any dependency — the source code is MIT but consumption of the binary packages is subject to a Microsoft EULA; we avoid that ambiguity). Instead, a minimal subset of our own, covering only the most commonly used elements:

```json
{
  "version": 1,
  "elements": [
    { "type": "text", "text": "Confirm payment of **$120.00**?" },
    { "type": "image", "url": "https://..." },
    { "type": "input.text", "id": "note", "placeholder": "Note (optional)" },
    { "type": "input.choice", "id": "method", "options": ["Pix", "Card"] },
    { "type": "action.button", "actionId": "authorize_payment", "label": "Authorize", "payload": { "amount": 120.00, "txId": "abc123" } }
  ]
}
```

- 100% native rendering (a small renderer per platform — manageable, because the schema is lean).
- `version` at the top, the same fallback principle used by Adaptive Cards (an older client that doesn't know how to render a new element ignores it and moves on).
- In-place updates via `card-update` (section 4.4) — the same "refresh" mechanism.

### 8.1 Who builds the card: the LLM or the BFF?

The LLM should not need to know how to assemble the JSON for the schema above. The recommended pattern decouples business intent from UI shape:

- The LLM only calls business-level semantic tools — `propose_payment(amount, recipient)`, `request_authorization(kind, details)` — small, stable schemas, using the standard tool-calling format every provider already supports today. This even matters for the model's own reliability: large or numerous tool schemas hurt the model's accuracy at picking the right tool, so keeping these tools small and at the business level (not the UI level) helps the model too, not just the architecture.
- Translating the tool call into the card JSON happens in the BFF — fixed, reviewed, tested code — never generated by the LLM and never decided by the library. It doesn't need to be a separate microservice (that would just add an unnecessary network hop); it lives inside the BFF itself, which is already the system's trust boundary.

Why this split matters, and isn't just aesthetic:

- A malformed/hallucinated card stops being a possible bug class — the LLM never needs to know the schema's exact shape, so it has no way to invent a field or an element type that doesn't exist.
- Schema evolution never touches a prompt — changing a layout, adding a new element type, or localizing text per language is work that only happens in the BFF.
- Reduces the prompt-injection surface at this specific point. If the LLM could freely emit card JSON, malicious content injected via a tool, a document, etc. would have a better chance of producing a convincing but malicious card. With the translation fixed in the BFF, the set of possible cards is always a small, known subset defined by code — never by generated text.

This is the same black-box logic from section 9, applied one step earlier: there, the library never decides what a sensitive action does; here, the LLM never decides the exact shape of what's shown on screen.

## 9. Actions and transactional security (black box)

```kotlin
interface ChatActionHandler {
    fun handle(actionId: String, payload: Map<String, Any?>)
}
```

- Tapping `action.button` → `ChatActionHandler.handle()` on the host. End of the library's responsibility.
- The library makes no network call, doesn't know what "payment" means, decides nothing.
- The host decides what to do (call biometrics, open a confirmation screen, invoke the app's own internal transaction SDK).
- Security principle: the card is a UX affordance, never an authorization mechanism. Every sensitive action must be independently revalidated by whoever executes it (the host / the BFF), exactly as it would be validated coming from any other channel — LLMs are susceptible to prompt injection via third-party content, so the card is never a source of authority.

## 10. Telemetry

```kotlin
sealed class ChatTelemetryEvent {
    data class SessionStarted(val sessionId: String) : ChatTelemetryEvent()
    data class MessageSent(val messageId: String) : ChatTelemetryEvent()
    data class FirstTokenLatency(val ms: Long) : ChatTelemetryEvent()
    data class StreamError(val code: String) : ChatTelemetryEvent()
    data class Reconnected(val attempt: Int) : ChatTelemetryEvent()
    data class CardRendered(val cardId: String) : ChatTelemetryEvent()
    data class ActionTapped(val actionId: String) : ChatTelemetryEvent()
    data class HandoffStarted(val reason: String) : ChatTelemetryEvent()
    data class HandoffCompleted(val durationMs: Long) : ChatTelemetryEvent()
    data class Custom(val name: String, val properties: Map<String, Any?>) : ChatTelemetryEvent()
}

interface ChatTelemetrySink {
    fun track(event: ChatTelemetryEvent)
}
```

Runs off the main thread, with failure isolation:

- Producers (any part of `commonMain`) push events into a bounded `Channel`, non-blockingly.
- A single consumer coroutine, on `Dispatchers.Default` (or a dedicated dispatcher with parallelism 1), drains the channel and calls `sink.track()`.
- The bounded channel uses a drop policy (drop-oldest) — protects against a slow or stuck sink.
- The call to `sink.track()` is wrapped in try/catch — an exception from the host never propagates into the app.
- On Kotlin/Native (iOS) this runs on genuine native threads, not a green-thread illusion — same mental model on both sides.

## 11. Markdown

- Rendering is always native (Text/AttributedString/Span) — never a WebView, given that LLM content is untrusted (a prompt-injection surface).
- iOS: leverage the work already underway on `SwiftStreamingMarkdown` (Microsoft) — including the planned `MarkdownImageProvider` contribution, which is directly applicable here.
- Android: `multiplatform-markdown-renderer` (mikepenz) is the most viable base today, but has an open issue specifically requesting incremental parsing for LLM streams (#315) — a contribution opportunity symmetric to the iOS one, rather than building a parser from scratch.
- Pragmatic v1: re-parsing the accumulated text on every chunk is acceptable performance-wise for typical chat messages; only optimize for incremental parsing if jank is observed on very long messages.

## 12. Remote UI config

- Asynchronous, non-blocking fetch, triggered when the session opens.
- Single-slot cache ("last successful config"), overwritten on every successful fetch — no TTL/expiration (unnecessary complexity for v1).
- Fetch failure → use the cache if it exists → otherwise, the default bundled in the library.
- Config is decided once at open time; it doesn't change mid-session even if a delayed fetch resolves later (avoids disruptive reflow).
- Simple storage (native file/KV — `UserDefaults`/`SharedPreferences`), no external persistence library.

### 12.1 Capability feature flags (upload, audio, transcription)

Rather than creating a new subsystem, these flags live inside the same `ChatUIConfig` from section 12 — reusing the fetch/cache/fallback mechanism that already exists, instead of inventing a second remote-configuration mechanism:

```kotlin
data class ChatFeatureFlags(
    val fileUploadEnabled: Boolean = false,
    val audioEnabled: Boolean = false,
    val audioTranscriptionPreferred: Boolean = false
)
```

Two different things decide the final state of each capability, and both need to be true — one doesn't substitute for the other:

- Product/compliance decision (`ChatFeatureFlags`, comes from the BFF via `ChatUIConfig`): the integrator may want to turn off file upload in a specific context (e.g., a support flow that, by policy, doesn't accept attachments), regardless of what the device supports.
- Actual device capability (checked locally, never over the network): microphone available, permission granted, and — only for transcription — whether an on-device speech-recognition engine is available for that language/device (`SFSpeechRecognizer.supportsOnDeviceRecognition` on iOS, `SpeechRecognizer.isOnDeviceRecognitionAvailable` on Android).

Final state = remote flag AND local capability. This resolves exactly the "depends on whether the device supports it" concern: if `audioTranscriptionPreferred = true` but the device doesn't support on-device recognition (older device, a language with no local model, permission denied), the library doesn't block the audio feature entirely — it simply sends the audio without a transcript, transparently.

Two design points worth recording:

- Transcription accompanies the audio, it doesn't replace it. When available, the transcribed text travels alongside the audio file (not instead of it) — the BFF decides what to do with each (for example, feeding the LLM only the text, while keeping the raw audio accessible for a human agent to hear tone of voice during a handoff).
- Safe default on fetch failure: unlike visual theming (which falls back to cache/bundled default), the library's bundled default for these flags should ship off (`false`). The library doesn't know the integrator's compliance policy, so requiring an explicit opt-in via config is safer than assuming "on" in the absence of a server response.

The same mechanism naturally extends to other capabilities (cards, handoff) if some integrator wants to disable them per context — it isn't exclusive to upload/audio, they just haven't been formalized here since they weren't requested yet.

## 13. Module structure (mono-repo)

```
:core            → pure KMP, no UI (transport, context, state machine, card schema, telemetry)
                   published as an AAR (Android) + XCFramework via Swift Package (iOS)
:ui-android      → Compose components, consumes :core
:ui-ios          → SwiftUI views, consumes :core (via SPM)
:sample-app-android / :sample-app-ios
                   consume the published artifacts normally (the same way a third-party integrator would),
                   serving as a demo/showcase and real integration QA
```

`:sample-app-*` should never have privileged access to `:core`/`:ui-*` — if it works well, that's proof the library integrates well the way any third-party client would.

### 13.1 Compatibility with UIKit and Android's classic View system

The UI is implemented once, in SwiftUI (iOS) and Jetpack Compose (Android) — not twice per platform. But the project's target audience (legacy native apps) includes many apps that haven't fully migrated to these declarative UI frameworks. Instead of rewriting the UI in each platform's older paradigm, each UI module exposes two entry points using each platform's official interop mechanism, without duplicating the actual implementation:

- `:ui-ios`: exposes `ChatView: View` (SwiftUI) as the primary entry point, plus a `ChatViewController: UIViewController` — a thin wrapper via `UIHostingController`, natively supported since iOS 13. Fully-UIKit apps import `ChatViewController` normally, just like they would any other screen.
- `:ui-android`: exposes `@Composable fun ChatScreen(...)` as the primary entry point, plus a `ChatFragment`/`ChatView` — a thin wrapper via `ComposeView` (the class that hosts Compose content inside the classic View system). Apps still built on XML/Fragments import this wrapper without needing to migrate to Compose.

Device compatibility isn't the real concern here — Compose only requires minSdk 21 (Android 5.0), which covers essentially 100% of active devices today. The real concern is legacy codebases (apps still on pure UIKit or pure View/XML), and that's solved by the interop layer, not by a second UI implementation.

A real point of attention (not trivial, worth testing early — inside the M2 walking skeleton, not left for later): the `UIHostingController`/`ComposeView` interop works well in the common case, but safe-area behavior, keyboard avoidance, and integration with the native navigation stack (`UINavigationController` / `Fragment`/Navigation Component) deserve practical validation early on, because that exact fit is what decides whether the experience feels genuinely native inside a legacy app, or shows up as a "patched-on" piece.

## 14. UI/UX direction (screens, states, and principles)

This project has had a deliberate gap up to this point: it solves architecture, not design. Since that isn't the author's strong suit, the goal of this section isn't a finished visual proposal — it's an inventory of screens/states and a set of objective principles, executable without requiring a dedicated designer for v1.

### 14.1 Screen and state inventory

1. **Core chat screen** — message list (user bubble, assistant bubble, card bubble, system/handoff banner), input bar (text, attachment, mic, send/stop), streaming indicator (distinguishing "thinking" from "generating"), a "jump to bottom" affordance when the user scrolls up during an active stream.
2. **Attachment flow** — picker (photo/file/camera), preview chips before sending, inline upload progress on the already-sent bubble.
3. **Audio** — record button (tap or press-and-hold, with waveform/timer), audio message bubble with play/pause + waveform + duration.
4. **Card rendering** — a bubble visually distinct from plain text (needs to "look like something else," not just another paragraph), action button states (normal, disabled right after the tap — important to avoid a double-tap on a sensitive action —, loading, error).
5. **Handoff transition** — system banner ("you're being transferred..."), agent-connection indicator, and once connected, a clear visual identity for the agent (name/avatar), distinct from the AI. A differentiated typing indicator (AI "generating" vs human "typing").
6. **Error/connection states** — a thin, non-blocking reconnection banner, a stream error with a retry affordance, an offline state.
7. **Config-driven theming** — what's themeable via `ChatUIConfig`: bubble colors, accent color, avatar/logo, disclaimer text, empty-state copy.

### 14.2 Objective principles (don't depend on "good taste" — they're rules)

- Each platform follows its own native convention: iOS respects HIG (SF Symbols, Dynamic Type, sheets, safe area), Android follows Material 3 (Material You tokens, elevation, IconButton/FAB). There isn't "one visual design" for both — there's a single principle ("look native to that platform") applied twice.
- AI and human must always be unambiguously distinguishable — never by color alone (accessibility), always with an explicit label/icon too. This isn't just good UX practice: it's already a transparency obligation under AI regulation in some jurisdictions (the EU's AI Act, for example, has a specific transparency requirement for chatbots) — worth treating as a requirement, not an aesthetic preference.
- Accessibility isn't optional: every interactive element (a card button, an audio control) needs a label for VoiceOver/TalkBack, must support large-font/Dynamic Type, and must never rely on color alone to convey state.
- Streaming motion needs to feel calm, not jittery — text "popping" too fast is more tiring than helpful. A configurable reveal throttle (Stream itself does this in their `StreamingText`, with a `chunkDelayMs`) is a good reference parameter.

### 14.3 How to close this gap without becoming a bottleneck

- A dedicated designer isn't necessary for v1 — what's necessary is treating the 14.1 inventory as an acceptance checklist and the 14.2 principles as rules, not suggestions.
- It makes sense for the fine visual details to come from a minimal "theme kit" (colors, typography, spacing) exposed via `ChatUIConfig`, rather than hardcoded — this pushes the visual decision to whoever integrates (who already has their own app's design system), and the library only guarantees the structure works well with any reasonable theme.
- Once the project grows, this is the first place worth bringing in a real designer — until then, the roadmap pipeline below doesn't get blocked by this.

## 15. Suggested roadmap (milestones)

1. **M0 — Transport:** `ChatTransport` (SSE), multiplatform Ktor Client, `Last-Event-ID` reconnection, event protocol parsing (section 4).
2. **M1 — Session and context:** `ChatSession` (state machine), `ChatContextProvider`, `ChatConfig`.
3. **M2 — Native text UI:** bubbles, streaming markdown (iOS via SwiftStreamingMarkdown, Android via mikepenz + incremental contribution).
4. **M2.5 — Chat resilience & history:** manual test scenarios (`tools/fake-sse-server`), SSE reconnection validation, non-duplicating message retry, server-fetched paginated history via `ChatHistoryProvider` (section 15.1). Inserted after M2 was already built; does not renumber the milestones below.
5. **M2.6 — Design System Adoption:** full replacement of M2's bubbles/banners/input bar/retry UX in both `:ui-android` and `:ui-ios` with a commissioned design (tokens, per-message failed-send treatment, chat-list sidebar, search overlay) — section 15.2. Also inserted without renumbering.
6. **M3 — Media:** file upload, native audio record/playback.
7. **M4 — Cards:** a generic, data-driven card-rendering engine (own `:cards-core`/`:cards-android`/`cards-ios` library) — section 15.3 replaces this milestone's earlier one-line placeholder with a concrete, validated scope.
8. **M5 — Telemetry:** `ChatTelemetrySink`, background pipeline with backpressure.
9. **M6 — Handoff:** `WebSocketChatTransport`, handoff events, presence/typing indicators.
10. **M7 — Sample apps:** `:sample-app-android` / `:sample-app-ios`, published to the app stores as a showcase.

### 15.1 M2.5 — Chat resilience & history (detail)

Inserted between M2 and M3 in implementation order only — the "M3 scope"/"M4 scope"/"M5 scope"/"M6 scope" comments already scattered through the codebase still refer to the original M3–M7 numbering above and are not renumbered by this insertion.

- **Manual test scenarios (`tools/fake-sse-server`):** multiple named, keyword-selected scripted scenarios (happy path, in-band error, handoff-only, long multi-chunk stream, plus the existing card/tool/handoff-with-drop scenario, renamed "reconnect") — still dev-only, still not shipped with `:core`.
- **Reconnection validation:** `SseChatTransport`'s existing `Last-Event-ID` reconnection (M0) needed no `:core` changes — validated by an added session-level test plus the "reconnect" fake-sse-server scenario. A visible "reconnecting..." banner (§14.1 item 6) is deferred as optional/stretch, not required for this milestone, since it needs a new signal path nothing in `:core` exposes yet.
- **Non-duplicating retry:** a failed turn's user message is now flagged (`ChatMessage.deliveryFailed`) rather than silently indistinguishable from a successful one; `ChatSession.retryLastFailedTurn()` re-sends it without appending a second bubble.
- **History:** `ChatHistoryProvider` (mirrors `ChatContextProvider`'s shape and philosophy exactly — `:core` never assumes a wire format, the host owns the actual fetch) restores a conversation server-side on session (re)open, with cursor-based "load older" pagination. No local persistence/KV layer, per design principle 6 and this milestone's explicit constraint.

### 15.2 M2.6 — Design System Adoption (detail)

A commissioned design (design tool export, reverse-engineered from a standalone HTML build; Android and iOS are the same design bar font/safe-area/star-icon) replaces M2's bare-Material-3/bare-SwiftUI visual language entirely, on both platforms — not additive, not optional. **Scope is deliberately narrowed to the single-chat surface** (see the deferred list below): the message thread, composer, header chrome, welcome/empty state, and all message/error/streaming states, built pixel-faithfully against the design's extracted style dictionary.

- **Tokens:** colors (`bg`/`surface`/`surfaceAlt`/`text`/`textSecondary`/`textTertiary`/`border`/`bubbleAssistant`/`inputBg`/`code`, light+dark), a 3-tier font scale (small/medium/large), a default accent (`#3B6EA5`) and destructive (`#C24545`) color — hardcoded for this milestone (system light/dark only), not routed through a `ChatUIConfig` that doesn't exist yet (§12). New `ThreadwireColors`/`ThreadwireTypography`/`ThreadwireTheme` files on both platforms.
- **Bubbles:** asymmetric "tail" corners (`bubbleUser` 18/18/4/18, `bubbleAssistant` 18/18/18/4), assistant bubble carries a 1px border, 10×14 padding — transcribed exactly from the design.
- **Composer:** a single rounded pill (`inputRow`) holding attach + text field + one trailing control (send/stop/mic 32px circles) — not separate elements.
- **§14.2 resolution:** the design has no author labels at all (position/color only). To preserve §14.2's non-color-alone guarantee, user bubbles stay unlabeled (matches the design) but AI/Agent/System messages keep a minimal small-type author label.
- **Per-message failed-send:** replaces the session-level `ErrorBanner`/`ErrorBannerView` for send failures with a small "Failed to send" + inline retry icon directly under the specific failed bubble (`ChatMessage.deliveryFailed`, already modeled by M2.5).
- **History-load-failure:** a slim inline "Couldn't load earlier messages. Retry" treatment, reading `ChatState.historyError` (already modeled by M2.5).
- **Streaming / welcome:** animated typing dots and a text-only welcome/empty state ("How can I help you today?" + optional host-supplied suggestion chips). The design's decorative assistant avatar was intentionally dropped — for an LLM, a persona avatar reads as misleading.
- **Scroll behaviour (market-standard, iMessage/WhatsApp-style):** opens on the most recent message; auto-follows the streaming tail *smoothly* only while the user is parked at the bottom (never yanks a reader who scrolled up); a 36px circular jump-to-bottom button appears once the user has scrolled up far enough that the last message is no longer readable, and tapping it smoothly scrolls to the true bottom and re-arms following. Android uses a `reverseLayout` LazyColumn (item 0 at the bottom, list growing upward) - the standard chat pattern: the newest message stays pinned to the bottom natively and a streaming reply grows upward while its bottom stays glued to the viewport, so there's no fragile scroll math (a forward layout mis-fired on a single screen-tall message and stuck at its top). "At the bottom" is `!canScrollBackward`; the jump button shows whenever the user is scrolled off it. iOS keeps a forward `ScrollView`, following via `ScrollViewReader.scrollTo(anchor: .bottom)` (which reaches the true content bottom) and reading scroll position via `onScrollGeometryChange` (iOS 18).
- **Timestamps:** each bubble shows a locale-aware send time from a new client-clock `ChatMessage.timestampMillis` (`0` = unknown, e.g. history messages with no server time — no timestamp shown). Stamped at the impure boundary (`ChatSession.sendMessage` for the user turn, the reducer via an injected `nowMillis` for assistant/agent turns, keeping the reducer pure/testable). On the assistant bubble the time sits inline as the trailing element of the reaction row.
- **Icons:** the design's line icons are rendered as **native vector paths** built from the design's own SVG path data (extracted from the export) — `ImageVector` on Android, a `Path`/`Canvas` renderer on iOS fed pre-flattened absolute move/line/cubic commands (arcs converted to Béziers once, offline) so iOS needs no runtime SVG engine. Identical glyphs on both platforms; tinted per usage. Manual cross-language path-data sync accepted as a risk (same posture as the tokens).
- **Header chrome:** the design's menu/title/search/close bar, with menu/search/close surfaced as **host navigation hooks** (`onMenuClick`/`onSearchClick`/`onClose`) rather than built-in behavior.

**Deferred out of M2.6** (design shows them, but they depend on features `:core` doesn't have yet — future milestones, not regressions): the built-in chat-list sidebar, in-chat search overlay, and `ChatListProvider` (a navigation/multi-session milestone); attachments + audio record/playback (M3); the media viewer, splash, auth/session-error full screens, header rename/delete/clear menu, and toasts (as-needed later); cards (M4).

### 15.3 M4 — Cards (detail, redefined)

Originally a one-line placeholder ("own schema, parser in `commonMain`, native renderers, `ChatActionHandler`"). Now concrete: a **generic, data-driven rendering engine**, not a fixed set of dedicated card types — deliberately scoped down from real Adaptive Cards (adaptivecards.microsoft.com), reusing its actual element vocabulary rather than inventing a competing one.

- **Module structure:** new, separate `:cards-core` (KMP schema/engine, `api(projects.core)`), `:cards-android` (Compose renderers), `cards-ios/` (SwiftUI Swift package) - a standalone library, not folded into `:ui-android`/`ui-ios`, so card rendering is usable outside a chat context too.
- **Element vocabulary:** `TextBlock`, `Image`, `Media`, `Container`, `ColumnSet`/`Column`, `FactSet`, `ActionSet`, `Input.Text`/`Input.ChoiceSet`/`Input.Toggle`/`Input.Date`/`Input.Time`/`Input.Rating`, plus two custom additions real AC doesn't cleanly cover for chat (`Carousel`, `Stepper`). Validated by composing all 17 illustrative card layouts from the commissioned design (confirm/summary/carousel/rating/choices/form/location/checklist/datetime/progress/payment/contact/poll/weather/ordertracking/video) purely as data - zero new Kotlin/Swift code per new card design a BFF author invents later.
- **`:core` stays untouched:** `MessagePart.Card`'s `body: JsonObject?` stays opaque; `:cards-core` is what interprets it, matching the existing "never interpret card/tool payloads" principle.
- **Interaction round-trip:** most actions synthesize a chat message via the existing `ChatSession.sendMessage` (no new `:core` API needed) - a per-action `mode: notify | hostAction` flag (data-driven, not a hardcoded type list) instead routes genuinely sensitive actions (payment, saving a contact) through a new `CardActionHandler`, a cards-scoped sibling of §9's existing `ChatActionHandler` pattern, not a replacement for it.
- **Wire schema:** documented separately in `docs/cards-wire-schema.md`, marked as this project's own proposal pending real-BFF validation - §8 below is updated to reflect the vocabulary instead of its earlier generic sketch.

## 16. Risks and open questions

- **Monetization:** free MIT/Apache core; the most realistic sustainability path is a separate paid layer for handoff orchestration (queueing, routing, SLA, dashboard) — the same pattern validated by Sendbird Desk/CometChat Agents. Donations (GitHub Sponsors/Patreon) treated as a bonus, not as a sustainability plan.
- **Institutional adoption:** the project isn't built for any specific company; the most likely scenario is organic adoption by teams with the same pain profile (legacy native apps, high performance/audit requirements).
- **Platform competition** (low priority, not urgent): Apple/Google could eventually ship official native AI chat components. This doesn't seem to be on their radar today — their current focus is model access (Foundation Models, Firebase AI Logic) and system assistants (Siri, Gemini), not an embeddable chat UI kit for a third-party backend. Worth monitoring only, not treating as an active threat.
