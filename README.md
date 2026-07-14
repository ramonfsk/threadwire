<div align="center">

# Threadwire

**A native chat SDK for Android & iOS to embed LLM-powered conversations in existing apps — no bridge, no bundled backend, no vendor lock-in.**

[![License: Apache 2.0](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/platform-iOS%20%7C%20Android-lightgrey)]()
[![Status](https://img.shields.io/badge/status-pre--alpha%20%2F%20design%20phase-orange)]()

[Design Doc](./docs/design-doc.md) · [Roadmap](#roadmap) · [Contributing](#contributing)

</div>

---

## Why Threadwire

Most teams shipping an AI chat screen inside a mobile app reach for React Native to move fast — and then hit the bridge exactly where it hurts: debugging, performance, and predictability, in the apps that can least afford it (banking, regulated, or large legacy native codebases).

There wasn't a native-first alternative that treats the LLM chat as *just a UI component* — one you can drop into an existing Swift/Kotlin app, wire up to your own backend, and move on. Threadwire is built to be that.

## What Threadwire is (and isn't)

**Is:**
- A genuinely native chat UI for iOS (SwiftUI) and Android (Jetpack Compose)
- BYO-backend and BYO-LLM: you own the BFF, the model choice, and the business logic
- Open source, no bundled telemetry vendor, no forced infrastructure

**Isn't:**
- Not an LLM provider — Threadwire never talks to OpenAI/Anthropic/Gemini/etc. directly, only to an HTTP endpoint you configure
- Not a full messaging platform — no multi-user rooms, no presence-at-scale, no moderation suite. It's scoped to one user talking to an AI (with optional handoff to one human agent)
- Not cross-platform UI — no React Native, no Flutter, no shared Compose Multiplatform rendering. The two native UI layers share only the underlying Kotlin Multiplatform core (networking, state, protocol parsing) — never the rendered UI itself

## Key features

- ⚡ **Streaming over SSE** with automatic reconnect (`Last-Event-ID`) — survives backgrounding and network switches without custom retry logic
- 📝 **Native, streaming-aware markdown rendering** — built for incomplete markdown mid-stream, not just finished messages
- 📎 **File upload** and 🎙️ **native audio record/playback**
- 🧩 **Lightweight dynamic cards** — a minimal, purpose-built schema (inspired by, not a clone of, Adaptive Cards) for buttons, inputs, and structured content driven by your backend
- 🔁 **Human handoff** — transport upgrades from SSE to WebSocket only when a human agent actually joins the conversation
- 🔒 **Security-first action model** — the SDK never executes sensitive actions itself. A card button tap is handed off to your app's own code; Threadwire doesn't know or care what "authorize payment" means
- 📊 **Vendor-agnostic telemetry** — a plain event contract; you decide where events go (Datadog, Firebase, your own pipeline, or nowhere), off the main thread, with failure isolation
- 🧵 **KMP-shared core, fully native UI** — no JS bridge, no bundled JS runtime, no canvas-rendered UI standing in for native controls

## How it compares

| | **Threadwire** | Stream / Sendbird / CometChat | assistant-ui / Vercel AI SDK UI | Flutter AI Toolkit |
|---|:---:|:---:|:---:|:---:|
| Native UI, no bridge | ✅ | ✅ (native SDKs) | ❌ web/React | ❌ Flutter/Skia |
| BYO backend & LLM | ✅ | Partial (AI is an add-on) | ✅ | ✅ |
| No messaging infra required | ✅ | ❌ full infra, MAU-based pricing | — | — |
| Built-in human handoff | ✅ | ✅ (paid tier) | ❌ | ❌ |
| License | OSS | Proprietary SaaS | OSS | OSS |

Threadwire doesn't compete with Stream/Sendbird/CometChat on messaging infrastructure — it competes on being the lightest possible way to give one user a native AI chat screen, backed by whatever you already run.

## Status

**This project is in early implementation.** The design doc is final for v0.1. M0 (SSE transport + event protocol parsing) and M1 (session state machine + context injection) are implemented in `:core` — see [roadmap](#roadmap) for what's next.

The full architecture — wire protocol, transport design, threat model for the action/telemetry boundaries, and module structure — is written up in [`docs/design-doc.md`](./docs/design-doc.md). Feedback and discussion on the design are very welcome via issues.

## Project layout

This is a Kotlin Multiplatform project targeting Android and iOS.

- [`core`](./core/src) — pure KMP code shared between platforms (transport, state, parsing; no UI — see [design doc §2](./docs/design-doc.md#2-design-principles-non-negotiable)). Most important subfolder: [`commonMain`](./core/src/commonMain/kotlin).
- [`sample-app-android`](./sample-app-android) — Android host app; consumes `:core` and renders its own native Jetpack Compose UI directly (never a shared/cross-platform UI module).
- [`sample-app-ios`](./sample-app-ios/sample-app-ios) — iOS host app (Xcode project); consumes `:core` (built as the `ThreadwireCore` framework) and renders its own native SwiftUI.

`:ui-android` and `:ui-ios` (the actual reusable chat UI modules) don't exist yet — see the [roadmap](#roadmap).

- [`tools/fake-sse-server`](./tools/fake-sse-server) — a small local Ktor server for manually exercising `SseChatTransport` against a real HTTP connection (scripted event sequence, deliberate mid-stream drop to test `Last-Event-ID` reconnection). Not shipped with `:core`, dev-only.

### Running the apps

- Android app: `./gradlew :sample-app-android:assembleDebug`
- iOS app: open [`sample-app-ios`](./sample-app-ios) in Xcode and run it from there.
- Fake SSE server (manual transport testing): `./gradlew :tools:fake-sse-server:run`, then `curl -N -H "Accept: text/event-stream" -X POST http://localhost:8080/chat`

### Running tests

- Android tests: `./gradlew :core:testAndroidHostTest`
- iOS tests: `./gradlew :core:iosSimulatorArm64Test`

## Planned API shape

> ⚠️ Illustrative only — not implemented yet, syntax subject to change.

```kotlin
// commonMain — shared across iOS and Android
val config = ChatConfig(
    baseUrl = "https://your-bff.example.com/chat",
    contextProvider = object : ChatContextProvider {
        override suspend fun headers(request: ChatRequest) = mapOf(
            "Authorization" to "Bearer ${currentUserToken()}"
        )
        override suspend fun contextPayload(request: ChatRequest) = mapOf(
            "accountTier" to "premium"
        )
    },
    actionHandler = object : ChatActionHandler {
        override fun handle(actionId: String, payload: Map<String, Any?>) {
            // Your app decides what "authorize_payment" means. Threadwire never does.
        }
    },
    telemetrySink = object : ChatTelemetrySink {
        override fun track(event: ChatTelemetryEvent) = analytics.log(event)
    }
)
```

```swift
// iOS — SwiftUI
ChatView(config: config)
```

```kotlin
// Android — Jetpack Compose
ChatScreen(config = config)
```

## Architecture at a glance

```
Native UI (SwiftUI / Jetpack Compose)  ── never shared ──
              │
     commonMain (Kotlin Multiplatform)
     transport · session state · context · card schema · telemetry
              │
     Your BFF  ── your LLM, your rules, your handoff logic ──
```

Full breakdown in the [design doc](./docs/design-doc.md).

## Roadmap

- **M0** — SSE transport + event protocol ✅ merged
- **M1** — Session state machine + context injection ✅ implemented (pending maintainer review/validation)
- **M2** — Native text UI + streaming markdown
- **M3** — File upload + audio record/playback
- **M4** — Dynamic cards + action handler
- **M5** — Telemetry pipeline
- **M6** — WebSocket handoff transport
- **M7** — Public sample apps (iOS + Android)

See the [design doc](./docs/design-doc.md#15-suggested-roadmap-milestones) for details on each milestone.

## Contributing

The project isn't accepting external code contributions yet — it's still in the design/early-implementation phase. The most useful contribution right now is feedback on the [design doc](./docs/design-doc.md): open a **Design feedback** issue if something looks wrong, underspecified, or worth challenging.

See [CONTRIBUTING.md](./CONTRIBUTING.md) for issue templates, branch/commit conventions, and the [code of conduct](./CODE_OF_CONDUCT.md) — these apply to the maintainer's own workflow too, not just external contributors. Security issues should go to [SECURITY.md](./SECURITY.md) instead of a public issue. [AGENTS.md](./AGENTS.md) has a condensed project context for AI coding assistants.

## License

Apache License 2.0 — see [`LICENSE`](./LICENSE).
