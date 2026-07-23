import SwiftUI
import ThreadwireCore

/// Design doc §14.1: distinguishes "thinking" (no visible output yet) from "generating"
/// (streaming in).
enum StreamingPhase {
    case thinking
    case generating
}

extension ChatState {
    func streamingPhase() -> StreamingPhase? {
        guard isAwaitingResponse else { return nil }
        guard let last = messages.last else { return .thinking }
        let hasVisibleContent = last.author != .user && !last.parts.isEmpty
        return hasVisibleContent ? .generating : .thinking
    }
}

/// The design's `typingDots`: a label followed by three staggered pulsing dots (6px,
/// textTertiary), replacing the earlier static "Thinking…" text.
struct StreamingIndicatorView: View {
    let phase: StreamingPhase

    @Environment(\.threadwireColors) private var colors
    @Environment(\.threadwireTypography) private var typography

    private var label: String {
        switch phase {
        case .thinking: return "Thinking"
        case .generating: return "Generating"
        }
    }

    var body: some View {
        HStack(spacing: 4) {
            Text(label)
                .font(typography.meta)
                .foregroundColor(colors.textSecondary)
                .padding(.trailing, 3)
            TypingDot(delay: 0)
            TypingDot(delay: 0.15)
            TypingDot(delay: 0.3)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 4)
        .accessibilityElement()
        .accessibilityLabel("\(label)…")
        .accessibilityAddTraits(.updatesFrequently)
    }
}

private struct TypingDot: View {
    let delay: Double
    @State private var pulsing = false
    @Environment(\.threadwireColors) private var colors

    var body: some View {
        Circle()
            .fill(colors.textTertiary)
            .frame(width: 6, height: 6)
            .opacity(pulsing ? 1 : 0.3)
            .animation(.easeInOut(duration: 0.55).repeatForever(autoreverses: true).delay(delay), value: pulsing)
            .onAppear { pulsing = true }
    }
}
