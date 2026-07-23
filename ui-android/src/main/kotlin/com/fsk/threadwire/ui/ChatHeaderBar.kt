package com.fsk.threadwire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fsk.threadwire.ui.icons.ThreadwireIcons
import com.fsk.threadwire.ui.theme.ThreadwireTheme

/**
 * chatHeader: menu (left), search+close (right), and the title ABSOLUTELY centered so it
 * stays visually centered on screen regardless of the differing left/right button widths
 * (the design centers it independently, not with a flex-weight that shifts it off-center).
 */
@Composable
internal fun ChatHeaderBar(
    title: String,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ThreadwireTheme.colors.headerBg)
            // Background runs to the top edge (under the status bar); content is inset
            // below it. This is what makes the surface edge-to-edge instead of a boxed-in
            // panel with a margin around it.
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onMenuClick, modifier = Modifier.semantics { contentDescription = "Open chat list" }) {
                Icon(ThreadwireIcons.Menu, contentDescription = null, tint = ThreadwireTheme.colors.text, modifier = Modifier.size(22.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSearchClick, modifier = Modifier.semantics { contentDescription = "Search in chat" }) {
                    Icon(ThreadwireIcons.Search, contentDescription = null, tint = ThreadwireTheme.colors.text, modifier = Modifier.size(21.dp))
                }
                IconButton(onClick = onCloseClick, modifier = Modifier.semantics { contentDescription = "Close chat" }) {
                    Icon(ThreadwireIcons.Close, contentDescription = null, tint = ThreadwireTheme.colors.text, modifier = Modifier.size(20.dp))
                }
            }
        }
        Text(
            text = title,
            style = ThreadwireTheme.typography.header,
            color = ThreadwireTheme.colors.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 90.dp),
        )
    }
}
