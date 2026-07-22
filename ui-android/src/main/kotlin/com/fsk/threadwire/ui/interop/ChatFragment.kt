package com.fsk.threadwire.ui.interop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.fsk.threadwire.session.ChatSession
import com.fsk.threadwire.ui.ChatScreen

/**
 * Fragment wrapper around `ChatScreen` (design doc §13.1's "`ChatFragment`/`ChatView`"),
 * for apps still built on the classic Fragment/Navigation Component stack. [session]
 * must be set before the fragment's view is created (e.g. via a factory function or
 * immediately after instantiation, before it's attached to a `FragmentManager`).
 */
class ChatFragment : Fragment() {

    var session: ChatSession? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                session?.let { ChatScreen(it) }
            }
        }
    }

    companion object {
        fun newInstance(session: ChatSession): ChatFragment = ChatFragment().apply {
            this.session = session
        }
    }
}
