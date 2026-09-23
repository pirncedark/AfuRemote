package com.afudm.afuremote.tv

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afudm.afuremote.net.LocalIp
import com.afudm.afuremote.ui.ModeSection
import kotlinx.coroutines.delay

@Composable
fun TvHomeScreen(versionName: String, onModeChange: (String?) -> Unit, footer: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val ip = remember { LocalIp.wifiIpv4() ?: "Wi-Fi bağlantısı yok" }
    val accessibilityOn by produceState(RemoteAccessibilityService.instance != null) {
        while (true) { value = RemoteAccessibilityService.instance != null; delay(1_000) }
    }
    Column(Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("AfuRemote TV hazır", fontSize = 30.sp)
        Text("Telefonda AfuRemote'u açın — bu TV listede kendiliğinden görünür.", fontSize = 18.sp)
        Text("Adres: $ip   ·   Sürüm $versionName")
        Text(if (accessibilityOn) "Erişilebilirlik: açık ✓" else "Erişilebilirlik: KAPALI — geri/ana ekran tuşları ve AfuRemote kapalıyken link açma için bir kez açın.", fontSize = 16.sp)
        if (!accessibilityOn) Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Erişilebilirlik ayarlarını aç") }
        OutlinedButton(onClick = { AfuTvService.resetPairings(context) }) { Text("Telefon onaylarını sıfırla") }
        footer()
        ModeSection(onModeChange)
    }
}
