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
        // Pinned to a branch, not a semver range (`from:`) - SwiftStreamingMarkdown
        // itself depends on a fork of `highlightswift` via a commit `revision:`, which
        // SPM treats as an "unstable version." Requiring SwiftStreamingMarkdown via a
        // semver range forces "stable version" resolution rules onto that transitive
        // dependency too, which fails ("required using a stable-version but depends on
        // an unstable-version package"). branch-based resolution doesn't impose that
        // constraint. Confirmed against a real Xcode package-resolution error.
        .package(url: "https://github.com/microsoft/SwiftStreamingMarkdown", branch: "main"),
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
