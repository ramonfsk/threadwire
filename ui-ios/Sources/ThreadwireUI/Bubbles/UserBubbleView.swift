import SwiftUI
import ThreadwireCore

/// Right-aligned, accent-colored - the accepted chat-UI convention for the local user.
struct UserBubbleView: View {
    let message: ChatMessage

    var body: some View {
        HStack {
            Spacer(minLength: 40)
            VStack(alignment: .leading, spacing: 4) {
                ForEach(Array(message.parts.enumerated()), id: \.offset) { _, part in
                    MessagePartView(part: part)
                }
            }
            .padding(12)
            .background(Color.accentColor.opacity(0.15))
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("You said: \(message.accessibleSummary())")
    }
}
