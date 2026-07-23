package com.fsk.threadwire.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

private val LocalThreadwireColors = compositionLocalOf { LightThreadwireColors }
private val LocalThreadwireTypography = compositionLocalOf { threadwireTypography(ThreadwireFontScale.MEDIUM) }

/**
 * Design System Adoption milestone - the source of truth every retokenized composable
 * reads from, instead of `MaterialTheme.colorScheme`. Still wraps `MaterialTheme`
 * underneath so interop widgets (e.g. `OutlinedTextField`) keep sane defaults.
 */
object ThreadwireTheme {
    val colors: ThreadwireColors
        @Composable get() = LocalThreadwireColors.current
    val typography: ThreadwireTypography
        @Composable get() = LocalThreadwireTypography.current
}

@Composable
fun ThreadwireTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: ThreadwireFontScale = ThreadwireFontScale.MEDIUM,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkThreadwireColors else LightThreadwireColors
    CompositionLocalProvider(
        LocalThreadwireColors provides colors,
        LocalThreadwireTypography provides threadwireTypography(fontScale),
    ) {
        MaterialTheme(content = content)
    }
}
