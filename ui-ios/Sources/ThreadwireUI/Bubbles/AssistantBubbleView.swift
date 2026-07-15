import SwiftUI
import ThreadwireCore

/// Left-aligned, neutral surface color, explicit "AI" label - design doc §14.2.
struct AssistantBubbleView: View {
    let message: ChatMessage

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            AuthorLabel(text: "AI")
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(Array(message.parts.enumerated()), id: \.offset) { _, part in
                        MessagePartView(part: part)
                    }
                }
                .padding(12)
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 16))
                Spacer(minLength: 40)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("AI said: \(message.accessibleSummary())")
    }
}
