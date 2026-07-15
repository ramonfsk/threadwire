import SwiftUI
import ThreadwireCore

/// A thin banner reflecting `SessionPhase` directly (not per-message) - design doc
/// §14.1 item 5 "Handoff transition," kept basic: a system banner is in scope for M2,
/// an agent avatar and differentiated typing indicator are finer M6 polish once real
/// handoff transport exists.
struct PhaseBannerView: View {
    let phase: SessionPhase

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
        if let text {
            Text(text)
                .font(.caption)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(8)
                .background(Color.yellow.opacity(0.2))
                .accessibilityLabel(text)
        }
    }
}
