package com.afudm.afuremote.phone

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afudm.afuremote.ui.Glyph
import com.afudm.afuremote.ui.GlyphIcon
import com.afudm.afuremote.ui.RemoteColors

/** "televizyonlar" seçici: bulanık renkli zemin, yanıp sönen üç nokta, bulunan TV listesi. */
@Composable
fun DiscoveryScreen(
    devices: List<TvDevice>,
    selectedId: String?,
    scanning: Boolean,
    onSelect: (TvDevice) -> Unit,
    onRefresh: () -> Unit,
    onAddIp: () -> Unit,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color(0xFF232323))) {
        GlowBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                RoundTap(Glyph.CLOSE, "Kapat", onClose, size = 26)
            }
            Text("televizyonlar", color = RemoteColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, modifier = Modifier.padding(top = 36.dp))

            if (scanning || devices.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { PulseDots() }
            } else {
                Spacer(Modifier.height(28.dp))
            }

            devices.forEach { tv ->
                DeviceRow(tv, tv.id == selectedId) { onSelect(tv) }
                Spacer(Modifier.height(10.dp))
            }

            if (devices.isEmpty()) {
                Text(
                    "Lütfen televizyonunuzun açık olduğundan ve aynı Wi-Fi ağına bağlı olduğundan emin olun.",
                    color = RemoteColors.Muted, fontSize = 15.sp, lineHeight = 24.sp, letterSpacing = 0.4.sp
                )
                Text(
                    "Android TV Remote hizmeti olan TV'ler ve projeksiyonlar da burada görünür.",
                    color = RemoteColors.Muted.copy(alpha = .7f), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)
                )
            }

            Row(
                Modifier.padding(top = 24.dp).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = .08f))
                    .clickable(enabled = !scanning, onClick = onRefresh).padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlyphIcon(Glyph.REFRESH, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Text(if (scanning) "Aranıyor…" else "Yenile", color = RemoteColors.Text, fontSize = 15.sp)
            }
            TextButton(onClick = onAddIp, modifier = Modifier.padding(top = 4.dp)) { Text("TV'yi bulamadım → IP ile ekle", color = RemoteColors.Text) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun DeviceRow(tv: TvDevice, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (selected) .14f else .07f))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(Glyph.TV, size = 28.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(tv.name, color = RemoteColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(if (tv.model == tv.name) "Android TV" else tv.model, color = RemoteColors.Muted, fontSize = 13.sp)
        }
        Box(Modifier.size(10.dp).clip(CircleShape).background(RemoteColors.Online))
    }
}

@Composable
fun RoundTap(glyph: Glyph, description: String, onClick: () -> Unit, tint: Color = RemoteColors.Icon, size: Int = 30) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) { GlyphIcon(glyph, size = size.dp, color = tint) }
}

/** Arkadaki yumuşak renk lekeleri (bulanıklık radyal geçişle yapılır; her Android sürümünde çalışır). */
@Composable
private fun GlowBackground() {
    val t by rememberInfiniteTransition(label = "glow").animateFloat(
        0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Reverse), label = "glowT"
    )
    Canvas(Modifier.fillMaxSize()) {
        fun blob(color: Color, x: Float, y: Float, r: Float) =
            drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(x, y), radius = r), radius = r, center = Offset(x, y))
        val w = size.width
        blob(Color(0x55777766), w * (.3f + .05f * t), w * .15f, w * .45f)
        blob(Color(0x33505A80), w * (.8f - .05f * t), w * .12f, w * .4f)
        blob(Color(0x66A01C1C), w * (.35f + .04f * t), w * .58f, w * .38f)
        blob(Color(0x55285AA0), w * (.66f - .04f * t), w * .55f, w * .36f)
        blob(Color(0x331E7878), w * .95f, w * (.62f + .03f * t), w * .3f)
        blob(Color(0x22FFFFFF), w * .3f, size.height * .82f, w * .25f)
    }
}

@Composable
private fun PulseDots() {
    val t by rememberInfiniteTransition(label = "dots").animateFloat(
        0f, 3f, infiniteRepeatable(tween(1_200, easing = LinearEasing)), label = "dotsT"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val active = t.toInt() == i
            Box(Modifier.size(if (active) 12.dp else 4.dp).clip(CircleShape).background(Color.White.copy(alpha = if (active) .75f else .45f)))
        }
    }
}
