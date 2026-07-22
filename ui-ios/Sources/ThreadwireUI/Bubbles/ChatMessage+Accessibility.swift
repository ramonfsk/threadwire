import ThreadwireCore

extension ChatMessage {
    /// Plain-text summary for accessibility labels - falls back to a generic
    /// description for non-text parts.
    func accessibleSummary() -> String {
        let joined = parts.map { part -> String in
            if let text = part as? MessagePartText {
                return text.text
            } else if part is MessagePartToolCall {
                return "tool call"
            } else if part is MessagePartCard {
                return "card"
            } else if let custom = part as? MessagePartCustom {
                return custom.type
            } else {
                return "unsupported content"
            }
        }.joined(separator: " ")
        return joined.trimmingCharacters(in: .whitespaces).isEmpty ? "empty message" : joined
    }
}
