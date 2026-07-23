package com.fsk.threadwire.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The chat surface's line icons (M2.6), rendered as native [ImageVector]s from the
 * commissioned design's *own* SVG path data - extracted verbatim from the design export, not
 * a stand-in set - so Android and iOS render identical glyphs (iOS builds the same paths in
 * `ThreadwireIcons.swift`). Every icon uses a 24x24 viewport, so the design's stroke widths
 * carry over 1:1; the tint is applied at the call site via `Icon(tint = ...)`, which colours
 * both stroked and filled glyphs. `thumbDown` is `ThumbUp` drawn rotated 180 (the design's
 * own relationship) - see the call sites.
 */
object ThreadwireIcons {

    private fun build(block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply(block)
            .build()

    private fun ImageVector.Builder.stroke(pathData: String, width: Float) = addPath(
        pathData = addPathNodes(pathData),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )

    private fun ImageVector.Builder.fill(pathData: String) = addPath(
        pathData = addPathNodes(pathData),
        fill = SolidColor(Color.Black),
    )

    val Menu: ImageVector = build { stroke("M4 6h16M4 12h16M4 18h16", 2f) }

    val Search: ImageVector = build {
        stroke("M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", 1.9f)
        stroke("M16 16L21 21", 1.9f)
    }

    val Close: ImageVector = build { stroke("M5 5l14 14M19 5L5 19", 2.2f) }

    val Attach: ImageVector = build {
        stroke("M21 11.5l-9 9a5 5 0 01-7-7l9-9a3.5 3.5 0 015 5l-8 8a2 2 0 01-3-3l7-7", 1.7f)
    }

    val Mic: ImageVector = build {
        stroke("M12 2h0a3 3 0 0 1 3 3v6a3 3 0 0 1 -3 3h0a3 3 0 0 1 -3 -3v-6a3 3 0 0 1 3 -3", 1.7f)
        stroke("M5 11a7 7 0 0014 0M12 18v3", 1.7f)
    }

    val Send: ImageVector = build { fill("M4 12l16-8-6 16-2.5-6.5L4 12z") }

    val Stop: ImageVector = build { fill("M8 7h8a1 1 0 011 1v8a1 1 0 01-1 1H8a1 1 0 01-1-1V8a1 1 0 011-1z") }

    val Copy: ImageVector = build {
        stroke("M11 9h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2", 1.7f)
        stroke("M5 15V5a2 2 0 012-2h10", 1.7f)
    }

    val Regenerate: ImageVector = build {
        stroke("M3 12a9 9 0 0115.5-6.4M21 12a9 9 0 01-15.5 6.4", 1.7f)
        stroke("M17 3v5h-5M7 21v-5h5", 1.7f)
    }

    val ThumbUp: ImageVector = build {
        stroke("M7 11v9H4a1 1 0 01-1-1v-7a1 1 0 011-1h3zm0 0l3.5-7a2 2 0 013.7 1.2L13.3 9H19a2 2 0 012 2.3l-1.2 7A2 2 0 0117.8 20H10a3 3 0 01-3-3v-6z", 1.6f)
    }

    val ArrowDown: ImageVector = build { stroke("M12 4v14M6 12l6 6 6-6", 2f) }
}
