import Foundation
import ThreadwireCore

/// Bridges `ChatSession.state` (a Kotlin `StateFlow`) into SwiftUI's observation model.
/// Uses `ChatSession.observeState(onChange:)` (a plain callback API `:core` exposes
/// specifically for this) rather than collecting the `StateFlow`/`Flow` directly - no
/// SKIE or equivalent tooling is used in this project, and raw suspend-function/Flow
/// interop from Swift isn't ergonomic without it.
///
/// NOTE: this file's exact interop shape (in particular, whether `ChatSubscription`
/// and the `(ChatState) -> Void` closure type bridge as cleanly as assumed here) has
/// not been verified against a real Xcode build yet - flagged per the M2 plan's open
/// question about `:core`'s Kotlin/Native Swift export ergonomics.
@MainActor
final class ChatSessionStore: ObservableObject {
    @Published private(set) var state: ChatState

    private let session: ChatSession
    private var subscription: ChatSubscription?

    init(session: ChatSession) {
        self.session = session
        // StateFlow<T>'s generic `value` property is type-erased across the K/N Swift
        // bridge (no SKIE) - it comes back as `Any?`, not `ChatState`, so it needs an
        // explicit cast. `observeState`'s callback isn't affected: its parameter type
        // is a concrete ChatState in Kotlin, not a generic type parameter.
        self.state = session.state.value as! ChatState
        self.subscription = session.observeState { [weak self] newState in
            DispatchQueue.main.async {
                self?.state = newState
            }
        }
    }

    deinit {
        subscription?.close()
    }

    func sendMessage(_ text: String) {
        session.sendMessage(text: text)
    }

    func cancelCurrentTurn() {
        session.cancelCurrentTurn()
    }

    /// Re-sends the last failed turn's user message in place (M2.5 - `:core`'s
    /// `retryLastFailedTurn()` now owns this, rather than the old UI-layer pattern of
    /// re-calling `sendMessage` with the same text, which always appended a second
    /// bubble). Name kept as-is - `ChatView.swift` already calls this method.
    func retryLastUserMessage() {
        session.retryLastFailedTurn()
    }

    /// Explicit "load older messages" pagination call (M2.5) - no-op in `:core` if the
    /// host supplied no history provider, a fetch is already in flight, or there's
    /// nothing older left.
    func loadOlderHistory() {
        session.loadOlderHistory()
    }
}
