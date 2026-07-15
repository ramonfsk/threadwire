// swift-tools-version:5.9
import PackageDescription

// NOTE: the binaryTarget below points at a real, locally-built .xcframework. It must
// exist on disk before Xcode/SwiftPM can resolve this package - run
// `./gradlew :core:assembleThreadwireCoreXCFramework` (registered by core/build.gradle.kts's
// new XCFramework DSL block) at least once first. The exact "release" segment of this
// path follows Kotlin Multiplatform's standard XCFramework output convention
// (build/XCFrameworks/<debug|release>/<Name>.xcframework) but hasn't been confirmed
// against a real build output yet - verify this path once, adjust if it differs.
let package = Package(
    name: "ThreadwireUI",
    platforms: [
        // Floor set by SwiftStreamingMarkdown's own requirement (iOS 16+), not by
        // ThreadwireCore or UIHostingController (which would allow iOS 13+) - see the
        // M2 plan's Decision C.
        .iOS(.v16),
    ],
    products: [
        .library(name: "ThreadwireUI", targets: ["ThreadwireUI"]),
    ],
    dependencies: [
        .package(url: "https://github.com/microsoft/SwiftStreamingMarkdown", from: "0.1.0"),
    ],
    targets: [
        .binaryTarget(
            name: "ThreadwireCore",
            path: "../core/build/XCFrameworks/release/ThreadwireCore.xcframework"
        ),
        .target(
            name: "ThreadwireUI",
            dependencies: [
                "ThreadwireCore",
                .product(name: "SwiftStreamingMarkdown", package: "SwiftStreamingMarkdown"),
            ]
        ),
    ]
)
