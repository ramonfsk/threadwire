import SwiftUI
import ThreadwireCore

/// Left-aligned, like `AssistantBubbleView` but on `surfaceAlt` (a subtly distinct
/// neutral shade already in the design tokens - the commissioned design doesn't call
/// out a dedicated human-agent color, so this reuses an existing token rather than
/// inventing one) - the differing "Agent"/"AI" label is still what §14.2 actually
/// requires. [agentName] comes from `SessionPhaseHandoffActive` when available; a
/// generic "Agent" label is used otherwise (e.g. phase has since moved on).
struct HumanAgentBubbleView: View {
    let message: ChatMessage
    let agentName: String?

    @Environment(\.threadwireColors) private var colors

    private var label: String {
        guard let agentName, !agentName.isEmpty else { return "Agent" }
        return "Agent · \(agentName)"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            AuthorLabel(text: label)
            // Hugs its content, caps at ~80% via the trailing Spacer - no `.frame(maxWidth:)`
            // (which fills). See AssistantBubbleView / UserBubbleView for the reasoning.
            HStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(Array(message.parts.enumerated()), id: \.offset) { _, part in
                        MessagePartView(part: part, textColor: colors.text)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(colors.surfaceAlt)
                .clipShape(threadwireAssistantBubbleShape)
                .overlay(threadwireAssistantBubbleShape.stroke(colors.border, lineWidth: 1))
                Spacer(minLength: UIScreen.main.bounds.width * 0.2)
            }
            MessageTimestamp(millis: message.timestampMillis)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label) said: \(message.accessibleSummary())")
    }
}
