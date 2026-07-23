package com.fsk.threadwire.ui.bubbles

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fsk.threadwire.session.ChatMessage
import com.fsk.threadwire.ui.theme.ThreadwireTheme

/**
 * Renders a [com.fsk.threadwire.session.MessageAuthor.SYSTEM] message - not produced by
 * any code path yet (no `:core` logic synthesizes one today), but handled here so the
 * author dispatch stays exhaustive and forward-compatible.
 */
@Composable
internal fun SystemBanner(message: ChatMessage, modifier: Modifier = Modifier) {
    Text(
        text = message.accessibleSummary(),
        style = ThreadwireTheme.typography.meta,
        textAlign = TextAlign.Center,
        color = ThreadwireTheme.colors.textSecondary,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics { contentDescription = "System message: ${message.accessibleSummary()}" },
    )
}
