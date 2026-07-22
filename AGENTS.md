# AGENTS.md — Threadwire project context for AI agents

Read this first for fast orientation. For full rationale, see [docs/design-doc.md](./docs/design-doc.md); for workflow, see [CONTRIBUTING.md](./CONTRIBUTING.md).

## What this project is

Threadwire is an open-source, native (Swift/Kotlin) chat SDK for iOS and Android that embeds LLM-powered conversations into existing apps. It is a UI + transport library only — it never talks to LLM providers directly, never executes sensitive actions, and never runs its own messaging infrastructure. Everything business-specific is delegated to the integrator's own HTTP backend (a BFF).

## Non-negotiable principles (design doc §2) — do not violate these when writing code

1. UI is always native: SwiftUI (iOS) / Jetpack Compose (Android). Never a JS bridge, never a cross-platform canvas.
2. The library only talks to the host-configured `baseUrl` (the integrator's BFF) — never an LLM provider directly, never stores a provider API key.
3. Card action taps only call `ChatActionHandler.handle(actionId, payload)` on the host. The library makes no network call itself when an action is tapped.
4. No bundled telemetry vendor — only the `ChatTelemetrySink` contract.
5. Kotlin Multiplatform (KMP) is scoped to `commonMain` logic (transport, state, parsing) — never UI.
6. Business context injected by the host (headers/body) is opaque — never parsed or interpreted by the library.
7. Secondary config (e.g. remote UI theming) degrades gracefully to cache, then to a bundled default, on network failure. Feature flags (upload/audio/transcription) default to `false`/off on failure instead — see design doc §12.1.
8. Prefer aligning with an existing market convention (wire protocol, card schema) over inventing a new one from scratch.

## Architecture at a glance

```
Native UI (SwiftUI / Jetpack Compose) — never shared
        │ observes StateFlow / calls methods
commonMain (KMP) — ChatSession (state machine), ChatTransport (SSE ⇄ WebSocket),
                   ChatContextProvider, ChatActionHandler, ChatTelemetrySink,
                   card schema parser, ChatUIConfig
        │ HTTPS (SSE / WebSocket)
Integrator's BFF — out of scope; owns LLM choice, context, handoff routing, action execution
```

- **Transport**: SSE by default (AI phase), promotes to WebSocket only once `handoff-start` fires (human handoff phase), demotes back to SSE on `handoff-end`. Wire protocol is SSE with typed start/delta/end parts, ID-reconciled (Vercel AI SDK-inspired) plus Anthropic-style indexed content blocks. See design doc §4 and §6.
- **Cards**: own minimal schema (not Adaptive Cards), versioned, rendered 100% natively. The LLM never generates card JSON directly — it calls small business-level tools, and only the BFF translates those into card JSON. See design doc §8.
- **Handoff**: bidirectional cycle (`ai_active ⇄ handoff_pending ⇄ handoff_active`), not a one-way escalation. See design doc §6.1.

## Module layout (mono-repo, target structure)

```
:core             pure KMP, no UI — transport, context, state machine, card schema, telemetry
                  (exists: protocol/ (M0, event parsing), transport/ (M0, SSE transport),
                  session/ (M1, ChatSession/ChatContextProvider/ChatConfig/ChatState).
                  Cards (M4), telemetry (M5), WebSocket handoff (M6) not built yet)
:ui-android       Jetpack Compose, consumes :core (M2 - ChatScreen, bubbles, streaming
                  markdown via mikepenz, interop ChatView/ChatFragment)
:ui-ios           SwiftUI via a local SPM package at ui-ios/ (M2 - ChatView, bubbles,
                  streaming markdown via microsoft/SwiftStreamingMarkdown, interop
                  ChatViewController). iOS 16+ floor (SwiftStreamingMarkdown's own
                  requirement, not :core's or UIHostingController's).
:sample-app-android / sample-app-ios
                  consume :ui-android/:ui-ios (not :core directly) - never privileged
                  access to :core/:ui-* internals, the same way any third-party
                  integrator would
:tools:fake-sse-server
                  dev-only local Ktor server for manually testing SseChatTransport
                  (scripted event sequence + deliberate mid-stream drop) - never a
                  dependency of :core, not part of what gets published
```

`:core` produces a real combined `ThreadwireCore.xcframework` via the Kotlin Multiplatform `XCFramework` Gradle DSL (task `assembleThreadwireCoreXCFramework`, output at `core/build/XCFrameworks/<debug|release>/`) - `:ui-ios/Package.swift` references it as a local `binaryTarget`. `sample-app-ios` separately still embeds `:core` via its own direct Xcode Run Script (`./gradlew :core:embedAndSignAppleFrameworkForXcode`) - having it wired in two places is a known, deliberate loose end from M2, not yet cleaned up. **Adding `ui-ios` as a local SPM dependency to `sample-app-ios.xcodeproj` is a manual Xcode step (File → Add Package Dependencies → Add Local...) - never hand-edit `.pbxproj` for this.**

`:core`'s transport/protocol layer (`com.fsk.threadwire.protocol.ChatEvent`/`ChatEventParser`, `com.fsk.threadwire.transport.ChatTransport`/`SseChatTransport`) stays decoupled from the session layer - `ChatTransport.streamEvents` takes a `TransportRequest` (url/headers/body, renamed from `ChatRequest` in M1 to avoid colliding with the new session-level type below). `com.fsk.threadwire.session` (M1) adds: `ChatRequest` (minimal, pre-headers/context view handed to `ChatContextProvider` - not the same type as `TransportRequest`), `ChatContextProvider`/`ChatConfig` (design doc §7 - `ChatConfig` intentionally omits `actionHandler`/`telemetrySink` until M4/M5 exist), `ChatState`/`ChatMessage`/`MessagePart`/`SessionPhase` (the state machine's data shape - not specified in the design doc beyond "`StateFlow<ChatState>`", designed from scratch), `ChatStateReducer` (pure fold, ID-reconciliation of repeated parts lives here), and `ChatSession` (ties it together, one turn per `sendMessage(text: String)` call). `ChatSession` depends on the `ChatTransport` interface, never `SseChatTransport` directly, so M6 can later substitute a phase-aware SSE/WebSocket router without a breaking change. `SessionPhase` already models the full handoff cycle (`AiActive`/`HandoffPending`/`HandoffActive`) reacting to M0's already-parsed handoff events, but the transport underneath stays SSE-only until M6 actually builds WebSocket support.

## Conventions

- Commits: [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`, `build:`, `ci:`), optionally scoped (`feat(core): ...`).
- Branches: prefixed to match commit type (`feat/`, `fix/`, `docs/`, `chore/`, `refactor/`, `test/`, `build/`, `ci/`).
- Code and docs are written in English; project discussion with the maintainer may happen in Portuguese.

## Branching model

`dev` is the default/integration branch — branch off it, PR back into it (squash merge). `main` only moves via a release PR from `dev` (merge commit, not squash) and is what will trigger CI/CD artifact/version publishing once that exists. Both branches are protected on GitHub: no direct pushes, not even for admins. See [CONTRIBUTING.md](./CONTRIBUTING.md) for the full workflow.

## Current status

Design phase is complete (design doc v0.1). M0 (SSE transport), M1 (session state machine), and M2 (native UI: `:ui-android`/`:ui-ios`, bubbles, streaming markdown) are implemented (see Module layout above) - pending the maintainer's own build/manual validation before merge. M2 particularly depends on manual validation: the §13.1 UIKit/View-system interop concern (safe-area, keyboard avoidance, navigation-stack integration for `ChatViewController`/`ChatFragment`) can only be confirmed by running the app, not by the code compiling. Roadmap order: M0 SSE transport → M1 session/context → M2 native text UI → M3 media → M4 cards → M5 telemetry → M6 WebSocket handoff → M7 sample apps. Check `README.md#roadmap` and recent commits/issues before assuming any milestone is further along than it is.

## Before implementing something with an open design gap

If a task touches a part of the design that isn't fully specified yet (naming, exact schema fields, which optional pieces to build), stop and ask the maintainer rather than assuming a default — this project treats that as a hard rule, not a suggestion.

## Never merge automatically; let the maintainer validate manually

An AI agent working on this repo may open PRs (`gh pr create` or equivalent), but must **never merge a PR** — not even one it opened itself — unless explicitly told to merge that specific PR in that specific moment. The maintainer always reviews and merges PRs himself. A past approval to merge one PR does not carry over to the next one; treat every merge as needing fresh, explicit permission.

Likewise, don't proactively run lint, test suites, or build/run the app "to verify" a change unless asked to in the moment. The maintainer prefers to do that validation himself — describe what changed and what to check instead of spending effort re-deriving it.

The repo auto-deletes head branches on merge (GitHub setting), so there's no remote branch to clean up after a merge. When told a PR was merged, sync local state: `git checkout dev && git pull && git fetch --prune && git branch -d <merged-branch>`.
