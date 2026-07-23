import SwiftUI

/// Design System Adoption milestone - the source of truth every retokenized view reads
/// from via `@Environment`, SwiftUI's actual idiom for ambient/themeable values (no
/// CompositionLocal equivalent exists). Set once at `ChatView`'s root.
private struct ThreadwireColorsKey: EnvironmentKey {
    static let defaultValue = ThreadwireColors.resolve(dark: false)
}

private struct ThreadwireTypographyKey: EnvironmentKey {
    static let defaultValue = threadwireTypography(.medium)
}

extension EnvironmentValues {
    var threadwireColors: ThreadwireColors {
        get { self[ThreadwireColorsKey.self] }
        set { self[ThreadwireColorsKey.self] = newValue }
    }
    var threadwireTypography: ThreadwireTypography {
        get { self[ThreadwireTypographyKey.self] }
        set { self[ThreadwireTypographyKey.self] = newValue }
    }
}

extension View {
    /// Applies the design tokens for the given appearance - call once at the chat root.
    public func threadwireTheme(dark: Bool, fontScale: ThreadwireFontScale = .medium) -> some View {
        environment(\.threadwireColors, ThreadwireColors.resolve(dark: dark))
            .environment(\.threadwireTypography, threadwireTypography(fontScale))
    }
}
