package com.fsk.threadwire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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

@Composable
internal fun MessageList(
    state: ChatState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.messages, key = { it.localId }) { message ->
            when (message.author) {
                MessageAuthor.USER -> UserBubble(message)
                MessageAuthor.AI -> AssistantBubble(message)
                MessageAuthor.HUMAN_AGENT -> HumanAgentBubble(
                    message = message,
                    agentName = (state.phase as? SessionPhase.HandoffActive)?.agentName,
                )
                MessageAuthor.SYSTEM -> SystemBanner(message)
            }
        }
        state.streamingPhase()?.let { streaming ->
            item(key = "streaming-indicator") { StreamingIndicator(streaming) }
        }
    }
}
