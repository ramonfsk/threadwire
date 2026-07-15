package com.fsk.threadwire.ui.bubbles

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design doc §14.2 hard rule: AI and human must always be unambiguously distinguishable
 * - never by color alone. An explicit text label on every non-user message satisfies
 * that rule (and the EU AI Act transparency point it cites) regardless of any theme/
 * color choice - deliberately not icon-only, since a label reads correctly under
 * VoiceOver/TalkBack without depending on icon semantics.
 */
@Composable
internal fun AuthorLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 2.dp),
    )
}
