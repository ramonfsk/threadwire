import SwiftUI

/// Design doc §14.2 hard rule: AI and human must always be unambiguously distinguishable
/// - never by color alone. An explicit text label on every non-user message satisfies
/// that rule (and the EU AI Act transparency point it cites) regardless of any theme/
/// color choice.
struct AuthorLabel: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.caption)
            .fontWeight(.medium)
            .foregroundColor(.secondary)
    }
}
