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
import com.fsk.threadwire.ui.MessagePartRenderer

/** Left-aligned, neutral surface color, explicit "AI" label - design doc §14.2. */
@Composable
internal fun AssistantBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    Column(modifier = modifier.fillMaxWidth()) {
        AuthorLabel("AI")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Column(
                modifier = Modifier
                    .widthIn(max = screenWidth * 0.8f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(12.dp)
                    .semantics { contentDescription = "AI said: ${message.accessibleSummary()}" },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                message.parts.forEach { part -> MessagePartRenderer(part) }
            }
        }
    }
}
