package com.fsk.threadwire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fsk.threadwire.session.MessagePart
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState

/**
 * Renders a single [MessagePart]. Non-text parts get a minimal, visually inert
 * placeholder chip - real card/tool-call UI is M4/M3 scope, not this milestone. Using
 * an exhaustive `when` means a new [MessagePart] case added later is a compile error
 * here, not a silent gap.
 */
@Composable
internal fun MessagePartRenderer(part: MessagePart, modifier: Modifier = Modifier) {
    when (part) {
        is MessagePart.Text -> {
            // retainState keeps the previously rendered content visible while the new
            // (larger) content re-parses, avoiding flicker on every streamed delta -
            // design doc §11's "pragmatic v1: re-parsing accumulated text on every
            // chunk is acceptable" is implemented by this, not a custom diff/patch.
            // The library also has an append-only rememberStreamingMarkdownState API
            // that would need real deltas (not the full-accumulated snapshot :core
            // exposes) - a future optimization per §11, only worth it if jank is
            // actually observed on long messages.
            val markdownState = rememberMarkdownState(part.text, retainState = true)
            Markdown(markdownState, modifier = modifier)
        }

        is MessagePart.ToolCall -> InertPlaceholderChip("Tool call", modifier)
        is MessagePart.Card -> InertPlaceholderChip("Card", modifier)
        is MessagePart.Custom -> InertPlaceholderChip(part.type, modifier)
        is MessagePart.Unknown -> InertPlaceholderChip("Unsupported", modifier)
    }
}

@Composable
private fun InertPlaceholderChip(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = "Unsupported content: $label" },
    )
}
