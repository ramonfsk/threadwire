import SwiftUI
import ThreadwireCore

/// A thin banner reflecting `SessionPhase` directly (not per-message) - design doc
/// §14.1 item 5 "Handoff transition," kept basic: a system banner is in scope for M2,
/// an agent avatar and differentiated typing indicator are finer M6 polish once real
/// handoff transport exists.
struct PhaseBannerView: View {
    let phase: SessionPhase

    @Environment(\.threadwireColors) private var colors
    @Environment(\.threadwireTypography) private var typography

    private var text: String? {
        if phase is SessionPhaseAiActive {
            return nil
        } else if phase is SessionPhaseHandoffPending {
            return "You're being transferred to a human agent…"
        } else if let active = phase as? SessionPhaseHandoffActive {
            return active.agentName.isEmpty ? "Connected with a human agent" : "Connected with \(active.agentName)"
        }
        return nil
    }

    var body: some View {
        Group {
            if let text {
                Text(text)
                    .font(typography.meta)
                    .foregroundColor(colors.textSecondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(8)
                    .background(colors.surfaceAlt)
                    .accessibilityLabel(text)
                    .transition(.opacity)
            }
        }
        // Keyed off `text` (plain String?, trivially Equatable), not `phase` directly -
        // SessionPhase is a Kotlin/Native-bridged type with no guaranteed Equatable
        // conformance for SwiftUI's animation(value:).
        .animation(.easeInOut, value: text)
    }
}
