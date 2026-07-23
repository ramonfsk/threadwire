import SwiftUI

/// The design's composer (`inputRow`): a single rounded-pill row holding the attach
/// button, the text field, and one trailing control - not three separate elements.
/// [isAwaitingResponse] swaps the trailing control between send/mic and stop. The attach
/// button opens an inert menu (M3/media not started).
struct ChatInputBar: View {
    @Binding var text: String
    let isAwaitingResponse: Bool
    let onSend: () -> Void
    let onStop: () -> Void

    @Environment(\.threadwireColors) private var colors
    @Environment(\.threadwireTypography) private var typography

    private var canSend: Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        HStack(spacing: 6) {
            AttachmentPickerMenu()
                .frame(width: 32, height: 32)

            TextField("Message", text: $text, axis: .vertical)
                .font(typography.msg)
                .foregroundColor(colors.text)
                .tint(colors.accent)
                .disabled(isAwaitingResponse)
                .lineLimit(1...5)
                .accessibilityLabel("Message input")

            trailingControl
        }
        .padding(6)
        .frame(minHeight: 44)
        .background(colors.inputBg)
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .overlay(RoundedRectangle(cornerRadius: 22).stroke(colors.border, lineWidth: 1))
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(colors.headerBg)
    }

    @ViewBuilder
    private var trailingControl: some View {
        if isAwaitingResponse {
            Button(action: onStop) {
                ThreadwireIconView(icon: .stop, color: .white, size: 14)
                    .frame(width: 32, height: 32)
                    .background(colors.destructive)
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Stop generating")
        } else if canSend {
            Button(action: onSend) {
                ThreadwireIconView(icon: .send, color: .white, size: 16)
                    .frame(width: 32, height: 32)
                    .background(colors.accent)
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Send message")
        } else {
            ThreadwireIconView(icon: .mic, color: colors.textSecondary, size: 20)
                .frame(width: 32, height: 32)
                .accessibilityLabel("Voice input")
        }
    }
}
