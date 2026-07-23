package com.fsk.threadwire.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.fsk.threadwire.ui.theme.ThreadwireTheme

/**
 * Paperclip popover - "Photo"/"File" chrome only, both inert (M3/media hasn't started,
 * so there's nothing real to wire yet - matches this milestone's "visually complete,
 * functionally inert" treatment for out-of-scope capability, same as the mic button).
 */
@Composable
internal fun AttachmentPickerPopover(expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Photo", style = ThreadwireTheme.typography.msg) },
            leadingIcon = { PickerIcon(icon = Icons.Filled.Photo) },
            onClick = onDismiss,
        )
        DropdownMenuItem(
            text = { Text("File", style = ThreadwireTheme.typography.msg) },
            leadingIcon = { PickerIcon(icon = Icons.AutoMirrored.Filled.InsertDriveFile) },
            onClick = onDismiss,
        )
    }
}

@Composable
private fun PickerIcon(icon: ImageVector) {
    Icon(imageVector = icon, contentDescription = null, tint = ThreadwireTheme.colors.textSecondary)
}
