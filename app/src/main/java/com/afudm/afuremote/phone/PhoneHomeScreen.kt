package com.afudm.afuremote.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afudm.afuremote.classify.LinkClassifier
import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.RemoteKey
import com.afudm.afuremote.ui.ModeSection
import kotlinx.coroutines.launch

@Composable
fun PhoneHomeScreen(versionName: String, onModeChange: (String?) -> Unit, footer: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val graph = remember { PhoneGraph.get(context) }
    val devices by graph.discovery.devices.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = devices.firstOrNull { it.id == selectedId } ?: devices.firstOrNull()
    var status by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        graph.discovery.acquire()
        onDispose { graph.discovery.release() }
    }

    fun send(block: suspend (TvDevice) -> SendResult) {
        val tv = selected ?: run { status = Messages.NO_TV; return }
        scope.launch {
            status = "Gönderiliyor…"
            status = when (val r = block(tv)) {
                SendResult.Ok -> "✓ ${tv.name}"
                SendResult.Unauthorized -> Messages.DENIED
                is SendResult.Failed -> r.message
            }
        }
    }
    val onPairing: () -> Unit = { status = Messages.WAITING_TV }
    fun key(k: RemoteKey) = send { graph.controller.key(it, k, onPairing) }

    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("AfuRemote", fontSize = 26.sp)
        if (devices.isEmpty()) Text("TV aranıyor… (TV'de AfuRemote açık ve aynı Wi-Fi'de olmalı)")
        devices.forEach { tv ->
            val mark = if (tv.id == selected?.id) "● " else "○ "
            OutlinedButton(onClick = { selectedId = tv.id }, modifier = Modifier.fillMaxWidth()) { Text("$mark${tv.name} · ${tv.model}") }
        }
        if (status.isNotBlank()) Text(status)

        OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("Link") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val url = LinkClassifier.extractUrl(link) ?: run { status = "Geçerli bir link yazın"; return@Button }
            send { graph.controller.open(it, OpenRequest(url), onPairing) }
        }, modifier = Modifier.fillMaxWidth()) { Text("TV'de aç") }

        Text("Kumanda")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { key(RemoteKey.BACK) }) { Text("↩ Geri") }
            Button(onClick = { key(RemoteKey.HOME) }) { Text("⌂ Ana ekran") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { key(RemoteKey.VOL_DOWN) }) { Text("Ses −") }
            Button(onClick = { key(RemoteKey.MUTE) }) { Text("Sessiz") }
            Button(onClick = { key(RemoteKey.VOL_UP) }) { Text("Ses +") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { key(RemoteKey.SEEK_BACK) }) { Text("⏪ 10 sn") }
            Button(onClick = { key(RemoteKey.PLAY_PAUSE) }) { Text("⏯") }
            Button(onClick = { key(RemoteKey.SEEK_FWD) }) { Text("10 sn ⏩") }
        }
        Text("Sürüm $versionName")
        footer()
        ModeSection(onModeChange)
    }
}
