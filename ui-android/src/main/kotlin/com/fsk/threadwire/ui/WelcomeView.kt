package com.fsk.threadwire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fsk.threadwire.ui.theme.ThreadwireTheme

/**
 * The design's `welcomeWrap` empty-conversation state: a title and optional
 * suggested-prompt chips, centered. Shown while the conversation has no messages.
 * (The design's decorative assistant avatar was dropped - talking to an LLM, a
 * persona avatar reads as misleading, so the empty state is text-only.) Suggested
 * prompts are host-supplied (`:core` has no concept of them).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WelcomeView(
    suggestedPrompts: List<String>,
    onPickPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "How can I help you today?",
            style = ThreadwireTheme.typography.header,
            fontWeight = FontWeight.SemiBold,
            color = ThreadwireTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        if (suggestedPrompts.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                suggestedPrompts.forEach { prompt ->
                    Text(
                        text = prompt,
                        style = ThreadwireTheme.typography.msg,
                        color = ThreadwireTheme.colors.text,
                        modifier = Modifier
                            .background(ThreadwireTheme.colors.surfaceAlt, RoundedCornerShape(16.dp))
                            .border(1.dp, ThreadwireTheme.colors.border, RoundedCornerShape(16.dp))
                            .clickable { onPickPrompt(prompt) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
