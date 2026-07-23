package com.fsk.threadwire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.fsk.threadwire.session.ChatConfig
import com.fsk.threadwire.session.ChatContextProvider
import com.fsk.threadwire.session.ChatRequest
import com.fsk.threadwire.ui.ChatScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            // ChatScreen is edge-to-edge: it fills the whole window (background runs
            // under the status/nav bars) and applies the safe-area insets internally
            // (header below the status bar, composer above the nav bar/keyboard). So
            // the host just fills the window - no outer safeContentPadding, which would
            // inset the whole surface and leave a margin of window background around it.
            ChatScreen(
                config = sampleConfig,
                sessionId = "sample-session",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Illustrative config only, not a real BFF integration - `10.0.2.2` is the Android
 * emulator's alias for the host machine's `localhost`, matching `tools/fake-sse-server`
 * (M0) listening on port 8080 there. A real integrator supplies their own baseUrl/auth.
 */
private val sampleConfig = ChatConfig(
    baseUrl = "http://10.0.2.2:8080/chat",
    contextProvider = object : ChatContextProvider {
        override suspend fun headers(request: ChatRequest): Map<String, String> = emptyMap()
        override suspend fun contextPayload(request: ChatRequest): Map<String, Any?> = emptyMap()
    },
)
