package com.afudm.afuremote.phone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.afudm.afuremote.classify.LinkClassifier
import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.ui.AfuTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ShareActivity : ComponentActivity() {
    private sealed interface Shared {
        data class Link(val url: String, val title: String) : Shared
        data class LocalVideo(val uri: Uri) : Shared
    }

    private val status = MutableStateFlow("TV aranıyor…")
    private val choices = MutableStateFlow<List<TvDevice>>(emptyList())
    private lateinit var graph: PhoneGraph.Graph
    private var discoveryAcquired = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        graph = PhoneGraph.get(this)
        setContent {
            AfuTheme {
                val text by status.collectAsState()
                val tvs by choices.collectAsState()
                Surface {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("AfuRemote")
                        Text(text)
                        tvs.forEach { tv ->
                            Button(onClick = { choices.value = emptyList(); lifecycleScope.launch { deliver(tv) } }) {
                                Text("${tv.name} · ${tv.model}")
                            }
                        }
                        OutlinedButton(onClick = { finish() }) { Text("Kapat") }
                    }
                }
            }
        }
        val shared = parse(intent)
        if (shared == null) {
            status.value = "Paylaşılan içerikte link ya da video yok"
            return
        }
        graph.discovery.acquire()
        discoveryAcquired = true
        lifecycleScope.launch {
            withTimeoutOrNull(8_000) { graph.discovery.devices.first { it.isNotEmpty() } }
            delay(1_000) // diğer TV'ler de gelsin
            val all = graph.discovery.devices.value
            when {
                all.isEmpty() -> status.value = Messages.NO_TV
                all.size == 1 -> deliver(all.first(), shared)
                else -> { status.value = "Hangi TV?"; choices.value = all; pending = shared }
            }
        }
    }

    private var pending: Shared? = null

    private suspend fun deliver(tv: TvDevice, shared: Shared? = pending) {
        val item = shared ?: return
        status.value = "${tv.name} TV'sine gönderiliyor…"
        val request = when (item) {
            is Shared.Link -> OpenRequest(item.url, item.title)
            is Shared.LocalVideo -> {
                val url = PhoneStream.publish(this, item.uri) ?: run { status.value = "Video okunamadı ya da Wi-Fi yok"; return }
                OpenRequest(url, "Telefondan video", forceMedia = true)
            }
        }
        when (val r = graph.controller.open(tv, request) { status.value = Messages.WAITING_TV }) {
            SendResult.Ok -> { status.value = "TV'de açıldı ✓"; delay(1_200); finish() }
            SendResult.Unauthorized -> status.value = Messages.DENIED
            is SendResult.Failed -> status.value = r.message
        }
    }

    private fun parse(intent: Intent?): Shared? {
        Log.i(TAG, "share intent action=${intent?.action} type=${intent?.type} extras=${intent?.extras?.keySet()}")
        if (intent?.action != Intent.ACTION_SEND) return null
        val type = intent.type.orEmpty()
        if (type.startsWith("video/")) {
            return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { Shared.LocalVideo(it) }
        }
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: return null
        val url = LinkClassifier.extractUrl(text) ?: run { Log.w(TAG, "share text present (${text.length} chars) but has no URL"); return null }
        Log.i(TAG, "share link extracted")
        return Shared.Link(url, intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty())
    }

    override fun onDestroy() {
        if (::graph.isInitialized && discoveryAcquired) graph.discovery.release()
        super.onDestroy()
    }

    private companion object { const val TAG = "AfuRemoteShare" }
}
