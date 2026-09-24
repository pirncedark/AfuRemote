package com.afudm.afuremote.phone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afudm.afuremote.protocol.RemoteKey
import com.afudm.afuremote.ui.Glyph
import com.afudm.afuremote.ui.GlyphIcon
import com.afudm.afuremote.ui.RemoteColors

/** TV'de açılacak hazır uygulamalar (Android TV paket adları). */
private data class TvApp(val label: String, val pkg: String, val bg: Brush, val mark: String, val markColor: Color)

private val APPS = listOf(
    TvApp("Netflix", "com.netflix.ninja", Brush.linearGradient(listOf(Color(0xFF141414), Color(0xFF1C1C1C))), "N", Color(0xFFE50914)),
    TvApp("YouTube", "com.google.android.youtube.tv", Brush.linearGradient(listOf(Color(0xFFFF0000), Color(0xFFE00000))), "▶", Color.White),
    TvApp("Prime Video", "com.amazon.amazonvideo.livingroom", Brush.linearGradient(listOf(Color(0xFF1A98FF), Color(0xFF0F79E0))), "prime\nvideo", Color.White),
    TvApp("Disney+", "com.disney.disneyplus", Brush.linearGradient(listOf(Color(0xFF0B3A5B), Color(0xFF1FA5A5))), "Disney+", Color.White),
)

@Composable
fun RemoteScreen(
    tv: TvDevice,
    online: Boolean,
    status: String,
    onKey: (RemoteKey) -> Unit,
    onLaunch: (String) -> Unit,
    onText: () -> Unit,
    onLink: () -> Unit,
    onPickTv: () -> Unit,
    onSettings: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val key: (RemoteKey) -> Unit = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onKey(it) }
    Column(
        Modifier.fillMaxSize().background(RemoteColors.Background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Üst çubuk: ayarlar · TV adı ▾ · güç
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundTap(Glyph.GEAR, "Ayarlar", onSettings, size = 34)
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable(onClick = onPickTv).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (online) RemoteColors.Online else RemoteColors.Offline))
                Spacer(Modifier.width(10.dp))
                Text(tv.name, color = RemoteColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(10.dp))
                GlyphIcon(Glyph.CARET, size = 18.dp)
            }
            RoundTap(Glyph.POWER, "Güç", { key(RemoteKey.POWER) }, tint = RemoteColors.Power, size = 32)
        }

        if (status.isNotBlank()) {
            Text(
                status, color = RemoteColors.Text, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .08f)).padding(12.dp)
            )
        }

        // Uygulama kısayolları
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            APPS.forEach { app -> AppTile(app) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onLaunch(app.pkg) } }
        }

        // Kaydırılabilir orta alan: 1) yön tuşları 2) oynatma + link
        val pager = rememberPagerState { 2 }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val pad = minOf(maxWidth - 64.dp, 320.dp)
            HorizontalPager(pager, Modifier.fillMaxWidth().height(pad + 16.dp)) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (page == 0) DPad(pad, key) else MediaPage(pad, key, onLink)
                }
            }
        }
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { i -> Box(Modifier.size(9.dp).clip(CircleShape).background(if (pager.currentPage == i) Color(0xFFDDDDDD) else Color(0xFF555555))) }
        }

        // Alt blok: VOL hapı · 3×2 tuş · SAR hapı
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Pill("VOL", Glyph.PLUS, Glyph.MINUS, "Sesi aç", "Sesi kıs", { key(RemoteKey.VOL_UP) }, { key(RemoteKey.VOL_DOWN) })
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircleKey(Glyph.KEYBOARD, "Klavye", onClick = onText)
                    CircleKey(Glyph.HOME, "Ana ekran") { key(RemoteKey.HOME) }
                    CircleKey(Glyph.PLAY_PAUSE, "Oynat / duraklat") { key(RemoteKey.PLAY_PAUSE) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircleKey(Glyph.MUTE, "Sessiz") { key(RemoteKey.MUTE) }
                    CircleKey(Glyph.LINK, "Link gönder", onClick = onLink)
                    CircleKey(Glyph.BACK, "Geri") { key(RemoteKey.BACK) }
                }
            }
            Pill("10 SN", Glyph.FORWARD, Glyph.REWIND, "10 sn ileri", "10 sn geri", { key(RemoteKey.SEEK_FWD) }, { key(RemoteKey.SEEK_BACK) }, iconSize = 22)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AppTile(app: TvApp, onClick: () -> Unit) {
    Column(Modifier.width(80.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).semantics { contentDescription = app.label }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(app.bg), contentAlignment = Alignment.Center) {
            val big = app.mark.length <= 1
            Text(
                app.mark, color = app.markColor, textAlign = TextAlign.Center, fontWeight = FontWeight.Black,
                fontSize = if (big) 38.sp else 15.sp, lineHeight = if (big) 40.sp else 16.sp
            )
        }
        Text(app.label, color = RemoteColors.Text, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun DPad(size: Dp, key: (RemoteKey) -> Unit) {
    val ring = Brush.sweepGradient(listOf(RemoteColors.RingStart, RemoteColors.RingEnd, RemoteColors.RingStart))
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF303030), RemoteColors.Pad)), radius = this.size.minDimension / 2)
            drawCircle(ring, radius = this.size.minDimension / 2 - 1.5.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        }
        val edge = size / 3
        Box(Modifier.align(Alignment.TopCenter).size(edge).clip(CircleShape).clickable { key(RemoteKey.DPAD_UP) }.semantics { contentDescription = "Yukarı" }, contentAlignment = Alignment.Center) { GlyphIcon(Glyph.UP, size = 30.dp) }
        Box(Modifier.align(Alignment.BottomCenter).size(edge).clip(CircleShape).clickable { key(RemoteKey.DPAD_DOWN) }.semantics { contentDescription = "Aşağı" }, contentAlignment = Alignment.Center) { GlyphIcon(Glyph.DOWN, size = 30.dp) }
        Box(Modifier.align(Alignment.CenterStart).size(edge).clip(CircleShape).clickable { key(RemoteKey.DPAD_LEFT) }.semantics { contentDescription = "Sol" }, contentAlignment = Alignment.Center) { GlyphIcon(Glyph.LEFT, size = 30.dp) }
        Box(Modifier.align(Alignment.CenterEnd).size(edge).clip(CircleShape).clickable { key(RemoteKey.DPAD_RIGHT) }.semantics { contentDescription = "Sağ" }, contentAlignment = Alignment.Center) { GlyphIcon(Glyph.RIGHT, size = 30.dp) }
        val ok = size * 0.3f
        Box(
            Modifier.size(ok).clip(CircleShape).background(Color(0xFF353535)).border(2.dp, ring, CircleShape)
                .clickable { key(RemoteKey.DPAD_CENTER) }.semantics { contentDescription = "Tamam" }
        )
    }
}

@Composable
private fun MediaPage(size: Dp, key: (RemoteKey) -> Unit, onLink: () -> Unit) {
    Column(Modifier.size(size).clip(CircleShape).background(RemoteColors.Pad), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Oynatma", color = RemoteColors.Muted, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleKey(Glyph.REWIND, "10 sn geri") { key(RemoteKey.SEEK_BACK) }
            CircleKey(Glyph.PLAY_PAUSE, "Oynat / duraklat", big = true) { key(RemoteKey.PLAY_PAUSE) }
            CircleKey(Glyph.FORWARD, "10 sn ileri") { key(RemoteKey.SEEK_FWD) }
        }
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.clip(RoundedCornerShape(22.dp)).background(RemoteColors.Button).clickable(onClick = onLink).padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphIcon(Glyph.LINK, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text("Link gönder", color = RemoteColors.Text, fontSize = 14.sp)
        }
    }
}

@Composable
private fun CircleKey(glyph: Glyph, description: String, big: Boolean = false, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val d = if (big) 72.dp else 58.dp
    Box(
        Modifier.size(d).clip(CircleShape).background(if (pressed) RemoteColors.ButtonPressed else RemoteColors.Button)
            .clickable(interactionSource = source, indication = null, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) { GlyphIcon(glyph, size = if (big) 32.dp else 28.dp) }
}

@Composable
private fun Pill(label: String, top: Glyph, bottom: Glyph, topDesc: String, bottomDesc: String, onTop: () -> Unit, onBottom: () -> Unit, iconSize: Int = 26) {
    Column(
        Modifier.width(60.dp).height(130.dp).clip(RoundedCornerShape(30.dp)).background(RemoteColors.Button),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).clickable(onClick = onTop).semantics { contentDescription = topDesc }, contentAlignment = Alignment.Center) { GlyphIcon(top, size = iconSize.dp) }
        Text(label, color = RemoteColors.Text, fontSize = 14.sp, letterSpacing = 1.sp)
        Box(Modifier.fillMaxWidth().weight(1f).clickable(onClick = onBottom).semantics { contentDescription = bottomDesc }, contentAlignment = Alignment.Center) { GlyphIcon(bottom, size = iconSize.dp) }
    }
}

/** Klavye / link penceresi için ortak metin alanı. */
@Composable
fun SendField(label: String, value: String, onChange: (String) -> Unit, onSend: () -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { onSend() }),
        modifier = Modifier.fillMaxWidth()
    )
}
