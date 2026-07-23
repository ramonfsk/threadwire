import SwiftUI
import ThreadwireCore

/// Renders a `MessageAuthor.system` message - not produced by any code path yet (no
/// `:core` logic synthesizes one today), but handled here so author dispatch stays
/// exhaustive and forward-compatible.
struct SystemBannerView: View {
    let message: ChatMessage

    @Environment(\.threadwireColors) private var colors
    @Environment(\.threadwireTypography) private var typography

    var body: some View {
        Text(message.accessibleSummary())
            .font(typography.meta)
            .foregroundColor(colors.textSecondary)
            .frame(maxWidth: .infinity)
            .multilineTextAlignment(.center)
            .padding(.vertical, 8)
            .accessibilityLabel("System message: \(message.accessibleSummary())")
    }
}
