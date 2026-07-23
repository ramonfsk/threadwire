import SwiftUI
import ThreadwireCore

/// The design's slim send-time line below a bubble (M2.6): meta type, tertiary color.
/// Renders nothing for an unknown time (`ChatMessage.timestampMillis == 0`, e.g. history
/// messages with no server-provided time yet). Locale-aware short time via `DateFormatter`.
struct MessageTimestamp: View {
    let millis: Int64

    @Environment(\.threadwireColors) private var colors
    @Environment(\.threadwireTypography) private var typography

    var body: some View {
        if millis > 0 {
            Text(Self.formatter.string(from: Date(timeIntervalSince1970: Double(millis) / 1000)))
                .font(typography.meta)
                .foregroundColor(colors.textTertiary)
        }
    }

    private static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .none
        f.timeStyle = .short
        return f
    }()
}
