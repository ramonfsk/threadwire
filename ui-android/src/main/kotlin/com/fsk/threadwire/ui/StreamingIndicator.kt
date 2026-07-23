package com.fsk.threadwire.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fsk.threadwire.session.ChatState
import com.fsk.threadwire.session.MessageAuthor
import com.fsk.threadwire.ui.theme.ThreadwireTheme

/** Design doc §14.1: distinguishes "thinking" (no visible output yet) from "generating" (streaming in). */
internal enum class StreamingPhase { THINKING, GENERATING }

internal fun ChatState.streamingPhase(): StreamingPhase? {
    if (!isAwaitingResponse) return null
    val last = messages.lastOrNull()
    val hasVisibleContent = last != null && last.author != MessageAuthor.USER && last.parts.isNotEmpty()
    return if (hasVisibleContent) StreamingPhase.GENERATING else StreamingPhase.THINKING
}

/**
 * The design's `typingDots`: a label followed by three staggered pulsing dots (6px,
 * textTertiary), replacing the earlier static "Thinking…" text.
 */
@Composable
internal fun StreamingIndicator(phase: StreamingPhase, modifier: Modifier = Modifier) {
    val label = when (phase) {
        StreamingPhase.THINKING -> "Thinking"
        StreamingPhase.GENERATING -> "Generating"
    }
    Row(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .semantics {
                contentDescription = "$label…"
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = ThreadwireTheme.typography.meta,
            color = ThreadwireTheme.colors.textSecondary,
            modifier = Modifier.padding(end = 3.dp),
        )
        TypingDot(delayMillis = 0)
        TypingDot(delayMillis = 150)
        TypingDot(delayMillis = 300)
    }
}

@Composable
private fun TypingDot(delayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "typingDot")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, delayMillis = delayMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha",
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .alpha(alpha)
            .background(ThreadwireTheme.colors.textTertiary, CircleShape),
    )
}
