package com.fsk.threadwire.ui.bubbles

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.fsk.threadwire.ui.theme.ThreadwireTheme
import java.text.DateFormat
import java.util.Date

/**
 * The design's slim send-time line below a bubble (M2.6): meta type, tertiary color.
 * Renders nothing for an unknown time ([ChatMessage.timestampMillis] == 0, e.g. history
 * messages with no server-provided time yet). Locale-aware short time via [DateFormat].
 */
@Composable
internal fun MessageTimestamp(millis: Long, modifier: Modifier = Modifier) {
    if (millis <= 0L) return
    val formatted = remember(millis) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
    }
    Text(
        text = formatted,
        style = ThreadwireTheme.typography.meta,
        color = ThreadwireTheme.colors.textTertiary,
        modifier = modifier,
    )
}
