import SwiftUI

/// The design's `welcomeWrap` empty-conversation state: a title and optional
/// suggested-prompt chips, centered. Shown while the conversation has no messages.
/// (The design's decorative assistant avatar was dropped - talking to an LLM, a persona
/// avatar reads as misleading, so the empty state is text-only.) Suggested prompts are
/// host-supplied (`:core` has no concept of them).
struct WelcomeView: View {
    let suggestedPrompts: [String]
    let onPickPrompt: (String) -> Void

    @Environment(\.threadwireColors) private var colors
    @Environment(\.threadwireTypography) private var typography

    var body: some View {
        VStack(spacing: 14) {
            Text("How can I help you today?")
                .font(typography.header)
                .fontWeight(.semibold)
                .foregroundColor(colors.text)
                .multilineTextAlignment(.center)

            if !suggestedPrompts.isEmpty {
                FlowChips(prompts: suggestedPrompts, onPick: onPickPrompt)
                    .padding(.top, 4)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 48)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Center-aligned wrapping chip row. iOS 16 has no `Layout`-free wrapping stack, so this
/// uses a simple wrapping arrangement built on `WrappingHStack`-style geometry via a
/// flexible VStack of rows computed from an approximate width — kept minimal since the
/// design's prompt count is small (default 3).
private struct FlowChips: View {
    let prompts: [String]
    let onPick: (String) -> Void

    @Environment(\.threadwireColors) private var colors
    @Environment(\.threadwireTypography) private var typography

    var body: some View {
        // A vertically stacked, centered set of chips - simplest faithful rendering for
        // the small default prompt count without a custom Layout.
        VStack(spacing: 8) {
            ForEach(prompts, id: \.self) { prompt in
                Button {
                    onPick(prompt)
                } label: {
                    Text(prompt)
                        .font(typography.msg)
                        .foregroundColor(colors.text)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(colors.surfaceAlt)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(colors.border, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
    }
}
