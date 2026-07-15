package com.fsk.threadwire.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsk.threadwire.session.ChatConfig
import com.fsk.threadwire.session.ChatSession
import com.fsk.threadwire.session.MessageAuthor
import com.fsk.threadwire.session.MessagePart
import kotlinx.coroutines.launch

/**
 * Primary `:ui-android` entry point (design doc §13.1). Session lifecycle stays
 * host-owned - callers using this overload are responsible for [ChatSession.close].
 */
@Composable
fun ChatScreen(session: ChatSession, modifier: Modifier = Modifier) {
    val state by session.state.collectAsStateWithLifecycle()
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index
            lastVisibleIndex == null || lastVisibleIndex >= info.totalItemsCount - 1
        }
    }

    // Auto-scroll to the newest item only while already at the bottom - never yank a
    // user who's scrolled up to read history (design doc §14.1 "jump to bottom").
    LaunchedEffect(state.messages.size, state.isAwaitingResponse) {
        if (isAtBottom) {
            val lastIndex = (state.messages.size - 1).coerceAtLeast(0)
            listState.animateScrollToItem(lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        PhaseBanner(state.phase)
        state.lastError?.let { error ->
            ErrorBanner(
                error = error,
                onRetry = {
                    val lastUserText = state.messages
                        .lastOrNull { it.author == MessageAuthor.USER }
                        ?.parts
                        ?.filterIsInstance<MessagePart.Text>()
                        ?.firstOrNull()
                        ?.text
                    if (lastUserText != null) session.sendMessage(lastUserText)
                },
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            MessageList(state = state, listState = listState, modifier = Modifier.fillMaxSize())
            if (!isAtBottom) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem((state.messages.size - 1).coerceAtLeast(0))
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = "Jump to latest message" },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("Jump to latest")
                }
            }
        }
        ChatInputBar(
            text = inputText,
            onTextChange = { inputText = it },
            isAwaitingResponse = state.isAwaitingResponse,
            onSend = {
                val toSend = inputText
                if (toSend.isNotBlank()) {
                    session.sendMessage(toSend)
                    inputText = ""
                }
            },
            onStop = { session.cancelCurrentTurn() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Convenience entry point: builds a [ChatSession] internally via `:core`'s factory and
 * closes it on teardown, so hosts never need to touch `:core` directly - matches design
 * doc §13's "`:sample-app-*` never privileged access" framing extended to any minimal
 * integrator.
 */
@Composable
fun ChatScreen(config: ChatConfig, sessionId: String, modifier: Modifier = Modifier) {
    val session = remember(config, sessionId) { ChatSession.create(config, sessionId) }
    DisposableEffect(session) {
        onDispose { session.close() }
    }
    ChatScreen(session, modifier)
}
