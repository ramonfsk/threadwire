import SwiftUI
import ThreadwireCore

/// Primary `:ui-ios` entry point (design doc §13.1). Session lifecycle stays
/// host-owned when using the `session:` initializer - callers are responsible for
/// `ChatSession.close()`. Use the `config:sessionId:` initializer for a
/// self-contained session that's closed automatically.
public struct ChatView: View {
    @StateObject private var store: ChatSessionStore
    @State private var inputText: String = ""
    @State private var isAtBottom: Bool = true

    public init(session: ChatSession) {
        _store = StateObject(wrappedValue: ChatSessionStore(session: session))
    }

    public init(config: ChatConfig, sessionId: String) {
        // Companion-object factory - a predictable Kotlin/Native Swift-export shape,
        // unlike top-level file-facade functions. See ChatSession.Companion.create's
        // KDoc in :core for why. Not yet verified against a real Xcode build.
        let session = ChatSession.companion.create(config: config, sessionId: sessionId)
        self.init(session: session)
    }

    public var body: some View {
        VStack(spacing: 0) {
            PhaseBannerView(phase: store.state.phase)
            if let error = store.state.lastError {
                ErrorBannerView(error: error, onRetry: { store.retryLastUserMessage() })
            }
            ZStack(alignment: .bottom) {
                MessageListView(state: store.state, isAtBottom: $isAtBottom)
                if !isAtBottom {
                    Button("Jump to latest") {
                        isAtBottom = true
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color(.systemBackground))
                    .clipShape(Capsule())
                    .shadow(radius: 2)
                    .padding(.bottom, 8)
                    .accessibilityLabel("Jump to latest message")
                }
            }
            ChatInputBar(
                text: $inputText,
                isAwaitingResponse: store.state.isAwaitingResponse,
                onSend: {
                    let toSend = inputText
                    guard !toSend.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                    store.sendMessage(toSend)
                    inputText = ""
                },
                onStop: { store.cancelCurrentTurn() }
            )
        }
    }
}
