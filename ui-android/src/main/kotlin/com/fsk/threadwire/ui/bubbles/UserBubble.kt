package com.fsk.threadwire.ui.bubbles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fsk.threadwire.session.ChatMessage
import com.fsk.threadwire.session.MessagePart
import com.fsk.threadwire.ui.MessagePartRenderer

/** Right-aligned, primary color - the accepted chat-UI convention for the local user. */
@Composable
internal fun UserBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Row(
            modifier = Modifier
                .widthIn(max = screenWidth * 0.8f)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
                .padding(12.dp)
                .semantics { contentDescription = "You said: ${message.accessibleSummary()}" },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            message.parts.forEach { part -> MessagePartRenderer(part) }
        }
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
