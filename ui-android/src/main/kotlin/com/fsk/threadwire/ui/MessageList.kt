package com.fsk.threadwire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fsk.threadwire.session.ChatState
import com.fsk.threadwire.session.MessageAuthor
import com.fsk.threadwire.session.SessionPhase
import com.fsk.threadwire.ui.bubbles.AssistantBubble
import com.fsk.threadwire.ui.bubbles.HumanAgentBubble
import com.fsk.threadwire.ui.bubbles.SystemBanner
import com.fsk.threadwire.ui.bubbles.UserBubble
import com.fsk.threadwire.ui.theme.ThreadwireTheme

@Composable
internal fun MessageList(
    state: ChatState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onLoadOlderHistory: () -> Unit = {},
    onRetryFailedMessage: () -> Unit = {},
) {
    // reverseLayout is the standard chat pattern: item 0 sits at the *bottom* and the list
    // lays out upward, so the newest content stays pinned to the bottom natively - a reply
    // streaming into the last bubble grows upward while its bottom stays glued to the
    // viewport bottom, with no manual scroll math (which on a forward layout mis-fired for a
    // single tall message and stuck at its top). It also opens on the newest message for
    // free. Declaration order is therefore bottom-first: streaming indicator, then messages
    // newest-first, then the load-older row at the very top.
    LazyColumn(
        state = listState,
        modifier = modifier,
        reverseLayout = true,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.streamingPhase()?.let { streaming ->
            item(key = "streaming-indicator") { StreamingIndicator(streaming) }
        }
        items(state.messages.asReversed(), key = { it.localId }) { message ->
            when (message.author) {
                MessageAuthor.USER -> UserBubble(message, onRetry = onRetryFailedMessage)
                MessageAuthor.AI -> AssistantBubble(message)
                MessageAuthor.HUMAN_AGENT -> HumanAgentBubble(
                    message = message,
                    agentName = (state.phase as? SessionPhase.HandoffActive)?.agentName,
                )
                MessageAuthor.SYSTEM -> SystemBanner(message)
            }
        }
        // Plumbing-first pagination affordance (M2.5) - only ever shown for a session that
        // actually has a ChatHistoryProvider. At the visual top (end of the reversed list).
        if (state.hasMoreHistory || state.isLoadingHistory || state.historyError != null) {
            item(key = "load-older-history") {
                LoadOlderHistoryRow(
                    isLoading = state.isLoadingHistory,
                    hasError = state.historyError != null,
                    onClick = onLoadOlderHistory,
                )
            }
        }
    }
}

/**
 * M2.6 - the design's slim inline treatment (gray text + link), replacing the earlier
 * block-style row, for both the loading and the (previously unhandled) error state.
 */
@Composable
private fun LoadOlderHistoryRow(isLoading: Boolean, hasError: Boolean, onClick: () -> Unit) {
    when {
        isLoading -> CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        hasError -> Row(modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Couldn't load earlier messages.", style = ThreadwireTheme.typography.meta, color = ThreadwireTheme.colors.textSecondary)
            TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text("Retry", style = ThreadwireTheme.typography.meta, color = ThreadwireTheme.colors.accent)
            }
        }
        else -> TextButton(onClick = onClick) {
            Text("Load older messages", style = ThreadwireTheme.typography.meta, color = ThreadwireTheme.colors.accent)
        }
    }
}
