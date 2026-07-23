package com.fsk.threadwire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fsk.threadwire.session.MessagePart
import com.fsk.threadwire.ui.theme.ThreadwireTheme
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.rememberMarkdownState

/**
 * Renders a single [MessagePart]. Non-text parts get a minimal, visually inert
 * placeholder chip - real card rendering is the M-Cards milestone's job (a separate
 * library), not this one. Using an exhaustive `when` means a new [MessagePart] case
 * added later is a compile error here, not a silent gap.
 *
 * [textColor] defaults to the design tokens' body text color, but the user bubble
 * (accent-filled, per M2.6) passes white explicitly - the markdown library takes an
 * explicit text color rather than reading it from ambient content color.
 */
@Composable
internal fun MessagePartRenderer(
    part: MessagePart,
    modifier: Modifier = Modifier,
    textColor: Color = ThreadwireTheme.colors.text,
) {
    when (part) {
        is MessagePart.Text -> {
            // retainState keeps the previously rendered content visible while the new
            // (larger) content re-parses, avoiding flicker on every streamed delta -
            // design doc §11's "pragmatic v1: re-parsing accumulated text on every
            // chunk is acceptable" is implemented by this, not a custom diff/patch.
            val markdownState = rememberMarkdownState(part.text, retainState = true)
            Markdown(
                markdownState,
                colors = markdownColor(text = textColor, codeBackground = ThreadwireTheme.colors.code, inlineCodeBackground = ThreadwireTheme.colors.code),
                modifier = modifier,
            )
        }

        is MessagePart.ToolCall -> InertPlaceholderChip("Tool call", modifier)
        is MessagePart.Card -> InertPlaceholderChip("Card", modifier)
        is MessagePart.Custom -> InertPlaceholderChip(part.type, modifier)
        is MessagePart.Unknown -> InertPlaceholderChip("Unsupported", modifier)
    }
}

@Composable
private fun InertPlaceholderChip(label: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        text = label,
        style = ThreadwireTheme.typography.meta,
        color = ThreadwireTheme.colors.textSecondary,
        modifier = modifier
            .background(ThreadwireTheme.colors.surfaceAlt, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = "Unsupported content: $label" },
    )
}
