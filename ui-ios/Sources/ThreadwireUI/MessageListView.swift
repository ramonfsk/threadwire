import SwiftUI
import ThreadwireCore

/// The scrolling content's frame in the ScrollView's own coordinate space. Read from a
/// GeometryReader in the *background of the whole content* - the standard iOS-16 scroll-
/// offset technique. Earlier attempts measured a 1pt bottom-anchor sentinel, but a sentinel
/// (lazy or not) sits off-screen once the user scrolls up, and off-screen GeometryReaders in
/// a ScrollView don't reliably publish their preference - so isAtBottom stayed stuck true and
/// the jump button never appeared. The content background always overlaps the viewport, so it
/// keeps reporting. `minY` is the scroll offset (0 at top, negative as you scroll down) and
/// `height` is the full content height - together with the viewport height that's all we need.
private struct ContentRectKey: PreferenceKey {
    static var defaultValue: CGRect = .zero
    static func reduce(value: inout CGRect, nextValue: () -> CGRect) {
        value = nextValue()
    }
}

/// The ScrollView's own visible height (its viewport), measured from its background.
private struct ViewportHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

// Two thresholds form a hysteresis band (iMessage/WhatsApp-style), which is what stops the
// jump button flashing during streaming: `isAtBottom` (our "follow the tail" intent) only
// flips *true* once within `followThreshold` of the bottom, and only flips *false* once the
// user has scrolled up past `showButtonThreshold` (far enough that the last message is no
// longer readable). Between them the value holds, so a chunk arriving a beat ahead of the
// smooth auto-scroll never crosses a boundary.
private let followThreshold: CGFloat = 100
private let showButtonThreshold: CGFloat = 260

struct MessageListView: View {
    let state: ChatState
    @Binding var isAtBottom: Bool
    /// Incremented by the host (jump-to-latest tap) to request a scroll to the bottom -
    /// the button lives outside this view, but only this view owns the ScrollViewReader
    /// proxy, so the scroll is driven through this observed counter.
    var scrollToBottomRequest: Int = 0
    var onLoadOlderHistory: () -> Void = {}
    var onRetryFailedMessage: () -> Void = {}
    @State private var viewportHeight: CGFloat = 0

    @Environment(\.threadwireColors) private var colors
    @Environment(\.threadwireTypography) private var typography

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    // Plumbing-first pagination affordance (M2.5) - only ever shown for
                    // a session that actually has a ChatHistoryProvider (hasMoreHistory
                    // otherwise stays false forever, see ChatState's doc in :core).
                    if state.hasMoreHistory || state.isLoadingHistory || state.historyError != nil {
                        loadOlderHistoryRow
                    }
                    ForEach(state.messages, id: \.localId) { message in
                        messageView(for: message)
                    }
                    if let streaming = state.streamingPhase() {
                        StreamingIndicatorView(phase: streaming)
                    }
                    // Kept purely as a scroll target for ScrollViewReader (jump-to-latest
                    // and auto-follow both scrollTo this id). Bottom *detection* no longer
                    // relies on it - see ContentRectKey.
                    Color.clear.frame(height: 1).id(bottomAnchorId)
                }
                .padding(12)
                .background(
                    // Whole-content background: always overlaps the viewport, so its
                    // GeometryReader keeps publishing on every scroll (unlike an off-screen
                    // sentinel). minY = scroll offset, size.height = full content height.
                    GeometryReader { geo in
                        Color.clear.preference(
                            key: ContentRectKey.self,
                            value: geo.frame(in: .named(scrollCoordinateSpace))
                        )
                    }
                )
            }
            // The auto-follow itself (iOS 17+): `.defaultScrollAnchor(.bottom)` pins the
            // bottom edge natively as the streaming reply grows - the iOS equivalent of
            // Android's reverseLayout, and crucially it does NOT depend on `isAtBottom`. That
            // decouples following from the button: the old manual `onChange` follow read
            // `isAtBottom`, which spikes false for a frame when a big chunk grows the content
            // ahead of the animated scroll - flashing the button *and* stalling the follow
            // mid-message (the reported conflict). Native pinning has no such gap. A user who
            // scrolls up is left where they are.
            .modifier(BottomAnchoredScroll())
            // Bottom detection for the *button* on iOS 18+: ScrollGeometry reports real
            // contentOffset/containerSize/contentSize on every scroll, applied through the
            // hysteresis band. The PreferenceKey path below is the iOS 16/17 fallback only.
            .modifier(ScrollBottomTracker(isAtBottom: $isAtBottom))
            .coordinateSpace(name: scrollCoordinateSpace)
            .background(
                GeometryReader { geo in
                    Color.clear.preference(key: ViewportHeightKey.self, value: geo.size.height)
                }
            )
            .onPreferenceChange(ViewportHeightKey.self) { viewportHeight = $0 }
            .onPreferenceChange(ContentRectKey.self) { rect in
                if #available(iOS 18.0, *) { return } // handled by ScrollBottomTracker
                guard viewportHeight > 0, rect.height > 0 else { return }
                let maxScroll = rect.height - viewportHeight
                if maxScroll <= 0 { isAtBottom = true; return } // content fits
                // rect.minY is 0 at the top and negative as content scrolls up, so the
                // distance from the bottom is maxScroll + minY. Same hysteresis as iOS 18.
                let distance = maxScroll + rect.minY
                if distance <= followThreshold { isAtBottom = true }
                else if distance >= showButtonThreshold { isAtBottom = false }
            }
            // iOS 16 fallback follow only. On iOS 17+ `.defaultScrollAnchor(.bottom)` handles
            // following natively (see above). Here the scroll is *instant* (no withAnimation):
            // an animated scroll lags behind the growing content, and the resulting isAtBottom
            // spike is exactly what stalled the follow / flashed the button - snapping keeps
            // the tail pinned each chunk so that gap is as small as iOS 16 allows.
            .onChange(of: scrollTrigger) { _ in
                if #available(iOS 17.0, *) { return }
                if isAtBottom {
                    proxy.scrollTo(bottomAnchorId, anchor: .bottom)
                }
            }
            // Open on the most recent message (iOS 16; 17+ opens at the bottom via the anchor).
            .onAppear {
                if #available(iOS 17.0, *) { return }
                proxy.scrollTo(bottomAnchorId, anchor: .bottom)
            }
            // Explicit jump-to-latest tap (from the host's button) - always scrolls,
            // regardless of isAtBottom. This one *is* animated: it's a deliberate user
            // action, so the smooth scroll reads as intentional.
            .onChange(of: scrollToBottomRequest) { _ in
                withAnimation {
                    proxy.scrollTo(bottomAnchorId, anchor: .bottom)
                }
            }
        }
    }

    private var scrollTrigger: String {
        guard let last = state.messages.last else { return "" }
        let textLength = last.parts.reduce(0) { total, part in
            total + ((part as? MessagePartText)?.text.count ?? 0)
        }
        return "\(last.localId)-\(last.parts.count)-\(textLength)-\(last.isComplete)"
    }

    /// M2.6 - the design's slim inline treatment (gray text + link), replacing the
    /// earlier block-style row, for both the loading and the (previously unhandled)
    /// error state.
    @ViewBuilder
    private var loadOlderHistoryRow: some View {
        if state.isLoadingHistory {
            ProgressView()
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
        } else if state.historyError != nil {
            HStack(spacing: 4) {
                Text("Couldn't load earlier messages.")
                    .font(typography.meta)
                    .foregroundColor(colors.textSecondary)
                Button("Retry", action: onLoadOlderHistory)
                    .font(typography.meta)
                    .foregroundColor(colors.accent)
            }
            .padding(.vertical, 8)
        } else {
            Button("Load older messages", action: onLoadOlderHistory)
                .font(typography.meta)
                .foregroundColor(colors.accent)
                .frame(maxWidth: .infinity, minHeight: 44)
                .padding(.vertical, 8)
        }
    }

    // Plain if/else on MessageAuthor rather than switch/case: Kotlin enums bridged
    // via Kotlin/Native's Obj-C export aren't necessarily `@frozen` Swift enums, and
    // exhaustiveness-checking their exact bridged shape isn't reliable without a real
    // build (see M2 debugging notes) - direct equality checks sidestep that entirely.
    @ViewBuilder
    private func messageView(for message: ChatMessage) -> some View {
        if message.author == .user {
            UserBubbleView(message: message, onRetry: onRetryFailedMessage)
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

/// iOS 17+ native bottom pinning (`.defaultScrollAnchor(.bottom)`) - the scroll view keeps
/// its bottom edge stable as content grows, so a streaming reply stays glued to the bottom
/// without any isAtBottom-dependent manual scroll (which flashed the jump button and stalled
/// mid-message). Opens at the bottom too. No-op on iOS 16 (the manual `onChange` fallback).
private struct BottomAnchoredScroll: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 17.0, *) {
            content.defaultScrollAnchor(.bottom)
        } else {
            content
        }
    }
}

/// iOS 18+ bottom detection via `onScrollGeometryChange`. Reports the live distance from the
/// bottom (contentSize.height - (contentOffset.y + containerSize.height)) and folds it through
/// the follow/showButton hysteresis band. On iOS 16/17 it's a no-op; the PreferenceKey
/// fallback in MessageListView handles those.
private struct ScrollBottomTracker: ViewModifier {
    @Binding var isAtBottom: Bool

    func body(content: Content) -> some View {
        if #available(iOS 18.0, *) {
            content.onScrollGeometryChange(for: CGFloat.self) { geo in
                geo.contentSize.height - (geo.contentOffset.y + geo.containerSize.height)
            } action: { _, distance in
                if distance <= followThreshold { isAtBottom = true }
                else if distance >= showButtonThreshold { isAtBottom = false }
                // hysteresis dead zone: leave isAtBottom unchanged
            }
        } else {
            content
        }
    }
}
