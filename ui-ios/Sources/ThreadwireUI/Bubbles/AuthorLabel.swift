import SwiftUI

/// Design doc §14.2 hard rule: AI and human must always be unambiguously distinguishable
/// - never by color alone. The commissioned design (M2.6) has no author labels at all;
/// resolution confirmed with the maintainer: user bubbles stay unlabeled (matches the
/// design), but this label is kept - at a deliberately restrained meta-scale size - for
/// every non-user message, preserving the accessibility/compliance guarantee the design
/// itself doesn't address.
struct AuthorLabel: View {
    let text: String

    @Environment(\.threadwireColors) private var colors
    @Environment(\.threadwireTypography) private var typography

    var body: some View {
        Text(text)
            .font(typography.meta)
            .foregroundColor(colors.textTertiary)
    }
}
