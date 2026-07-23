import SwiftUI

/// Chat-surface line icons (M2.6) built from the commissioned design's *own* SVG path data -
/// the same data Android uses in `ThreadwireIcons.kt` - so both platforms render identical
/// glyphs. The design's paths (arcs, relative commands, packed flags) were pre-flattened,
/// once, to absolute move/line/cubic commands, so iOS needs only the trivial parser below
/// rather than a full SVG engine (rendering raw SVG on iOS/UIKit is exactly the non-trivial
/// task we're avoiding). All icons are authored in a 24x24 space; `ThreadwireIconView` scales
/// that to the requested size and paints strokes and fills in the caller's colour.
enum ThreadwireIcon {
    case menu, search, close, attach, mic, send, stop, copy, regenerate, thumbUp, arrowDown

    /// (strokeWidth, pathData) elements; a strokeWidth of 0 means a filled path.
    fileprivate var elements: [(width: CGFloat, d: String)] {
        switch self {
        case .menu:
            return [(2, "M 4 6 L 20 6 M 4 12 L 20 12 M 4 18 L 20 18")]
        case .search:
            return [(1.9, "M 4 11 C 4 14.87 7.13 18 11 18 C 14.87 18 18 14.87 18 11 C 18 7.13 14.87 4 11 4 C 7.13 4 4 7.13 4 11"),
                    (1.9, "M 16 16 L 21 21")]
        case .close:
            return [(2.2, "M 5 5 L 19 19 M 19 5 L 5 19")]
        case .attach:
            return [(1.7, "M 21 11.5 L 12 20.5 C 10.01 21.99 7.22 21.79 5.46 20.04 C 3.71 18.28 3.51 15.49 5 13.5 L 14 4.5 C 15.38 3.12 17.62 3.12 19 4.5 C 20.38 5.88 20.38 8.12 19 9.5 L 11 17.5 C 10.17 18.33 8.83 18.33 8 17.5 C 7.17 16.67 7.17 15.33 8 14.5 L 15 7.5")]
        case .mic:
            return [(1.7, "M 12 2 L 12 2 C 13.66 2 15 3.34 15 5 L 15 11 C 15 12.66 13.66 14 12 14 L 12 14 C 10.34 14 9 12.66 9 11 L 9 5 C 9 3.34 10.34 2 12 2"),
                    (1.7, "M 5 11 C 5 14.87 8.13 18 12 18 C 15.87 18 19 14.87 19 11 M 12 18 L 12 21")]
        case .send:
            return [(0, "M 4 12 L 20 4 L 14 20 L 11.5 13.5 L 4 12 Z")]
        case .stop:
            return [(0, "M 8 7 L 16 7 C 16.55 7 17 7.45 17 8 L 17 16 C 17 16.55 16.55 17 16 17 L 8 17 C 7.45 17 7 16.55 7 16 L 7 8 C 7 7.45 7.45 7 8 7 Z")]
        case .copy:
            return [(1.7, "M 11 9 L 19 9 C 20.1 9 21 9.9 21 11 L 21 19 C 21 20.1 20.1 21 19 21 L 11 21 C 9.9 21 9 20.1 9 19 L 9 11 C 9 9.9 9.9 9 11 9"),
                    (1.7, "M 5 15 L 5 5 C 5 3.9 5.9 3 7 3 L 17 3")]
        case .regenerate:
            return [(1.7, "M 3 12 C 2.93 8.29 5.14 4.92 8.56 3.5 C 11.99 2.09 15.94 2.92 18.5 5.6 M 21 12 C 21.07 15.71 18.86 19.08 15.44 20.5 C 12.01 21.91 8.06 21.08 5.5 18.4"),
                    (1.7, "M 17 3 L 17 8 L 12 8 M 7 21 L 7 16 L 12 16")]
        case .thumbUp:
            return [(1.6, "M 7 11 L 7 20 L 4 20 C 3.45 20 3 19.55 3 19 L 3 12 C 3 11.45 3.45 11 4 11 L 7 11 Z M 7 11 L 10.5 4 C 10.98 3.21 11.94 2.86 12.82 3.14 C 13.7 3.43 14.27 4.28 14.2 5.2 L 13.3 9 L 19 9 C 19.59 8.99 20.15 9.24 20.53 9.69 C 20.92 10.13 21.09 10.72 21 11.3 L 19.8 18.3 C 19.65 19.29 18.8 20.01 17.8 20 L 10 20 C 8.34 20 7 18.66 7 17 L 7 11 Z")]
        case .arrowDown:
            return [(2, "M 12 4 L 12 18 M 6 12 L 12 18 L 18 12")]
        }
    }

    fileprivate func path(_ d: String) -> Path {
        var path = Path()
        let toks = d.split(separator: " ")
        var i = 0
        func p() -> CGPoint { defer { i += 2 }; return CGPoint(x: Double(toks[i]) ?? 0, y: Double(toks[i + 1]) ?? 0) }
        while i < toks.count {
            let cmd = toks[i]; i += 1
            switch cmd {
            case "M": path.move(to: p())
            case "L": path.addLine(to: p())
            case "C": let c1 = p(); let c2 = p(); path.addCurve(to: p(), control1: c1, control2: c2)
            case "Z": path.closeSubpath()
            default: break
            }
        }
        return path
    }
}

/// Renders a [ThreadwireIcon] at [size] points in [color]. `thumbDown` is `thumbUp` rotated
/// 180 at the call site (the design's own relationship), e.g. `.rotationEffect(.degrees(180))`.
struct ThreadwireIconView: View {
    let icon: ThreadwireIcon
    var color: Color
    var size: CGFloat

    var body: some View {
        Canvas { ctx, canvasSize in
            let scale = canvasSize.width / 24
            let t = CGAffineTransform(scaleX: scale, y: scale)
            for element in icon.elements {
                let shape = icon.path(element.d).applying(t)
                if element.width == 0 {
                    ctx.fill(shape, with: .color(color))
                } else {
                    ctx.stroke(shape, with: .color(color),
                               style: StrokeStyle(lineWidth: element.width * scale, lineCap: .round, lineJoin: .round))
                }
            }
        }
        .frame(width: size, height: size)
    }
}
