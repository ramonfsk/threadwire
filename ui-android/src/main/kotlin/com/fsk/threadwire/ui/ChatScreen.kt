package com.fsk.threadwire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fsk.threadwire.session.ChatConfig
import com.fsk.threadwire.session.ChatSession
import com.fsk.threadwire.ui.icons.ThreadwireIcons
import com.fsk.threadwire.ui.theme.ThreadwireTheme
import kotlinx.coroutines.launch

/**
 * Primary `:ui-android` entry point (design doc §13.1). Session lifecycle stays
 * host-owned - callers using this overload are responsible for [ChatSession.close].
 * [title]/[assistantName]/[suggestedPrompts] are host-supplied (`:core` has no per-session
 * display metadata). The header's menu/search/close controls are surfaced as host
 * navigation hooks ([onMenuClick]/[onSearchClick]/[onClose]) - a built-in chat-list
 * sidebar, in-chat search, and multi-session switching are deferred to a future
 * navigation/multi-session milestone (`:core` is single-session today).
 */
@Composable
fun ChatScreen(
    session: ChatSession,
    modifier: Modifier = Modifier,
    title: String = "Chat",
    assistantName: String = "AI",
    suggestedPrompts: List<String> = emptyList(),
    onMenuClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val state by session.state.collectAsStateWithLifecycle()
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    // With reverseLayout (see MessageList) the newest content is item 0 / the bottom, and a
    // streaming tail stays pinned there natively - so "at the bottom" is simply "item 0 is
    // resting against the bottom", with no follow math to get wrong (the forward-layout
    // version mis-fired on a single tall message and stuck at its top).
    // With reverseLayout, "at the bottom" (newest content fully visible) is exactly "can't
    // scroll any further toward the start (the bottom)". `canScrollBackward` is that signal,
    // robust regardless of how tall the newest item is - unlike a firstVisibleItemScrollOffset
    // threshold, which a single screen-filling item never crosses. The jump button shows
    // whenever the user is scrolled up off the bottom.
    val showJumpButton by remember { derivedStateOf { listState.canScrollBackward } }

    // reverseLayout pins a *growing* tail natively, so auto-follow only has to bring a
    // brand-new message into view - and only when the user is parked at the bottom (never
    // yank a history reader). A single snapshotFlow so the "was at the bottom" reading used
    // for a new message is the one from *before* that message shifted the indices.
    LaunchedEffect(Unit) {
        var prevId: String? = null
        var wasAtBottom = true
        snapshotFlow {
            state.messages.lastOrNull()?.localId to listState.canScrollBackward
        }.collect { (newestId, canScrollBackward) ->
            if (newestId != prevId) {
                if (prevId != null && wasAtBottom) listState.animateScrollToItem(0)
                prevId = newestId
            }
            wasAtBottom = !canScrollBackward
        }
    }

    ThreadwireTheme {
        Box(modifier = modifier.fillMaxSize().background(ThreadwireTheme.colors.bg)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ChatHeaderBar(
                        title = title,
                        onMenuClick = onMenuClick,
                        onSearchClick = onSearchClick,
                        onCloseClick = onClose,
                    )
                    PhaseBanner(state.phase)
                    Box(modifier = Modifier.weight(1f)) {
                        if (state.messages.isEmpty() && !state.isLoadingHistory && state.historyError == null) {
                            WelcomeView(
                                suggestedPrompts = suggestedPrompts,
                                onPickPrompt = { session.sendMessage(it) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            MessageList(
                                state = state,
                                listState = listState,
                                modifier = Modifier.fillMaxSize(),
                                onLoadOlderHistory = { session.loadOlderHistory() },
                                onRetryFailedMessage = { session.retryLastFailedTurn() },
                            )
                        }
                        if (showJumpButton && state.messages.isNotEmpty()) {
                            // scrollToBottomBtn: a 36px circle with a down-arrow, bordered,
                            // surface-filled, shadowed - the design's affordance, not a
                            // text pill. Positioned bottom-end above the composer.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = 16.dp)
                                    .shadow(6.dp, CircleShape)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(ThreadwireTheme.colors.surface)
                                    .border(1.dp, ThreadwireTheme.colors.border, CircleShape)
                                    .clickable {
                                        coroutineScope.launch { listState.animateScrollToItem(0) }
                                    }
                                    .semantics { contentDescription = "Jump to latest message" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = ThreadwireIcons.ArrowDown,
                                    contentDescription = null,
                                    tint = ThreadwireTheme.colors.text,
                                    modifier = Modifier.size(20.dp),
                                )
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
    }
}

/**
 * Convenience entry point: builds a [ChatSession] internally via `:core`'s factory and
 * closes it on teardown, so hosts never need to touch `:core` directly - matches design
 * doc §13's "`:sample-app-*` never privileged access" framing extended to any minimal
 * integrator.
 */
@Composable
fun ChatScreen(
    config: ChatConfig,
    sessionId: String,
    modifier: Modifier = Modifier,
    title: String = "Chat",
    assistantName: String = "AI",
    suggestedPrompts: List<String> = emptyList(),
    onMenuClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val session = remember(config, sessionId) { ChatSession.create(config, sessionId) }
    DisposableEffect(session) {
        onDispose { session.close() }
    }
    ChatScreen(
        session = session,
        modifier = modifier,
        title = title,
        assistantName = assistantName,
        suggestedPrompts = suggestedPrompts,
        onMenuClick = onMenuClick,
        onSearchClick = onSearchClick,
        onClose = onClose,
    )
}
