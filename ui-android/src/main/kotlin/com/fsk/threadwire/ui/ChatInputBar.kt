package com.fsk.threadwire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.fsk.threadwire.ui.icons.ThreadwireIcons
import com.fsk.threadwire.ui.theme.ThreadwireTheme

/**
 * The design's composer (`composerWrap` + `inputRow`): a single rounded-pill row holding
 * the attach button, the text field, and one trailing control - not three separate
 * elements. [isAwaitingResponse] swaps the trailing control between send/mic and stop.
 * The attach button opens an inert popover (M3/media not started).
 */
@Composable
internal fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isAwaitingResponse: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var attachmentPickerExpanded by remember { mutableStateOf(false) }
    // composerWrap: headerBg background runs to the bottom edge (under the nav bar); the
    // content is inset above the nav bar and the keyboard (imePadding) so it rises with
    // the keyboard instead of being covered. This keeps the surface edge-to-edge.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ThreadwireTheme.colors.headerBg)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // inputRow: one pill (border, radius 22, minHeight 44, padding 6, gap 6).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp)
                .background(ThreadwireTheme.colors.inputBg, RoundedCornerShape(22.dp))
                .border(1.dp, ThreadwireTheme.colors.border, RoundedCornerShape(22.dp))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                PillIconButton(
                    onClick = { attachmentPickerExpanded = true },
                    contentDescription = "Add attachment",
                ) {
                    Icon(ThreadwireIcons.Attach, contentDescription = null, tint = ThreadwireTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
                }
                AttachmentPickerPopover(expanded = attachmentPickerExpanded, onDismiss = { attachmentPickerExpanded = false })
            }

            Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                if (text.isEmpty()) {
                    Text("Message", style = ThreadwireTheme.typography.msg, color = ThreadwireTheme.colors.textTertiary)
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    enabled = !isAwaitingResponse,
                    textStyle = ThreadwireTheme.typography.msg.copy(color = ThreadwireTheme.colors.text),
                    cursorBrush = SolidColor(ThreadwireTheme.colors.accent),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { if (text.isNotBlank()) onSend() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Message input" },
                )
            }

            when {
                isAwaitingResponse -> CircleControl(
                    onClick = onStop,
                    background = ThreadwireTheme.colors.destructive,
                    contentDescription = "Stop generating",
                ) {
                    Icon(ThreadwireIcons.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
                text.isNotBlank() -> CircleControl(
                    onClick = onSend,
                    background = ThreadwireTheme.colors.accent,
                    contentDescription = "Send message",
                ) {
                    Icon(ThreadwireIcons.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                else -> PillIconButton(onClick = {}, enabled = false, contentDescription = "Voice input") {
                    Icon(ThreadwireIcons.Mic, contentDescription = null, tint = ThreadwireTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** iconBtnSm: 32px transparent square, icon centered. */
@Composable
private fun PillIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/** sendBtn/stopGenBtn: 32px filled circle. */
@Composable
private fun CircleControl(
    onClick: () -> Unit,
    background: Color,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
