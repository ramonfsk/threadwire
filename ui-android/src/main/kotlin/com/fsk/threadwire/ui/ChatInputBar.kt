package com.fsk.threadwire.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Text-only input (attachment/mic buttons are M3 scope, not built here). [isAwaitingResponse]
 * swaps the trailing button between send and stop, per design doc §14.1's "send/stop"
 * input-bar requirement. Text buttons (not icons) - no Material Icons dependency
 * verified/added for this milestone, and text reads unambiguously under VoiceOver/
 * TalkBack without depending on icon recognition (design doc §14.2 accessibility rule).
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Message input" },
            placeholder = { Text("Message") },
            enabled = !isAwaitingResponse,
        )
        if (isAwaitingResponse) {
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "Stop generating" },
            ) {
                Text("Stop")
            }
        } else {
            Button(
                onClick = onSend,
                enabled = text.isNotBlank(),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "Send message" },
            ) {
                Text("Send")
            }
        }
    }
}
