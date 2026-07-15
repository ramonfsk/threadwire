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

struct StreamingIndicatorView: View {
    let phase: StreamingPhase

    private var label: String {
        switch phase {
        case .thinking: return "Thinking…"
        case .generating: return "Generating…"
        }
    }

    var body: some View {
        Text(label)
            .font(.caption)
            .foregroundColor(.secondary)
            .padding(.horizontal, 16)
            .padding(.vertical, 4)
            .accessibilityLabel(label)
            .accessibilityAddTraits(.updatesFrequently)
    }
}
