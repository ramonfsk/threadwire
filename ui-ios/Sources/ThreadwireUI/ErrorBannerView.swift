import SwiftUI
import ThreadwireCore

/// A thin, non-blocking banner for `ChatErrorInfo` (design doc §14.1 item 6), with a
/// retry affordance.
struct ErrorBannerView: View {
    let error: ChatErrorInfo
    let onRetry: () -> Void

    private var message: String {
        error.message ?? "Something went wrong."
    }

    var body: some View {
        HStack {
            Text(message)
                .font(.caption)
                .foregroundColor(.red)
            Spacer()
            Button("Retry", action: onRetry)
                .font(.caption)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(Color.red.opacity(0.1))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Error: \(message)")
    }
}
