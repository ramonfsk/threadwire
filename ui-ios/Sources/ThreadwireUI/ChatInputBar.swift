import SwiftUI

/// Text-only input (attachment/mic buttons are M3 scope, not built here). [isAwaitingResponse]
/// swaps the trailing button between send and stop, per design doc §14.1's "send/stop"
/// input-bar requirement.
struct ChatInputBar: View {
    @Binding var text: String
    let isAwaitingResponse: Bool
    let onSend: () -> Void
    let onStop: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            TextField("Message", text: $text)
                .textFieldStyle(.roundedBorder)
                .disabled(isAwaitingResponse)
                .accessibilityLabel("Message input")
            if isAwaitingResponse {
                Button("Stop", action: onStop)
                    .frame(minWidth: 44, minHeight: 44)
                    .accessibilityLabel("Stop generating")
            } else {
                Button("Send", action: onSend)
                    .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    .frame(minWidth: 44, minHeight: 44)
                    .accessibilityLabel("Send message")
            }
        }
        .padding(8)
    }
}
