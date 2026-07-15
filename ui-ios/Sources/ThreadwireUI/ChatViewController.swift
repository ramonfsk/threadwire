import SwiftUI
import UIKit
import ThreadwireCore

/// UIKit interop wrapper around `ChatView` (design doc §13.1) - a thin
/// `UIHostingController` wrapper, for apps that haven't migrated to SwiftUI.
///
/// NOTE: safe-area behavior, keyboard avoidance, and navigation-stack integration
/// (`UINavigationController`) need practical validation in a real host app - this is
/// the maintainer's job per §13.1's explicit "test this early" instruction and the M2
/// plan's verification section, not something this wrapper's code alone guarantees.
public final class ChatViewController: UIViewController {
    private let session: ChatSession

    public init(session: ChatSession) {
        self.session = session
        super.init(nibName: nil, bundle: nil)
    }

    public convenience init(config: ChatConfig, sessionId: String) {
        self.init(session: ChatSession.companion.create(config: config, sessionId: sessionId))
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported - use init(session:) or init(config:sessionId:)")
    }

    public override func viewDidLoad() {
        super.viewDidLoad()

        let hosting = UIHostingController(rootView: ChatView(session: session))
        addChild(hosting)
        view.addSubview(hosting.view)
        hosting.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            hosting.view.topAnchor.constraint(equalTo: view.topAnchor),
            hosting.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            hosting.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            hosting.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
        ])
        hosting.didMove(toParent: self)
    }
}
