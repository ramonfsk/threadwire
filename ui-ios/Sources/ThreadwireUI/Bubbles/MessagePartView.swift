import SwiftUI
import ThreadwireCore
import SwiftStreamingMarkdown

/// Renders a single `MessagePart`. Non-text parts get a minimal, visually inert
/// placeholder - real card rendering is the M-Cards milestone's job (a separate
/// library), not this one.
///
/// [textColor] defaults to the design tokens' body text color, but the user bubble
/// (accent-filled, per M2.6) passes white explicitly. Applied via `.foregroundColor`
/// on `StreamedMarkdownView` - not verified against a real build that the library's
/// internal `Text` views actually inherit it (flagging per this project's established
/// pattern for unverified Kotlin/Swift-adjacent assumptions).
struct MessagePartView: View {
    let part: MessagePart
    var textColor: Color = .primary

    var body: some View {
        if let text = part as? MessagePartText {
            TextPartView(part: text, textColor: textColor)
        } else if part is MessagePartToolCall {
            InertPlaceholderChip(label: "Tool call")
        } else if part is MessagePartCard {
            InertPlaceholderChip(label: "Card")
        } else if let custom = part as? MessagePartCustom {
            InertPlaceholderChip(label: custom.type)
        } else {
            InertPlaceholderChip(label: "Unsupported")
        }
    }
}

private struct TextPartView: View {
    let part: MessagePartText
    let textColor: Color

    var body: some View {
        // MarkdownView (driven by `.task(id: text)`), not StreamedMarkdownView. `:core`
        // already emits a full text snapshot on every update, so re-parsing the current
        // snapshot renders progressively as the reply streams - the same snapshot-by-
        // snapshot look, since the design's config doesn't animate per-character anyway.
        // The reason for the switch is correctness under LazyVStack recycling: scrolling a
        // streaming bubble off-screen ran StreamedMarkdownController's `.onDisappear`,
        // cancelling its consuming task, and its single-shot AsyncStream cannot be
        // re-iterated when `.task` restarts on reappear - so the text froze mid-stream
        // ("ficou incompleta"). MarkdownView holds no stream: it just re-parses whatever
        // `part.text` currently is, every time it appears or grows. Font/color come from
        // the design-aligned config (not the library's 17pt default).
        MarkdownView(text: part.text, config: threadwireMarkdownConfig(textColor: textColor))
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
