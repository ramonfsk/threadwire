package com.fsk.threadwire.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fsk.threadwire.session.ChatState
import com.fsk.threadwire.session.MessageAuthor

/** Design doc §14.1: distinguishes "thinking" (no visible output yet) from "generating" (streaming in). */
internal enum class StreamingPhase { THINKING, GENERATING }

internal fun ChatState.streamingPhase(): StreamingPhase? {
    if (!isAwaitingResponse) return null
    val last = messages.lastOrNull()
    val hasVisibleContent = last != null && last.author != MessageAuthor.USER && last.parts.isNotEmpty()
    return if (hasVisibleContent) StreamingPhase.GENERATING else StreamingPhase.THINKING
}

@Composable
internal fun StreamingIndicator(phase: StreamingPhase, modifier: Modifier = Modifier) {
    val label = when (phase) {
        StreamingPhase.THINKING -> "Thinking…"
        StreamingPhase.GENERATING -> "Generating…"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            },
    )
}
