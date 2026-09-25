package com.afudm.afuremote.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@Composable
fun PhoneHomeScreen(versionName: String, onModeChange: (String?) -> Unit, footer: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val graph = remember { PhoneGraph.get(context) }
    val devices by graph.discovery.devices.collectAsState()
    val scanning by graph.discovery.scanning.collectAsState()
    val scope = rememberCoroutineScope()

    // Son seçilen TV, doğrulanmadan önce de gösterilir (nokta kırmızı kalır).
    var current by remember {
        mutableStateOf(graph.known.all().firstOrNull { it.id == graph.known.lastSelected }?.let {
            TvDevice(it.id, it.name, it.model, it.host, it.port, "", runCatching { TvDevice.Backend.valueOf(it.backend) }.getOrDefault(TvDevice.Backend.AFUREMOTE), it.subtitle)
        })
    }
    var picker by remember { mutableStateOf(current == null) }
    var settings by remember { mutableStateOf(false) }
    var textDialog by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var ipDialog by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var ipError by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var pairingDevice by remember { mutableStateOf<TvDevice?>(null) }
    var pairingCode by remember { mutableStateOf("") }
    var codeWaiter by remember { mutableStateOf<CompletableDeferred<String?>?>(null) }

    DisposableEffect(Unit) {
        graph.discovery.acquire()
        onDispose { graph.discovery.release() }
    }

    fun select(tv: TvDevice) {
        current = tv
        graph.known.lastSelected = tv.id
        graph.known.remember(tv)
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
        // TV seçilmeden bir tuşa basılırsa seçici açılır.
        val tv = current ?: run { picker = true; return }
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
    suspend fun askForAtvCode(target: TvDevice): String = withContext(Dispatchers.Main) {
        pairingCode = ""
        pairingDevice = target
        CompletableDeferred<String?>().also { codeWaiter = it }
    }.await() ?: throw IllegalStateException("Eşleştirme iptal edildi")
    suspend fun pairAtv(target: TvDevice): Boolean = graph.atvPairing.pair(target.host, target.port, "AfuRemote") { askForAtvCode(target) }
    suspend fun remoteKey(target: TvDevice, key: com.afudm.afuremote.protocol.RemoteKey): SendResult =
        if (target.backend == TvDevice.Backend.ATV_REMOTE_V2) graph.atvRemote.key(target, key) { pairAtv(target) }
        else graph.controller.key(target, key, onPairing)
    suspend fun remoteText(target: TvDevice, value: String): SendResult =
        if (target.backend == TvDevice.Backend.ATV_REMOTE_V2) graph.atvRemote.text(target, value) { pairAtv(target) }
        else graph.controller.text(target, value, onPairing)
    suspend fun remoteLaunch(target: TvDevice, app: String): SendResult =
        if (target.backend == TvDevice.Backend.ATV_REMOTE_V2) graph.atvRemote.launch(target, "market://launch?id=$app") { pairAtv(target) }
        else graph.controller.launch(target, app, onPairing)

    val tv = current
    Box(Modifier.fillMaxSize().background(RemoteColors.Background)) {
        // Kumanda hep altta durur; geri tuşu seçiciyi/ayarları kapatıp buraya döner.
        RemoteScreen(
                tv = tv,
                online = tv != null && devices.any { it.id == tv.id },
                status = status,
                onKey = { k -> send { remoteKey(it, k) } },
                onLaunch = { pkg -> send { remoteLaunch(it, pkg) } },
                onText = { textDialog = true },
                onLink = { linkDialog = true },
                onPickTv = { picker = true },
                onSettings = { settings = true }
            )
        if (picker) {
            DiscoveryScreen(
                devices = devices,
                selectedId = tv?.id,
                scanning = scanning,
                onSelect = ::select,
                onRefresh = { graph.discovery.refresh() },
                onAddIp = { ipDialog = true; ipError = "" },
                onClose = { picker = false }
            )
            BackHandler { picker = false }
        }
        if (settings) {
            SettingsPage(versionName, onModeChange, footer, onClose = { settings = false }) { url ->
                send(quiet = false) { if (it.backend == TvDevice.Backend.ATV_REMOTE_V2) graph.atvRemote.launch(it, url) { pairAtv(it) } else graph.controller.open(it, OpenRequest(url), onPairing) }
            }
            BackHandler { settings = false }
        }
    }

    if (pairingDevice != null) {
        AlertDialog(
            onDismissRequest = { codeWaiter?.complete(null); codeWaiter = null; pairingDevice = null },
            title = { Text("${pairingDevice?.name} ile eşleştir") },
            text = { Column {
                Text("TV ekranında görünen 6 haneli hexadecimal kodu girin.")
                OutlinedTextField(value = pairingCode, onValueChange = { pairingCode = it.filter { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' }.take(6) }, label = { Text("TV kodu") }, singleLine = true)
                pairingDevice?.subtitle?.takeIf { it.matches(Regex("[0-9A-Fa-f:]{11,}")) }?.let { Text(it, color = RemoteColors.Muted) }
            } },
            confirmButton = { TextButton(onClick = { codeWaiter?.complete(pairingCode); codeWaiter = null; pairingDevice = null }) { Text("Eşleştir") } },
            dismissButton = { TextButton(onClick = { codeWaiter?.complete(null); codeWaiter = null; pairingDevice = null }) { Text("Vazgeç") } }
        )
    }

    if (ipDialog) {
        AlertDialog(
            onDismissRequest = { ipDialog = false },
            title = { Text("TV'yi IP ile ekle") },
            text = { Column {
                Text("TV ekranındaki IP adresini girin. Port: 9870")
                OutlinedTextField(value = manualIp, onValueChange = { manualIp = it; ipError = "" }, label = { Text("TV IP adresi") }, singleLine = true)
                if (ipError.isNotBlank()) Text(ipError, color = RemoteColors.Offline)
            } },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    val found = graph.discovery.addManually(manualIp)
                    if (found == null) ipError = "TV'ye bağlanılamadı. IP adresini ve TV uygulamasının açık olduğunu kontrol edin."
                    else { select(found); ipDialog = false }
                }
            }) { Text("Bağlan") } },
            dismissButton = { TextButton(onClick = { ipDialog = false }) { Text("Vazgeç") } }
        )
    }

    if (textDialog) {
        var text by remember { mutableStateOf("") }
        fun submit() {
            val value = text
            textDialog = false
            if (value.isNotEmpty()) send(quiet = false) { remoteText(it, value) }
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
            send(quiet = false) { if (it.backend == TvDevice.Backend.ATV_REMOTE_V2) graph.atvRemote.launch(it, url) { pairAtv(it) } else graph.controller.open(it, OpenRequest(url), onPairing) }
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
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            RoundTap(Glyph.CLOSE, "Kapat", onClose)
        }
        Text("ayarlar", color = RemoteColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        SettingsCard("Güncelleme") { footer() }
        SettingsCard("Link gönder") {
            SendField("Link", link, { link = it; error = "" }) {}
            if (error.isNotBlank()) Text(error, color = RemoteColors.Offline, fontSize = 13.sp)
            Button(onClick = {
                val url = LinkClassifier.extractUrl(link) ?: run { error = "Geçerli bir link yazın"; return@Button }
                onOpenLink(url)
                onClose()
            }, modifier = Modifier.fillMaxWidth()) { Text("TV'de aç") }
        }
        SettingsCard(null) { ModeSection(onModeChange) }
        Text("AfuRemote $versionName", color = RemoteColors.Muted, fontSize = 13.sp)
        Spacer(Modifier.padding(bottom = 24.dp))
    }
}

/** Ayarlar sayfasındaki yuvarlak köşeli koyu gri bölüm. */
@Composable
private fun SettingsCard(title: String?, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(RemoteColors.Pad).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (title != null) Text(title, color = RemoteColors.Muted, fontSize = 14.sp)
        content()
    }
}
