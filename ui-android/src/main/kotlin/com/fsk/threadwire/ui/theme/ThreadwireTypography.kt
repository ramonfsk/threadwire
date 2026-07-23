package com.fsk.threadwire.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The design's 3-tier font scale - `medium` is this milestone's hardcoded default (see ThreadwireColors' ChatUIConfig note). */
enum class ThreadwireFontScale { SMALL, MEDIUM, LARGE }

data class ThreadwireTypography(
    val title: TextStyle,
    val header: TextStyle,
    val msg: TextStyle,
    val meta: TextStyle,
    val name: TextStyle,
)

/** Sizes (sp) transcribed verbatim from the design's `FS` table. */
fun threadwireTypography(scale: ThreadwireFontScale): ThreadwireTypography = when (scale) {
    ThreadwireFontScale.SMALL -> ThreadwireTypography(
        title = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        header = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        msg = TextStyle(fontSize = 14.sp),
        meta = TextStyle(fontSize = 12.sp),
        name = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    )
    ThreadwireFontScale.MEDIUM -> ThreadwireTypography(
        title = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.Bold),
        header = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        msg = TextStyle(fontSize = 15.5.sp),
        meta = TextStyle(fontSize = 12.5.sp),
        name = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    )
    ThreadwireFontScale.LARGE -> ThreadwireTypography(
        title = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
        header = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        msg = TextStyle(fontSize = 17.sp),
        meta = TextStyle(fontSize = 13.5.sp),
        name = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    )
}

/** Card / generic container corner radius (design: card wrappers, chips use ~14-16). */
const val ThreadwireCardCornerRadiusDp = 16

/**
 * Message-bubble corner radii, transcribed from the design's `bubbleUser`/`bubbleAssistant`
 * (`18px 18px 4px 18px` and `18px 18px 18px 4px`) — asymmetric "tail" corners: user's tail
 * is bottom-end (right), assistant's is bottom-start (left). CSS order is
 * top-left/top-right/bottom-right/bottom-left; Compose order is topStart/topEnd/bottomEnd/bottomStart.
 */
val ThreadwireUserBubbleShape = androidx.compose.foundation.shape.RoundedCornerShape(
    topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp,
)
val ThreadwireAssistantBubbleShape = androidx.compose.foundation.shape.RoundedCornerShape(
    topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp,
)
