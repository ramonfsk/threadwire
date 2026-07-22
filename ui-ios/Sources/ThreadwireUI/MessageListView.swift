import SwiftUI
import ThreadwireCore

/// Tracks the bottom-anchor's position so `ChatView` can show/hide a jump-to-bottom
/// button - single `PreferenceKey`-based approach (no `#available` branching), per the
/// M2 plan's decision. NOTE: the "near bottom" heuristic below is a rough v1
/// approximation, not yet visually verified on a real device/simulator.
private struct BottomAnchorOffsetKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

struct MessageListView: View {
    let state: ChatState
    @Binding var isAtBottom: Bool

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    ForEach(state.messages, id: \.localId) { message in
                        messageView(for: message)
                    }
                    if let streaming = state.streamingPhase() {
                        StreamingIndicatorView(phase: streaming)
                    }
                    Color.clear
                        .frame(height: 1)
                        .id(bottomAnchorId)
                        .background(
                            GeometryReader { geo in
                                Color.clear.preference(
                                    key: BottomAnchorOffsetKey.self,
                                    value: geo.frame(in: .named(scrollCoordinateSpace)).minY
                                )
                            }
                        )
                }
                .padding(12)
            }
            .coordinateSpace(name: scrollCoordinateSpace)
            .onPreferenceChange(BottomAnchorOffsetKey.self) { minY in
                isAtBottom = minY < UIScreen.main.bounds.height
            }
            .onChange(of: state.messages.count) { _ in
                if isAtBottom {
                    withAnimation {
                        proxy.scrollTo(bottomAnchorId, anchor: .bottom)
                    }
                }
            }
        }
    }

    // Plain if/else on MessageAuthor rather than switch/case: Kotlin enums bridged
    // via Kotlin/Native's Obj-C export aren't necessarily `@frozen` Swift enums, and
    // exhaustiveness-checking their exact bridged shape isn't reliable without a real
    // build (see M2 debugging notes) - direct equality checks sidestep that entirely.
    @ViewBuilder
    private func messageView(for message: ChatMessage) -> some View {
        if message.author == .user {
            UserBubbleView(message: message)
        } else if message.author == .ai {
            AssistantBubbleView(message: message)
        } else if message.author == .humanAgent {
            HumanAgentBubbleView(message: message, agentName: (state.phase as? SessionPhaseHandoffActive)?.agentName)
        } else if message.author == .system {
            SystemBannerView(message: message)
        } else {
            EmptyView()
        }
    }
}

private let bottomAnchorId = "bottom-anchor"
private let scrollCoordinateSpace = "messageScroll"
