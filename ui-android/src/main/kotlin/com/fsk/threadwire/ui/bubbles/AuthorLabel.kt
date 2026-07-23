package com.fsk.threadwire.ui.bubbles

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fsk.threadwire.ui.theme.ThreadwireTheme

/**
 * Design doc §14.2 hard rule: AI and human must always be unambiguously distinguishable
 * - never by color alone. The commissioned design (M2.6) has no author labels at all;
 * resolution confirmed with the maintainer: user bubbles stay unlabeled (matches the
 * design), but this label is kept - at a deliberately restrained meta-scale size - for
 * every non-user message, preserving the accessibility/compliance guarantee the design
 * itself doesn't address. Deliberately text, not icon-only, since a label reads
 * correctly under VoiceOver/TalkBack without depending on icon semantics.
 */
@Composable
internal fun AuthorLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = ThreadwireTheme.typography.meta,
        color = ThreadwireTheme.colors.textTertiary,
        modifier = modifier.padding(bottom = 2.dp),
    )
}
