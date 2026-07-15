import SwiftUI
import ThreadwireCore

/// Left-aligned, visually distinct accent from `AssistantBubbleView` - design doc §14.2
/// requires AI/human be unambiguously distinguishable, which the differing "Agent"/"AI"
/// label already satisfies regardless of color choice. [agentName] comes from
/// `SessionPhase.HandoffActive` when available; a generic "Agent" label is used
/// otherwise (e.g. phase has since moved on).
struct HumanAgentBubbleView: View {
    let message: ChatMessage
    let agentName: String?

    private var label: String {
        guard let agentName, !agentName.isEmpty else { return "Agent" }
        return "Agent · \(agentName)"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            AuthorLabel(text: label)
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(Array(message.parts.enumerated()), id: \.offset) { _, part in
                        MessagePartView(part: part)
                    }
                }
                .padding(12)
                .background(Color.orange.opacity(0.15))
                .clipShape(RoundedRectangle(cornerRadius: 16))
                Spacer(minLength: 40)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label) said: \(message.accessibleSummary())")
    }
}
