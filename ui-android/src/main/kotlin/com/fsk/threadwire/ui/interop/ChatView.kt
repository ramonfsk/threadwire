package com.fsk.threadwire.ui.interop

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView
import com.fsk.threadwire.session.ChatSession
import com.fsk.threadwire.ui.ChatScreen

/**
 * Classic-View wrapper around `ChatScreen` (design doc §13.1) - lets apps still built
 * on XML/Fragments embed the chat screen without migrating to Compose. Subclasses
 * [AbstractComposeView] (the recommended base for a reusable custom View backed by
 * Compose), not a one-off [androidx.compose.ui.platform.ComposeView] instance.
 */
class ChatView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AbstractComposeView(context, attrs) {

    /** Must be set before this view is attached/composed. */
    var session: ChatSession? = null
        set(value) {
            field = value
            disposeComposition()
        }

    @Composable
    override fun Content() {
        session?.let { ChatScreen(it) }
    }
}
