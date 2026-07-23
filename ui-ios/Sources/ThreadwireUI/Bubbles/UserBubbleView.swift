import SwiftUI
import ThreadwireCore

/// Right-aligned, accent-filled, no author label (matches the design - see `AuthorLabel`'s
/// doc for why non-user messages keep one and this doesn't). Shape/padding transcribed
/// from the design's `bubbleUser` (asymmetric radius, 10x14 padding).
/// [onRetry] is only ever invoked when `message.deliveryFailed` is true.
struct UserBubbleView: View {
    let message: ChatMessage
    var onRetry: () -> Void = {}

    @Environment(\.threadwireColors) private var colors
    @Environment(\.threadwireTypography) private var typography

    var body: some View {
        VStack(alignment: .trailing, spacing: 3) {
            // The bubble hugs its content and caps at ~80%. Deliberately NO `.frame(maxWidth:)`
            // on the bubble: in SwiftUI that *fills* to the max (it's how `.frame(maxWidth:
            // .infinity)` makes a view greedy), which stretched even a one-word message to the
            // full 80%. Instead the width cap comes from the leading Spacer's minLength (20% of
            // the screen), and plain SwiftUI Text hugs its own content, wrapping only once it
            // runs out of the remaining 80%.
            HStack(spacing: 0) {
                Spacer(minLength: UIScreen.main.bounds.width * 0.2)
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(Array(message.parts.enumerated()), id: \.offset) { _, part in
                        // User text is plain Text (not the greedy markdown renderer, which
                        // would fill the whole 80%). Non-text parts (rare from a user) keep the
                        // shared renderer.
                        if let text = part as? MessagePartText {
                            Text(text.text)
                                .font(typography.msg)
                                .foregroundColor(.white)
                        } else {
                            MessagePartView(part: part, textColor: .white)
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(colors.accent)
                .clipShape(threadwireUserBubbleShape)
            }
            MessageTimestamp(millis: message.timestampMillis)
            if message.deliveryFailed {
                HStack(spacing: 6) {
                    Text("Failed to send")
                        .font(typography.meta)
                        .foregroundColor(colors.destructive)
                    // retryIconBtn: 20px circle, tinted-red background (design spec).
                    Button(action: onRetry) {
                        ThreadwireIconView(icon: .regenerate, color: colors.destructive, size: 12)
                            .frame(width: 20, height: 20)
                            .background(colors.destructive.opacity(0.12))
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Retry sending message")
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("You said: \(message.accessibleSummary())")
    }
}
