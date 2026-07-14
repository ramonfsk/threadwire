# Security Policy

Threadwire is pre-release (design/early-implementation phase) — there are no published versions with security support yet. This policy will be updated once versioned releases exist.

## Reporting a vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Instead, report privately via GitHub's [private vulnerability reporting](https://github.com/ramonfsk/threadwire/security/advisories/new) for this repository, or email **ramonfsk@gmail.com** with:

- A description of the vulnerability and its potential impact
- Steps to reproduce (proof-of-concept code, if available)
- The version/commit affected

You should expect an initial response within 5 business days.

## Scope

**In scope:**
- The Threadwire library itself (`:core`, `:ui-android`, `:ui-ios`) — e.g. a way to bypass the `ChatActionHandler` black-box boundary and trigger a network call directly from the library, a card-schema parsing bug that allows executing unintended behavior, or transport/reconnection bugs that leak or duplicate data across sessions.

**Out of scope (by design, per [design doc §9](./docs/design-doc.md#9-actions-and-transactional-security-black-box)):**
- The integrator's own BFF, LLM provider, or action-handling logic — Threadwire treats sensitive actions as an opaque black box and never authorizes them itself; a vulnerability there belongs to the integrator's own security process, not this library's.
- The sample apps (`:sample-app-*`), which exist only as integration demos and intentionally consume the library like any third-party client.
