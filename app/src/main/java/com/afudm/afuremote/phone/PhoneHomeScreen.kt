package com.afudm.afuremote.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afudm.afuremote.classify.LinkClassifier
import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.ui.Glyph
import com.afudm.afuremote.ui.ModeSection
import com.afudm.afuremote.ui.RemoteColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PhoneHomeScreen(versionName: String, onModeChange: (String?) -> Unit, footer: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val graph = remember { PhoneGraph.get(context) }
    val devices by graph.discovery.devices.collectAsState()
    val scanning by graph.discovery.scanning.collectAsState()
    val scope = rememberCoroutineScope()

    // Son seçilen TV, doğrulanmadan önce de gösterilir (nokta kırmızı kalır).
    var current by remember {
        mutableStateOf(graph.known.all().firstOrNull { it.id == graph.known.lastSelected }?.let { TvDevice(it.id, it.name, it.model, it.host, it.port, "") })
    }
    var picker by remember { mutableStateOf(current == null) }
    var settings by remember { mutableStateOf(false) }
    var textDialog by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        graph.discovery.acquire()
        onDispose { graph.discovery.release() }
    }

    fun select(tv: TvDevice) {
        current = tv
        graph.known.lastSelected = tv.id
        picker = false
    }

    // Bulunan listeyle eşitle: seçili TV'nin güncel adresini al; hiç seçim yoksa ilk bulunana bağlan.
    LaunchedEffect(devices) {
        val live = devices.firstOrNull { it.id == current?.id }
        when {
            live != null -> current = live
            current == null && devices.isNotEmpty() -> select(devices.first())
        }
    }
    LaunchedEffect(status) {
        if (status.startsWith("✓")) { delay(2_000); status = "" }
    }

    fun send(quiet: Boolean = true, block: suspend (TvDevice) -> SendResult) {
        val tv = current ?: run { status = Messages.NO_TV; return }
        scope.launch {
            if (!quiet) status = "Gönderiliyor…"
            val r = block(tv)
            status = when (r) {
                SendResult.Ok -> if (quiet) "" else "✓ ${tv.name}"
                SendResult.Unauthorized -> Messages.DENIED
                is SendResult.Failed -> r.message
            }
        }
    }
    val onPairing: (String) -> Unit = { status = it }

    val tv = current
    Box(Modifier.fillMaxSize().background(RemoteColors.Background)) {
        if (tv != null) {
            RemoteScreen(
                tv = tv,
                online = devices.any { it.id == tv.id },
                status = status,
                onKey = { k -> send { graph.controller.key(it, k, onPairing) } },
                onLaunch = { pkg -> send { graph.controller.launch(it, pkg, onPairing) } },
                onText = { textDialog = true },
                onLink = { linkDialog = true },
                onPickTv = { picker = true },
                onSettings = { settings = true }
            )
        }
        if (picker || tv == null) {
            DiscoveryScreen(
                devices = devices,
                selectedId = tv?.id,
                scanning = scanning,
                onSelect = ::select,
                onRefresh = { graph.discovery.refresh() },
                onSettings = { settings = true },
                onClose = if (tv != null) ({ picker = false }) else null
            )
            if (tv != null) BackHandler { picker = false }
        }
        if (settings) {
            SettingsPage(versionName, onModeChange, footer, onClose = { settings = false }) { url ->
                send(quiet = false) { graph.controller.open(it, OpenRequest(url), onPairing) }
            }
            BackHandler { settings = false }
        }
    }

    if (textDialog) {
        var text by remember { mutableStateOf("") }
        fun submit() {
            val value = text
            textDialog = false
            if (value.isNotEmpty()) send(quiet = false) { graph.controller.text(it, value, onPairing) }
        }
        AlertDialog(
            onDismissRequest = { textDialog = false },
            title = { Text("TV'ye yaz") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TV'de bir arama/yazı alanı seçili olmalı.", fontSize = 13.sp)
                    SendField("Metin", text, { text = it }) { submit() }
                }
            },
            confirmButton = { TextButton(onClick = { submit() }) { Text("Gönder") } },
            dismissButton = { TextButton(onClick = { textDialog = false }) { Text("Vazgeç") } }
        )
    }

    if (linkDialog) {
        var link by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        fun submit() {
            val url = LinkClassifier.extractUrl(link) ?: run { error = "Geçerli bir link yazın"; return }
            linkDialog = false
            send(quiet = false) { graph.controller.open(it, OpenRequest(url), onPairing) }
        }
        AlertDialog(
            onDismissRequest = { linkDialog = false },
            title = { Text("TV'de link aç") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SendField("Link", link, { link = it; error = "" }) { submit() }
                    if (error.isNotBlank()) Text(error, color = RemoteColors.Offline, fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { submit() }) { Text("TV'de aç") } },
            dismissButton = { TextButton(onClick = { linkDialog = false }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun SettingsPage(
    versionName: String,
    onModeChange: (String?) -> Unit,
    footer: @Composable () -> Unit,
    onClose: () -> Unit,
    onOpenLink: (String) -> Unit
) {
    var link by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(RemoteColors.Background).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Ayarlar", color = RemoteColors.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            RoundTap(Glyph.CLOSE, "Kapat", onClose, size = 26)
        }
        footer()
        Text("Link gönder", color = RemoteColors.Muted, fontSize = 14.sp)
        SendField("Link", link, { link = it; error = "" }) {}
        if (error.isNotBlank()) Text(error, color = RemoteColors.Offline, fontSize = 13.sp)
        Button(onClick = {
            val url = LinkClassifier.extractUrl(link) ?: run { error = "Geçerli bir link yazın"; return@Button }
            onOpenLink(url)
            onClose()
        }, modifier = Modifier.fillMaxWidth()) { Text("TV'de aç") }
        ModeSection(onModeChange)
        Text("AfuRemote $versionName", color = RemoteColors.Muted, fontSize = 13.sp)
        Spacer(Modifier.padding(bottom = 24.dp))
    }
}
