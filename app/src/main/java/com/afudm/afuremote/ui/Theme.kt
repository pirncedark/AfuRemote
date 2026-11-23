package com.afudm.afuremote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object RemoteColors {
    val RingStart = Color(0xFFB89452)
    val RingEnd = Color(0xFFDCC28A)
    val GoldSoft = Color(0xFFF2E5C9)
    val Background @Composable get() = MaterialTheme.colorScheme.background
    val Button @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val ButtonPressed @Composable get() = MaterialTheme.colorScheme.primaryContainer
    val Pad @Composable get() = MaterialTheme.colorScheme.surfaceContainer
    val Icon @Composable get() = MaterialTheme.colorScheme.onSurface
    val Text @Composable get() = MaterialTheme.colorScheme.onSurface
    val Muted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Power @Composable get() = MaterialTheme.colorScheme.error
    val Online @Composable get() = if (isSystemInDarkTheme()) Color(0xFF81DEB0) else Color(0xFF18643A)
    val Offline @Composable get() = MaterialTheme.colorScheme.error
}

@Composable
fun AfuTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val bg = if (dark) Color(0xFF141518) else Color(0xFFF5F5F7)
    val glass = if (dark) Color(0xFF1A1C22).copy(alpha = .68f) else Color.White.copy(alpha = .72f)
    val border = if (dark) RemoteColors.GoldSoft.copy(alpha = .14f) else RemoteColors.RingStart.copy(alpha = .24f)
    val text = if (dark) Color(0xFFF7F3EC) else Color(0xFF24211D)
    val muted = if (dark) Color(0xFFC8C2B7) else Color(0xFF57534B)
    val accent = if (dark) RemoteColors.RingEnd else Color(0xFF70531D)
    val onAccent = if (dark) Color(0xFF211D15) else Color.White
    val raised = if (dark) Color(0xFF33302B) else Color(0xFFEEEAE2)
    val selected = if (dark) Color(0xFF51432B) else RemoteColors.GoldSoft
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = accent, onPrimary = onAccent, primaryContainer = selected, onPrimaryContainer = text,
        secondary = accent, onSecondary = onAccent, secondaryContainer = selected, onSecondaryContainer = text,
        tertiary = accent, onTertiary = onAccent, tertiaryContainer = selected, onTertiaryContainer = text,
        background = bg, onBackground = text, surface = bg, onSurface = text,
        surfaceVariant = glass, onSurfaceVariant = muted, surfaceTint = accent,
        surfaceContainerLowest = bg, surfaceContainerLow = raised, surfaceContainer = raised,
        surfaceContainerHigh = raised, surfaceContainerHighest = raised,
        outline = muted, outlineVariant = border, inversePrimary = RemoteColors.RingEnd
    )
    MaterialTheme(colorScheme = scheme, shapes = Shapes(extraLarge = RoundedCornerShape(28.dp)), content = content)
}
