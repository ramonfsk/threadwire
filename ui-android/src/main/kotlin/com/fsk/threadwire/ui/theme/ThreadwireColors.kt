package com.fsk.threadwire.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design System Adoption milestone - values transcribed verbatim from the commissioned
 * design (extracted from its standalone HTML export's `getColors(dark)` function), not
 * derived from Material 3's `ColorScheme` slots - the design's roles (`surfaceAlt`,
 * `bubbleAssistant`, `code`, ...) don't map cleanly onto M3's fixed slot set, so this is
 * a plain value holder rather than a `ColorScheme` extension. [headerBg]/[border] carry
 * their alpha channel as authored (composited over [bg]), not baked into an opaque color.
 */
data class ThreadwireColors(
    val bg: Color,
    val headerBg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    val bubbleAssistant: Color,
    val inputBg: Color,
    val code: Color,
    val accent: Color,
    val destructive: Color,
)

/**
 * Default accent (user bubble fill) - not theme-dependent, matches the design's default
 * `accentColor` prop. Design doc §12's full `ChatUIConfig` (remote theming, alternate
 * accents) isn't built yet - this milestone hardcodes the default rather than guessing
 * an unspec'd config API shape.
 */
val ThreadwireAccent = Color(0xFF3B6EA5)

/** Same in light/dark - used for destructive confirm-card actions and the failed-send row. */
val ThreadwireDestructive = Color(0xFFC24545)

/** Manual-sync risk accepted (see M2.6 plan): hand-transcribed from the design, not codegen'd. */
val LightThreadwireColors = ThreadwireColors(
    bg = Color(0xFFF7F4EF),
    headerBg = Color(0xF2F7F4EF),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEFEAE1),
    text = Color(0xFF211B14),
    textSecondary = Color(0x9E211B14),
    textTertiary = Color(0x5C211B14),
    border = Color(0x14211B14),
    bubbleAssistant = Color(0xFFFFFFFF),
    inputBg = Color(0xFFFFFFFF),
    code = Color(0xFFF1ECE2),
    accent = ThreadwireAccent,
    destructive = ThreadwireDestructive,
)

val DarkThreadwireColors = ThreadwireColors(
    bg = Color(0xFF18130F),
    headerBg = Color(0xEB18130F),
    surface = Color(0xFF241D17),
    surfaceAlt = Color(0xFF2A2219),
    text = Color(0xFFF3EDE4),
    textSecondary = Color(0x9EF3EDE4),
    textTertiary = Color(0x5CF3EDE4),
    border = Color(0x1AF3EDE4),
    bubbleAssistant = Color(0xFF241D17),
    inputBg = Color(0xFF241D17),
    code = Color(0xFF2E2418),
    accent = ThreadwireAccent,
    destructive = ThreadwireDestructive,
)
