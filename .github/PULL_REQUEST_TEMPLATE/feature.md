## What does this change?

<!-- One or two sentences. Link the issue it closes, if any: "Closes #123" -->

## Which milestone does this belong to?

<!-- M0 transport / M1 session / M2 UI / M3 media / M4 cards / M5 telemetry / M6 handoff / M7 samples / repo tooling -->

## Checklist

- [ ] Base branch is `dev` (feature/fix/docs/chore PRs never target `main` directly)
- [ ] Branch follows the naming convention (`feat/`, `fix/`, `docs/`, `chore/`, `refactor/`, `test/`, `build/`, `ci/`)
- [ ] Commits follow [Conventional Commits](https://www.conventionalcommits.org/)
- [ ] This change respects the non-negotiable principles in [design doc §2](../../docs/design-doc.md#2-design-principles-non-negotiable) (native UI only, no direct LLM provider calls, no sensitive actions executed by the library, no bundled telemetry vendor, KMP scoped to core only)
- [ ] `:sample-app-*` was not given privileged access to `:core`/`:ui-*` (if touched)
- [ ] Tests added/updated where relevant
- [ ] Docs updated where relevant (`README.md`, `docs/design-doc.md`, `AGENTS.md`)

## How was this tested?

<!-- Manual steps, device/simulator used, or automated test coverage -->
