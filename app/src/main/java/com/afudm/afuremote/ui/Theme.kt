package com.afudm.afuremote.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val AfuPurple = Color(0xFF7C5CFF)

/** Telefon kumandası renkleri (koyu gri zemin, mor→mavi halka). */
object RemoteColors {
    val Background = Color(0xFF1E1E1E)
    val Button = Color(0xFF333333)
    val ButtonPressed = Color(0xFF454545)
    val Pad = Color(0xFF2B2B2B)
    val Icon = Color(0xFFE6E6E6)
    val Text = Color(0xFFF2F2F2)
    val Muted = Color(0xFF8C8C8C)
    val RingStart = Color(0xFF8B5CF6)
    val RingEnd = Color(0xFF3B82F6)
    val Power = Color(0xFFE5483C)
    val Online = Color(0xFF34C759)
    val Offline = Color(0xFFE5483C)
}

@Composable
fun AfuTheme(content: @Composable () -> Unit) {
    // Kumanda görselindeki koyu gri tema: nötr yüzeyler, mor vurgu, açık gri yazı.
    val scheme = darkColorScheme(
        primary = RemoteColors.RingStart,
        onPrimary = Color.White,
        secondary = RemoteColors.RingEnd,
        onSecondary = Color.White,
        background = RemoteColors.Background,
        onBackground = RemoteColors.Text,
        surface = RemoteColors.Background,
        onSurface = RemoteColors.Text,
        surfaceVariant = RemoteColors.Button,
        onSurfaceVariant = RemoteColors.Muted,
        surfaceContainerLowest = RemoteColors.Background,
        surfaceContainerLow = RemoteColors.Pad,
        surfaceContainer = RemoteColors.Pad,
        surfaceContainerHigh = RemoteColors.Pad,
        surfaceContainerHighest = RemoteColors.Button,
        outline = Color(0xFF5A5A5A),
        outlineVariant = Color(0xFF3A3A3A),
        error = RemoteColors.Offline
    )
    MaterialTheme(colorScheme = scheme, shapes = Shapes(extraLarge = RoundedCornerShape(28.dp)), content = content)
}
