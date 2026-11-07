package com.laurio.workoutlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laurio.workoutlog.logic.Status

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.labelMedium, color = Muted, letterSpacing = 1.5.sp,
    )
}

@Composable
fun Pill(text: String, fg: Color, bg: Color) {
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50)) {
        Text(
            text.uppercase(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        )
    }
}

@Composable
fun StatusPill(s: Status) {
    val (fg, bg) = statusColors(s)
    Pill(s.label, fg, bg)
}

/** A tappable rounded chip; filled when [selected]. */
@Composable
fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) cs.primary else cs.surfaceVariant,
        contentColor = if (selected) cs.onPrimary else cs.onSurface,
    ) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}

/** Fills toward the area's target; the tick in the middle is the target, the far end is twice it. */
@Composable
fun TargetBar(ratio: Double, color: Color, modifier: Modifier = Modifier) {
    val fill = (ratio.coerceIn(0.0, 2.0) / 2.0).toFloat()
    Box(
        modifier.height(6.dp).clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(color))
        Box(Modifier.fillMaxHeight().fillMaxWidth(0.5f)) {
            Box(
                Modifier.align(Alignment.CenterEnd).width(2.dp).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)),
            )
        }
    }
}

/** ▶ button that opens a movement's instruction video (YouTube app or browser). Nothing if there's no link. */
@Composable
fun VideoButton(url: String, name: String) {
    if (url.isBlank()) return
    val uri = LocalUriHandler.current
    IconButton(onClick = { runCatching { uri.openUri(url) } }) {
        Icon(Icons.Filled.PlayArrow, contentDescription = "Watch how to do $name", tint = MaterialTheme.colorScheme.primary)
    }
}
