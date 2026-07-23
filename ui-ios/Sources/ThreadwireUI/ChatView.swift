import SwiftUI
import ThreadwireCore

/// Primary `:ui-ios` entry point (design doc §13.1). Session lifecycle stays
/// host-owned when using the `session:` initializer - callers are responsible for
/// `ChatSession.close()`. Use the `config:sessionId:` initializer for a
/// self-contained session that's closed automatically.
///
/// [title]/[assistantName]/[suggestedPrompts] are host-supplied (`:core` has no
/// per-session display metadata). The header's menu/search/close controls are surfaced
/// as host navigation hooks ([onMenuClick]/[onSearchClick]/[onClose]) - a built-in
/// chat-list sidebar, in-chat search, and multi-session switching are deferred to a
/// future navigation/multi-session milestone (`:core` is single-session today).
public struct ChatView: View {
    @StateObject private var store: ChatSessionStore
    @State private var inputText: String = ""
    @State private var isAtBottom: Bool = true
    @State private var scrollToBottomRequest: Int = 0

    let title: String
    let assistantName: String
    let suggestedPrompts: [String]
    let onMenuClick: () -> Void
    let onSearchClick: () -> Void
    let onClose: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    public init(
        session: ChatSession,
        title: String = "Chat",
        assistantName: String = "AI",
        suggestedPrompts: [String] = [],
        onMenuClick: @escaping () -> Void = {},
        onSearchClick: @escaping () -> Void = {},
        onClose: @escaping () -> Void = {}
    ) {
        _store = StateObject(wrappedValue: ChatSessionStore(session: session))
        self.title = title
        self.assistantName = assistantName
        self.suggestedPrompts = suggestedPrompts
        self.onMenuClick = onMenuClick
        self.onSearchClick = onSearchClick
        self.onClose = onClose
    }

    public init(
        config: ChatConfig,
        sessionId: String,
        title: String = "Chat",
        assistantName: String = "AI",
        suggestedPrompts: [String] = [],
        onMenuClick: @escaping () -> Void = {},
        onSearchClick: @escaping () -> Void = {},
        onClose: @escaping () -> Void = {}
    ) {
        // Companion-object factory - a predictable Kotlin/Native Swift-export shape,
        // unlike top-level file-facade functions. See ChatSession.Companion.create's
        // KDoc in :core for why. Not yet verified against a real Xcode build.
        let session = ChatSession.companion.create(config: config, sessionId: sessionId)
        self.init(session: session, title: title, assistantName: assistantName, suggestedPrompts: suggestedPrompts, onMenuClick: onMenuClick, onSearchClick: onSearchClick, onClose: onClose)
    }

    public var body: some View {
        VStack(spacing: 0) {
            ChatHeaderBar(
                title: title,
                onMenuClick: onMenuClick,
                onSearchClick: onSearchClick,
                onCloseClick: onClose
            )
            PhaseBannerView(phase: store.state.phase)
            ZStack(alignment: .bottomTrailing) {
                if store.state.messages.isEmpty && !store.state.isLoadingHistory && store.state.historyError == nil {
                    WelcomeView(
                        suggestedPrompts: suggestedPrompts,
                        onPickPrompt: { store.sendMessage($0) }
                    )
                } else {
                    MessageListView(
                        state: store.state,
                        isAtBottom: $isAtBottom,
                        scrollToBottomRequest: scrollToBottomRequest,
                        onLoadOlderHistory: { store.loadOlderHistory() },
                        onRetryFailedMessage: { store.retryLastUserMessage() }
                    )
                }
                if !isAtBottom && !store.state.messages.isEmpty {
                    // scrollToBottomBtn: 36px circle with a down chevron, bordered,
                    // surface-filled, shadowed - the design's affordance, not a text pill.
                    Button {
                        scrollToBottomRequest += 1
                    } label: {
                        ThreadwireIconView(icon: .arrowDown, color: colorScheme == .dark ? ThreadwireColors.resolve(dark: true).text : ThreadwireColors.resolve(dark: false).text, size: 20)
                            .frame(width: 36, height: 36)
                            .background(colorScheme == .dark ? ThreadwireColors.resolve(dark: true).surface : ThreadwireColors.resolve(dark: false).surface)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(colorScheme == .dark ? ThreadwireColors.resolve(dark: true).border : ThreadwireColors.resolve(dark: false).border, lineWidth: 1))
                            .shadow(color: .black.opacity(0.16), radius: 6, y: 2)
                    }
                    .buttonStyle(.plain)
                    .padding(.trailing, 16)
                    .padding(.bottom, 16)
                    .accessibilityLabel("Jump to latest message")
                    .transition(.opacity.combined(with: .scale(scale: 0.9)))
                }
            }
            .animation(.easeInOut, value: isAtBottom)
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
        .threadwireTheme(dark: colorScheme == .dark)
        .background(colorScheme == .dark ? ThreadwireColors.resolve(dark: true).bg : ThreadwireColors.resolve(dark: false).bg)
    }
}
