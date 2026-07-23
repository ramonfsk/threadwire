import SwiftUI

/// chatHeader: menu (left), search+close (right), and the title ABSOLUTELY centered so it
/// stays visually centered regardless of the differing left/right button widths (the
/// design centers it independently, not with a layout weight that shifts it off-center).
struct ChatHeaderBar: View {
    let title: String
    let onMenuClick: () -> Void
    let onSearchClick: () -> Void
    let onCloseClick: () -> Void

    @Environment(\.threadwireColors) private var colors
    @Environment(\.threadwireTypography) private var typography

    var body: some View {
        ZStack {
            HStack(spacing: 0) {
                Button(action: onMenuClick) {
                    ThreadwireIconView(icon: .menu, color: colors.text, size: 22)
                }
                .frame(width: 40, height: 40)
                .accessibilityLabel("Open chat list")

                Spacer()

                Button(action: onSearchClick) {
                    ThreadwireIconView(icon: .search, color: colors.text, size: 21)
                }
                .frame(width: 40, height: 40)
                .accessibilityLabel("Search in chat")

                Button(action: onCloseClick) {
                    ThreadwireIconView(icon: .close, color: colors.text, size: 20)
                }
                .frame(width: 40, height: 40)
                .accessibilityLabel("Close chat")
            }

            Text(title)
                .font(typography.header)
                .foregroundColor(colors.text)
                .lineLimit(1)
                .padding(.horizontal, 90)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(colors.headerBg)
    }
}
