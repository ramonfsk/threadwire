package com.fsk.threadwire.ui.bubbles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fsk.threadwire.session.ChatMessage
import com.fsk.threadwire.session.MessagePart
import com.fsk.threadwire.ui.MessagePartRenderer
import com.fsk.threadwire.ui.icons.ThreadwireIcons
import com.fsk.threadwire.ui.theme.ThreadwireTheme
import com.fsk.threadwire.ui.theme.ThreadwireUserBubbleShape

/**
 * Right-aligned, accent-filled, no author label (matches the design - see `AuthorLabel`'s
 * KDoc for why non-user messages keep one and this doesn't). Bubble shape/padding are
 * transcribed from the design's `bubbleUser` (asymmetric radius, 10x14 padding).
 * [onRetry] is only ever invoked when [ChatMessage.deliveryFailed] is true.
 */
@Composable
internal fun UserBubble(message: ChatMessage, modifier: Modifier = Modifier, onRetry: () -> Unit = {}) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Row(
            modifier = Modifier
                .widthIn(max = screenWidth * 0.8f)
                .background(ThreadwireTheme.colors.accent, ThreadwireUserBubbleShape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .semantics { contentDescription = "You said: ${message.accessibleSummary()}" },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            message.parts.forEach { part -> MessagePartRenderer(part, textColor = Color.White) }
        }
        if (message.deliveryFailed) {
            Row(
                modifier = Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Failed to send",
                    style = ThreadwireTheme.typography.meta,
                    color = ThreadwireTheme.colors.destructive,
                )
                // retryIconBtn: 20px circle, tinted-red background (design spec).
                Row(
                    modifier = Modifier
                        .size(20.dp)
                        .background(ThreadwireTheme.colors.destructive.copy(alpha = 0.12f), CircleShape)
                        .clickable(onClick = onRetry)
                        .semantics { contentDescription = "Retry sending message" },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = ThreadwireIcons.Regenerate,
                        contentDescription = null,
                        tint = ThreadwireTheme.colors.destructive,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        MessageTimestamp(message.timestampMillis, modifier = Modifier.padding(top = 2.dp, end = 4.dp))
    }
}

/** Plain-text summary for accessibility labels - falls back to a generic description for non-text parts. */
internal fun ChatMessage.accessibleSummary(): String =
    parts.joinToString(" ") { part ->
        when (part) {
            is MessagePart.Text -> part.text
            is MessagePart.ToolCall -> "tool call"
            is MessagePart.Card -> "card"
            is MessagePart.Custom -> part.type
            is MessagePart.Unknown -> "unsupported content"
        }
    }.ifBlank { "empty message" }
