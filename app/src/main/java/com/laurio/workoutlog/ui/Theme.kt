package com.laurio.workoutlog.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.laurio.workoutlog.logic.Status

val Cobalt = Color(0xFF6F95FF)
val Muted = Color(0xFF94A2B1)

private val Scheme = darkColorScheme(
    primary = Cobalt,
    onPrimary = Color(0xFF0B1220),
    secondary = Color(0xFF5FC58A),
    onSecondary = Color(0xFF06140E),
    background = Color(0xFF10151B),
    onBackground = Color(0xFFE6EBF0),
    surface = Color(0xFF18202A),
    onSurface = Color(0xFFE6EBF0),
    surfaceVariant = Color(0xFF243040),
    onSurfaceVariant = Muted,
    surfaceContainer = Color(0xFF151C25),
    secondaryContainer = Color(0xFF2A3A55),
    onSecondaryContainer = Color(0xFFE6EBF0),
    outline = Color(0xFF3A4757),
    error = Color(0xFFFF7A6E),
)

/** Text color and pill background for each status. */
fun statusColors(s: Status): Pair<Color, Color> = when (s) {
    Status.FRESH -> Color(0xFF5FC58A) to Color(0xFF15301F)
    Status.SOON -> Color(0xFFE5A73A) to Color(0xFF33270F)
    Status.DUE -> Color(0xFFF28A55) to Color(0xFF3A1F12)
    Status.OVERDUE -> Color(0xFFFF7A6E) to Color(0xFF3D1714)
}

@Composable
fun WorkoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
