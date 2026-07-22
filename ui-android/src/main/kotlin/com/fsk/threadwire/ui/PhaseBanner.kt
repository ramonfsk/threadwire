package com.fsk.threadwire.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fsk.threadwire.session.SessionPhase

/**
 * A thin banner reflecting [SessionPhase] directly (not per-message) - design doc
 * §14.1 item 5 "Handoff transition," kept basic: a system banner is in scope for M2,
 * an agent avatar and differentiated typing indicator are finer M6 polish once real
 * handoff transport exists (M1 already models the phase cycle; M2 just displays it).
 */
@Composable
internal fun PhaseBanner(phase: SessionPhase, modifier: Modifier = Modifier) {
    val text = when (phase) {
        is SessionPhase.AiActive -> null
        is SessionPhase.HandoffPending -> "You're being transferred to a human agent..."
        is SessionPhase.HandoffActive -> {
            if (phase.agentName.isBlank()) "Connected with a human agent" else "Connected with ${phase.agentName}"
        }
    }
    AnimatedVisibility(visible = text != null) {
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(8.dp)
                    .semantics { contentDescription = text },
            )
        }
    }
}
