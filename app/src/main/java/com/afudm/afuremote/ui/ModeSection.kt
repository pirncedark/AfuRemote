package com.afudm.afuremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun ModeSection(onModeChange: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Çalışma modu")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onModeChange(null) }) { Text("Otomatik") }
            OutlinedButton(onClick = { onModeChange("tv") }) { Text("TV") }
            OutlinedButton(onClick = { onModeChange("phone") }) { Text("Telefon") }
        }
    }
}
