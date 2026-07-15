import SwiftUI
import ThreadwireCore
import SwiftStreamingMarkdown

/// Renders a single `MessagePart`. Non-text parts get a minimal, visually inert
/// placeholder - real card/tool-call UI is M4/M3 scope, not this milestone.
struct MessagePartView: View {
    let part: MessagePart

    var body: some View {
        if let text = part as? MessagePart.Text {
            TextPartView(part: text)
        } else if part is MessagePart.ToolCall {
            InertPlaceholderChip(label: "Tool call")
        } else if part is MessagePart.Card {
            InertPlaceholderChip(label: "Card")
        } else if let custom = part as? MessagePart.Custom {
            InertPlaceholderChip(label: custom.type)
        } else {
            InertPlaceholderChip(label: "Unsupported")
        }
    }
}

/// Bridges one `MessagePart.Text`'s accumulated string (already a full snapshot on
/// every update, per `:core`'s reducer - never just a delta) into `SwiftStreamingMarkdown`'s
/// `AsyncStream`-based streaming source.
private final class TextPartMarkdownSource: ObservableObject, StreamedMarkdownSource {
    let text: AsyncStream<String>
    private let continuation: AsyncStream<String>.Continuation

    init() {
        var continuationRef: AsyncStream<String>.Continuation!
        self.text = AsyncStream { continuation in
            continuationRef = continuation
        }
        self.continuation = continuationRef
    }

    func update(_ newText: String) {
        continuation.yield(newText)
    }
}

private struct TextPartView: View {
    let part: MessagePart.Text
    @StateObject private var source = TextPartMarkdownSource()

    var body: some View {
        StreamedMarkdownView(source: source)
            .onAppear { source.update(part.text) }
            .onChange(of: part.text) { newValue in
                source.update(newValue)
            }
    }
}

struct InertPlaceholderChip: View {
    let label: String

    var body: some View {
        Text(label)
            .font(.caption)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .accessibilityLabel("Unsupported content: \(label)")
    }
}
