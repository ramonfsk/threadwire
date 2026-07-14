# Contributing to Threadwire

Threadwire is in early implementation — the [design doc](./docs/design-doc.md) is the source of truth for architecture decisions, and most of the surface area described there doesn't exist as code yet. Check the [roadmap](./README.md#roadmap) before assuming a milestone is further along than it is.

## Most valuable contribution right now

Feedback on the [design doc](./docs/design-doc.md) — open a **Design feedback** issue if something looks wrong, underspecified, or worth challenging. This matters more than code right now, since the architecture is the foundation everything else builds on.

## Reporting bugs / requesting features

Use the **Bug report** or **Feature request** issue templates. For anything security-sensitive, see [SECURITY.md](./SECURITY.md) instead of opening a public issue.

## Branch naming

Prefix branches with the [Conventional Commits](https://www.conventionalcommits.org/) type they represent:

| Prefix | Use for |
|---|---|
| `feat/` | New functionality |
| `fix/` | Bug fixes |
| `docs/` | Documentation only |
| `chore/` | Tooling, dependencies, repo maintenance |
| `refactor/` | Code change that neither fixes a bug nor adds a feature |
| `test/` | Adding or correcting tests |
| `build/` | Build system or module structure changes |
| `ci/` | CI/CD pipeline changes |

Example: `feat/sse-transport`, `fix/reconnect-backoff`.

## Commit messages

Follow [Conventional Commits](https://www.conventionalcommits.org/): `<type>(<scope>): <description>`.

Examples:
- `feat(core): add Last-Event-ID reconnection to SseChatTransport`
- `fix(ui-ios): correct safe-area inset in ChatViewController wrapper`
- `docs: add design feedback issue template`

Scope is optional but encouraged when the change is localized to one module (`core`, `ui-ios`, `ui-android`, `sample-android`, `sample-ios`).

## Pull requests

- One logical change per PR — matches one milestone or a clearly scoped slice of one.
- Fill in the PR template completely, including the non-negotiable-principles checklist ([design doc §2](./docs/design-doc.md#2-design-principles-non-negotiable)).
- `:sample-app-*` must only consume published artifacts, never get privileged access to `:core`/`:ui-*` — a PR that breaks this isolation will be asked to change, regardless of what it's trying to demonstrate.

## Code of conduct

This project follows the [Contributor Covenant](./CODE_OF_CONDUCT.md).

## For AI agents / assistants

See [AGENTS.md](./AGENTS.md) for a condensed project context designed to be read quickly by an AI coding assistant.
