package com.fsk.threadwire.ui.bubbles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fsk.threadwire.session.ChatMessage
import com.fsk.threadwire.session.SessionPhase
import com.fsk.threadwire.ui.MessagePartRenderer

/**
 * Left-aligned, visually distinct accent from [AssistantBubble] (tertiary vs. secondary
 * container color) - design doc §14.2 requires AI/human be unambiguously distinguishable,
 * which the differing "Agent"/"AI" label already satisfies regardless of color choice.
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
                    .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(16.dp))
                    .padding(12.dp)
                    .semantics { contentDescription = "$label said: ${message.accessibleSummary()}" },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                message.parts.forEach { part -> MessagePartRenderer(part) }
            }
        }
    }
}
