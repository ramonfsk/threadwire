import SwiftUI
import ThreadwireCore

/// Renders a `MessageAuthor.system` message - not produced by any code path yet (no
/// `:core` logic synthesizes one today), but handled here so author dispatch stays
/// exhaustive and forward-compatible.
struct SystemBannerView: View {
    let message: ChatMessage

    var body: some View {
        Text(message.accessibleSummary())
            .font(.caption)
            .foregroundColor(.secondary)
            .frame(maxWidth: .infinity)
            .multilineTextAlignment(.center)
            .padding(.vertical, 8)
            .accessibilityLabel("System message: \(message.accessibleSummary())")
    }
}
