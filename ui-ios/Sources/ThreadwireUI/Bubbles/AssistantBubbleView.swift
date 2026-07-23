import SwiftUI
import ThreadwireCore

/// Left-aligned, neutral surface color, minimal author label - see AuthorLabel's KDoc (§14.2).
struct AssistantBubbleView: View {
    let message: ChatMessage

    @Environment(\.threadwireColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            AuthorLabel(text: "AI")
            // Hugs its content, caps at ~80%. No `.frame(maxWidth:)` on the bubble - that
            // *fills* to the max (see UserBubbleView). The markdown paragraph reports its
            // natural width via ParagraphView.sizeThatFits (returns the fitted text width, not
            // the proposed width), so it hugs a short reply and only wraps once it hits the
            // 80% the trailing Spacer's minLength leaves it.
            HStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(Array(message.parts.enumerated()), id: \.offset) { _, part in
                        MessagePartView(part: part, textColor: colors.text)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(colors.bubbleAssistant)
                .clipShape(threadwireAssistantBubbleShape)
                .overlay(threadwireAssistantBubbleShape.stroke(colors.border, lineWidth: 1))
                Spacer(minLength: UIScreen.main.bounds.width * 0.2)
            }
            MessageReactionRow(message: message)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("AI said: \(message.accessibleSummary())")
    }
}

/// Copy is real (`UIPasteboard`, no `:core` involvement). Regenerate/thumbs are
/// visually complete but inert - regenerating a completed AI turn and recording
/// feedback both need real `:core` capability that doesn't exist yet (regenerate
/// would need a distinct "replace last AI reply" operation, not `retryLastFailedTurn`;
/// feedback belongs to M5's `ChatTelemetrySink`, not built yet) - flagged rather than
/// silently faked.
private struct MessageReactionRow: View {
    let message: ChatMessage

    @Environment(\.threadwireColors) private var colors

    var body: some View {
        HStack(spacing: 4) {
            ReactionButton(icon: .copy, label: "Copy message") {
                UIPasteboard.general.string = message.accessibleSummary()
            }
            ReactionButton(icon: .regenerate, label: "Regenerate response") {}
            ReactionButton(icon: .thumbUp, label: "Good response") {}
            // Thumbs-down is the same glyph rotated 180 (the design's own relationship).
            ReactionButton(icon: .thumbUp, label: "Bad response", rotate: 180) {}
            // Send time sits right next to the thumbs pair (not pushed to the far edge).
            MessageTimestamp(millis: message.timestampMillis)
                .padding(.leading, 2)
        }
        .padding(.top, 2)
    }

    // actionBtn: 26px round, icon ~15, tint textTertiary (design spec).
    @ViewBuilder
    private func ReactionButton(icon: ThreadwireIcon, label: String, rotate: Double = 0, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ThreadwireIconView(icon: icon, color: colors.textTertiary, size: 16)
                .rotationEffect(.degrees(rotate))
                .frame(width: 26, height: 26)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}
