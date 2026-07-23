package com.fsk.threadwire.ui.bubbles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.fsk.threadwire.session.ChatMessage
import com.fsk.threadwire.ui.MessagePartRenderer
import com.fsk.threadwire.ui.icons.ThreadwireIcons
import com.fsk.threadwire.ui.theme.ThreadwireAssistantBubbleShape
import com.fsk.threadwire.ui.theme.ThreadwireTheme

/** Left-aligned, neutral surface color, minimal author label - see AuthorLabel's KDoc (§14.2). */
@Composable
internal fun AssistantBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    Column(modifier = modifier.fillMaxWidth()) {
        AuthorLabel("AI")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Column(
                modifier = Modifier
                    .widthIn(max = screenWidth * 0.8f)
                    .background(ThreadwireTheme.colors.bubbleAssistant, ThreadwireAssistantBubbleShape)
                    .border(1.dp, ThreadwireTheme.colors.border, ThreadwireAssistantBubbleShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .semantics { contentDescription = "AI said: ${message.accessibleSummary()}" },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                message.parts.forEach { part -> MessagePartRenderer(part) }
            }
        }
        MessageReactionRow(message = message)
    }
}

/**
 * Copy is real (local clipboard, no `:core` involvement). Regenerate/thumbs are
 * visually complete but inert - regenerating a completed AI turn and recording
 * feedback both need real `:core` capability that doesn't exist yet (regenerate
 * would need a distinct "replace last AI reply" operation, not `retryLastFailedTurn`;
 * feedback belongs to M5's `ChatTelemetrySink`, not built yet) - flagged rather than
 * silently faked. Icons are real `material-icons-extended` glyphs (see ChatInputBar's
 * KDoc for why the earlier hand-drawn Canvas icons were replaced).
 */
@Composable
private fun MessageReactionRow(message: ChatMessage, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReactionIconButton(
            icon = ThreadwireIcons.Copy,
            contentDescription = "Copy message",
            onClick = { clipboard.setText(AnnotatedString(message.accessibleSummary())) },
        )
        ReactionIconButton(icon = ThreadwireIcons.Regenerate, contentDescription = "Regenerate response", onClick = {})
        ReactionIconButton(icon = ThreadwireIcons.ThumbUp, contentDescription = "Good response", onClick = {})
        // Thumbs-down is the same glyph rotated 180 (the design's own relationship).
        ReactionIconButton(icon = ThreadwireIcons.ThumbUp, contentDescription = "Bad response", rotate = 180f, onClick = {})
        // Send time sits right next to the thumbs pair (not pushed to the far edge).
        MessageTimestamp(message.timestampMillis, modifier = Modifier.padding(start = 4.dp))
    }
}

// actionBtn: 26px round, icon ~15, tint textTertiary (design spec).
@Composable
private fun ReactionIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, rotate: Float = 0f) {
    IconButton(onClick = onClick, modifier = Modifier.size(26.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = ThreadwireTheme.colors.textTertiary,
            modifier = Modifier.size(16.dp).rotate(rotate),
        )
    }
}
