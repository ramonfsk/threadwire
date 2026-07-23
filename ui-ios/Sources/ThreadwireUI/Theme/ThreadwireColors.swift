import SwiftUI

/// Design System Adoption milestone - values transcribed verbatim from the commissioned
/// design (extracted from its standalone HTML export's `getColors(dark)` function).
/// SwiftUI has no built-in themeable token system, so this is a plain struct rather
/// than an environment-provided design-system type (that role is `ThreadwireTheme`).
public struct ThreadwireColors {
    public let bg, headerBg, surface, surfaceAlt: Color
    public let text, textSecondary, textTertiary: Color
    public let border, bubbleAssistant, inputBg, code: Color
    public let accent: Color
    public let destructive: Color

    /// Manual-sync risk accepted (see M2.6 plan): hand-transcribed from the design, not codegen'd.
    public static func resolve(dark: Bool) -> ThreadwireColors {
        dark ? darkColors : lightColors
    }
}

/// Default accent (user bubble fill) - not theme-dependent, matches the design's default
/// `accentColor` prop. Design doc §12's full `ChatUIConfig` (remote theming, alternate
/// accents) isn't built yet - this milestone hardcodes the default rather than guessing
/// an unspec'd config API shape.
let threadwireAccent = Color(hex: 0x3B6EA5)

/// Same in light/dark - used for destructive confirm-card actions and the failed-send row.
let threadwireDestructive = Color(hex: 0xC24545)

private let lightColors = ThreadwireColors(
    bg: Color(hex: 0xF7F4EF),
    headerBg: Color(hex: 0xF7F4EF, alpha: 0.92),
    surface: Color(hex: 0xFFFFFF),
    surfaceAlt: Color(hex: 0xEFEAE1),
    text: Color(hex: 0x211B14),
    textSecondary: Color(hex: 0x211B14, alpha: 0.62),
    textTertiary: Color(hex: 0x211B14, alpha: 0.36),
    border: Color(hex: 0x211B14, alpha: 0.08),
    bubbleAssistant: Color(hex: 0xFFFFFF),
    inputBg: Color(hex: 0xFFFFFF),
    code: Color(hex: 0xF1ECE2),
    accent: threadwireAccent,
    destructive: threadwireDestructive
)

private let darkColors = ThreadwireColors(
    bg: Color(hex: 0x18130F),
    headerBg: Color(hex: 0x18130F, alpha: 0.92),
    surface: Color(hex: 0x241D17),
    surfaceAlt: Color(hex: 0x2A2219),
    text: Color(hex: 0xF3EDE4),
    textSecondary: Color(hex: 0xF3EDE4, alpha: 0.62),
    textTertiary: Color(hex: 0xF3EDE4, alpha: 0.36),
    border: Color(hex: 0xF3EDE4, alpha: 0.1),
    bubbleAssistant: Color(hex: 0x241D17),
    inputBg: Color(hex: 0x241D17),
    code: Color(hex: 0x2E2418),
    accent: threadwireAccent,
    destructive: threadwireDestructive
)

extension Color {
    /// Not public - internal helper solely for transcribing the design's hex tokens above.
    init(hex: UInt32, alpha: Double = 1) {
        let r = Double((hex >> 16) & 0xFF) / 255
        let g = Double((hex >> 8) & 0xFF) / 255
        let b = Double(hex & 0xFF) / 255
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }
}
