package com.afudm.afuremote.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.afudm.afuremote.ui.AfuTheme

class PairPromptActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pairId = intent.getStringExtra(EXTRA_PAIR_ID).orEmpty()
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID).orEmpty()
        val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME).orEmpty()
        val code = intent.getStringExtra(EXTRA_CODE).orEmpty()
        setContent {
            AfuTheme {
                val focus = FocusRequester()
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Telefon eşleştirme isteği", style = MaterialTheme.typography.headlineMedium)
                    Text("$deviceName cihazının bu TV'de video açmasına izin verilsin mi?", Modifier.padding(top = 16.dp))
                    Text(code.chunked(3).joinToString(" "), style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(24.dp))
                    Text("Telefondaki kod aynı mı?", Modifier.padding(bottom = 24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(modifier = Modifier.focusRequester(focus), onClick = { answer(pairId, deviceId, true) }) { Text("İzin ver") }
                        OutlinedButton(onClick = { answer(pairId, deviceId, false) }) { Text("Reddet") }
                    }
                }
            }
        }
    }

    private fun answer(pairId: String, deviceId: String, allowed: Boolean) {
        PairBroker.decide(pairId, deviceId, allowed)
        finish()
    }

    companion object {
        const val EXTRA_PAIR_ID = "pair_id"
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_CODE = "pair_code"
    }
}
