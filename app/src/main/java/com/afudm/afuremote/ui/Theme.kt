package com.afudm.afuremote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AfuPurple = Color(0xFF7C5CFF)

@Composable
fun AfuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = AfuPurple, background = Color(0xFF110E1C), surface = Color(0xFF1B1530)), content = content)
}
