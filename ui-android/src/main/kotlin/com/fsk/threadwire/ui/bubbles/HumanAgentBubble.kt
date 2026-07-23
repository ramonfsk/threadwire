package com.fsk.threadwire.ui.bubbles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fsk.threadwire.session.ChatMessage
import com.fsk.threadwire.session.SessionPhase
import com.fsk.threadwire.ui.MessagePartRenderer
import com.fsk.threadwire.ui.theme.ThreadwireAssistantBubbleShape
import com.fsk.threadwire.ui.theme.ThreadwireTheme

/**
 * Left-aligned, like [AssistantBubble] but on `surfaceAlt` (a subtly distinct neutral
 * shade already in the design tokens - the commissioned design doesn't call out a
 * dedicated human-agent color, so this reuses an existing token rather than inventing
 * one) - the differing "Agent"/"AI" label is still what §14.2 actually requires.
 * [agentName] comes from [SessionPhase.HandoffActive] when available; a generic "Agent"
 * label is used otherwise (e.g. phase has since moved on).
 */
@Composable
internal fun HumanAgentBubble(message: ChatMessage, agentName: String?, modifier: Modifier = Modifier) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val label = if (agentName.isNullOrBlank()) "Agent" else "Agent · $agentName"
    Column(modifier = modifier.fillMaxWidth()) {
        AuthorLabel(label)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Column(
                modifier = Modifier
                    .widthIn(max = screenWidth * 0.8f)
                    .background(ThreadwireTheme.colors.surfaceAlt, ThreadwireAssistantBubbleShape)
                    .border(1.dp, ThreadwireTheme.colors.border, ThreadwireAssistantBubbleShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .semantics { contentDescription = "$label said: ${message.accessibleSummary()}" },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                message.parts.forEach { part -> MessagePartRenderer(part) }
            }
        }
        MessageTimestamp(message.timestampMillis, modifier = Modifier.padding(top = 2.dp, start = 4.dp))
    }
}
