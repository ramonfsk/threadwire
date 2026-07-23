import SwiftUI

/// Paperclip menu - "Photo"/"File" chrome only, both inert (M3/media hasn't started, so
/// there's nothing real to wire yet - matches this milestone's "visually complete,
/// functionally inert" treatment for out-of-scope capability, same as the mic button).
/// A `Menu` rather than `.popover` - guaranteed iOS 16.0-compatible without needing
/// `presentationCompactAdaptation` (iOS 16.4+, above this project's floor).
struct AttachmentPickerMenu: View {
    @Environment(\.threadwireColors) private var colors

    var body: some View {
        Menu {
            Button(action: {}) { Label("Photo", systemImage: "photo") }
            Button(action: {}) { Label("File", systemImage: "doc") }
        } label: {
            ThreadwireIconView(icon: .attach, color: colors.textSecondary, size: 20)
        }
        .accessibilityLabel("Add attachment")
    }
}
