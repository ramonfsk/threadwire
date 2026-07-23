import SwiftUI
import SwiftStreamingMarkdown

/// The design's 3-tier font scale - `.medium` is this milestone's hardcoded default
/// (see ThreadwireColors' ChatUIConfig note).
public enum ThreadwireFontScale {
    case small, medium, large
}

/// A Markdown render config aligned to Threadwire's design tokens. `MarkdownRenderConfig`'s
/// `.default` renders body text with the library's bundled 17pt `baseTextFonts` in *its*
/// theme color - both larger than the design's message size (medium `msg` = 15.5) and not
/// our text token, which is why message text looked oversized on iOS versus the design and
/// versus Android. This overrides the paragraph/list body to the design's size + the
/// caller's color (white in the user bubble, `colors.text` in the assistant bubble).
/// Headings/tables/code keep the library defaults on purpose (larger / monospaced).
func threadwireMarkdownConfig(textColor: Color, bodySize: CGFloat = 15.5) -> MarkdownRenderConfig {
    let fonts = TextFonts(
        normal: .systemFont(ofSize: bodySize, weight: .regular),
        italic: .italicSystemFont(ofSize: bodySize),
        bold: .systemFont(ofSize: bodySize, weight: .semibold),
        boldItalic: nil,
        preferredLetterSpacing: nil,
        preferredLineHeight: nil
    )
    let body = MarkdownRenderConfig.MarkdownTextStyle(textFonts: fonts, textColor: textColor)
    return MarkdownRenderConfig(orderedListStyle: body, paragraphStyle: body)
}

public struct ThreadwireTypography {
    public let title, header, msg, meta, name: Font
}

/// Sizes (pt) transcribed verbatim from the design's `FS` table - converted 1:1 from the
/// design's px values, the standard iOS convention.
func threadwireTypography(_ scale: ThreadwireFontScale) -> ThreadwireTypography {
    switch scale {
    case .small:
        return ThreadwireTypography(
            title: .system(size: 20, weight: .bold),
            header: .system(size: 15, weight: .semibold),
            msg: .system(size: 14),
            meta: .system(size: 12),
            name: .system(size: 15, weight: .medium)
        )
    case .medium:
        return ThreadwireTypography(
            title: .system(size: 23, weight: .bold),
            header: .system(size: 16, weight: .semibold),
            msg: .system(size: 15.5),
            meta: .system(size: 12.5),
            name: .system(size: 16, weight: .medium)
        )
    case .large:
        return ThreadwireTypography(
            title: .system(size: 26, weight: .bold),
            header: .system(size: 17, weight: .semibold),
            msg: .system(size: 17),
            meta: .system(size: 13.5),
            name: .system(size: 17, weight: .medium)
        )
    }
}

/// Card / generic container corner radius (design: card wrappers, chips use ~14-16).
let threadwireCardCornerRadius: CGFloat = 16

/// Message-bubble shapes, transcribed from the design's `bubbleUser` (`18 18 4 18`) and
/// `bubbleAssistant` (`18 18 18 4`) — asymmetric "tail" corner. CSS order is
/// TL/TR/BR/BL; `UnevenRoundedRectangle` (iOS 16.0+) takes topLeading/bottomLeading/
/// bottomTrailing/topTrailing.
let threadwireUserBubbleShape = UnevenRoundedRectangle(
    topLeadingRadius: 18, bottomLeadingRadius: 18, bottomTrailingRadius: 4, topTrailingRadius: 18
)
let threadwireAssistantBubbleShape = UnevenRoundedRectangle(
    topLeadingRadius: 18, bottomLeadingRadius: 4, bottomTrailingRadius: 18, topTrailingRadius: 18
)
