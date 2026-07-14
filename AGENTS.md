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
                  (exists: toolchain smoke test only, no SDK logic yet)
:ui-android       Jetpack Compose, consumes :core (not created yet)
:ui-ios           SwiftUI (via SPM), consumes :core (not created yet)
:sample-app-android / sample-app-ios
                  consume :core directly for now (own native UI, no shared UI module) —
                  will move to consuming :ui-android/:ui-ios once those exist, and must
                  never get privileged access to :core/:ui-* internals
```

The iOS host app builds `:core` as a framework named `ThreadwireCore` (`import ThreadwireCore` in Swift), embedded via an Xcode Run Script phase that calls `./gradlew :core:embedAndSignAppleFrameworkForXcode`.

## Conventions

- Commits: [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`, `build:`, `ci:`), optionally scoped (`feat(core): ...`).
- Branches: prefixed to match commit type (`feat/`, `fix/`, `docs/`, `chore/`, `refactor/`, `test/`, `build/`, `ci/`).
- Code and docs are written in English; project discussion with the maintainer may happen in Portuguese.

## Branching model

`dev` is the default/integration branch — branch off it, PR back into it (squash merge). `main` only moves via a release PR from `dev` (merge commit, not squash) and is what will trigger CI/CD artifact/version publishing once that exists. Both branches are protected on GitHub: no direct pushes, not even for admins. See [CONTRIBUTING.md](./CONTRIBUTING.md) for the full workflow.

## Current status

Design phase is complete (design doc v0.1). The mono-repo has a Kotlin Multiplatform toolchain smoke test (`:core` + `:sample-app-android` + `sample-app-ios`, each with a trivial "hello world" greeting) — no real SDK logic (transport, session, cards, etc.) exists yet. Note: `sample-app-ios`'s Xcode project/target/scheme are still internally named `iosApp` (only the containing folder was renamed so far). Roadmap order: M0 SSE transport → M1 session/context → M2 native text UI → M3 media → M4 cards → M5 telemetry → M6 WebSocket handoff → M7 sample apps. Check `README.md#roadmap` and recent commits/issues before assuming any milestone is further along than it is.

## Before implementing something with an open design gap

If a task touches a part of the design that isn't fully specified yet (naming, exact schema fields, which optional pieces to build), stop and ask the maintainer rather than assuming a default — this project treats that as a hard rule, not a suggestion.
