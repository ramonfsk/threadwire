import SwiftUI
import ThreadwireCore
import ThreadwireUI

struct ContentView: View {
    var body: some View {
        ChatView(config: sampleConfig, sessionId: "sample-session")
    }
}

/// Illustrative config only, not a real BFF integration - the iOS simulator can reach
/// the host machine directly via `localhost`, matching `tools/fake-sse-server` (M0)
/// listening on port 8080 there. A real integrator supplies their own baseUrl/auth.
private final class SampleContextProvider: ChatContextProvider {
    func headers(request: ChatRequest) async throws -> [String: String] { [:] }
    func contextPayload(request: ChatRequest) async throws -> [String: Any] { [:] }
}

private let sampleConfig = ChatConfig(
    baseUrl: "http://localhost:8080/chat",
    contextProvider: SampleContextProvider()
)

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
