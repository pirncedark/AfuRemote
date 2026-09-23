package com.afudm.afuremote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Kumanda ikonları: ek ikon kütüphanesi olmadan Canvas ile çizilir. */
enum class Glyph { UP, DOWN, LEFT, RIGHT, PLUS, MINUS, POWER, HOME, BACK, KEYBOARD, MUTE, PLAY_PAUSE, REWIND, FORWARD, GEAR, LINK, CLOSE, REFRESH, CARET, TV }

@Composable
fun GlyphIcon(glyph: Glyph, modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = RemoteColors.Icon) {
    Canvas(modifier.size(size)) { draw(glyph, color) }
}

private fun DrawScope.draw(glyph: Glyph, color: Color) {
    val w = size.width
    val s = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(vararg pts: Float) = Path().apply { moveTo(pts[0] * w, pts[1] * w); for (i in 2 until pts.size step 2) lineTo(pts[i] * w, pts[i + 1] * w) }
    fun line(vararg pts: Float) = drawPath(p(*pts), color, style = s)
    when (glyph) {
        Glyph.UP -> line(.2f, .65f, .5f, .35f, .8f, .65f)
        Glyph.DOWN -> line(.2f, .35f, .5f, .65f, .8f, .35f)
        Glyph.LEFT -> line(.62f, .18f, .32f, .5f, .62f, .82f)
        Glyph.RIGHT -> line(.38f, .18f, .68f, .5f, .38f, .82f)
        Glyph.PLUS -> { line(.5f, .15f, .5f, .85f); line(.15f, .5f, .85f, .5f) }
        Glyph.MINUS -> line(.15f, .5f, .85f, .5f)
        Glyph.CLOSE -> { line(.2f, .2f, .8f, .8f); line(.8f, .2f, .2f, .8f) }
        Glyph.POWER -> {
            drawArc(color, startAngle = -50f, sweepAngle = 280f, useCenter = false, topLeft = Offset(w * .15f, w * .17f), size = Size(w * .7f, w * .7f), style = s)
            line(.5f, .08f, .5f, .45f)
        }
        Glyph.HOME -> {
            drawPath(p(.18f, .45f, .5f, .15f, .82f, .45f, .82f, .85f, .18f, .85f).apply { close() }, color, style = s)
            line(.5f, .68f, .5f, .8f)
        }
        Glyph.BACK -> {
            drawArc(color, startAngle = -90f, sweepAngle = 180f, useCenter = false, topLeft = Offset(w * .3f, w * .3f), size = Size(w * .5f, w * .5f), style = s)
            line(.55f, .3f, .2f, .3f); line(.32f, .18f, .2f, .3f, .32f, .42f)
            line(.55f, .8f, .3f, .8f)
        }
        Glyph.KEYBOARD -> {
            drawRoundRect(color, topLeft = Offset(w * .08f, w * .25f), size = Size(w * .84f, w * .5f), cornerRadius = CornerRadius(w * .08f))
            val key = Color(0xFF3A3A3A)
            for (row in 0..1) for (col in 0..5) drawRect(key, Offset(w * (.16f + col * .115f), w * (.33f + row * .12f)), Size(w * .07f, w * .07f))
            drawRect(key, Offset(w * .3f, w * .6f), Size(w * .4f, w * .06f))
        }
        Glyph.MUTE -> {
            drawPath(p(.12f, .38f, .3f, .38f, .52f, .2f, .52f, .8f, .3f, .62f, .12f, .62f).apply { close() }, color, style = s)
            line(.66f, .38f, .88f, .62f); line(.88f, .38f, .66f, .62f)
        }
        Glyph.PLAY_PAUSE -> {
            drawPath(p(.1f, .25f, .45f, .5f, .1f, .75f).apply { close() }, color)
            drawRect(color, Offset(w * .58f, w * .25f), Size(w * .1f, w * .5f))
            drawRect(color, Offset(w * .76f, w * .25f), Size(w * .1f, w * .5f))
        }
        Glyph.REWIND -> {
            drawPath(p(.48f, .25f, .12f, .5f, .48f, .75f).apply { close() }, color)
            drawPath(p(.88f, .25f, .52f, .5f, .88f, .75f).apply { close() }, color)
        }
        Glyph.FORWARD -> {
            drawPath(p(.12f, .25f, .48f, .5f, .12f, .75f).apply { close() }, color)
            drawPath(p(.52f, .25f, .88f, .5f, .52f, .75f).apply { close() }, color)
        }
        Glyph.GEAR -> {
            for (i in 0 until 8) rotate(i * 45f) {
                drawRoundRect(color, topLeft = Offset(w * .42f, w * .04f), size = Size(w * .16f, w * .2f), cornerRadius = CornerRadius(w * .03f))
            }
            drawCircle(color, radius = w * .32f)
            drawCircle(RemoteColors.Background, radius = w * .13f)
        }
        Glyph.LINK -> rotate(-45f) {
            drawRoundRect(color, topLeft = Offset(w * .08f, w * .36f), size = Size(w * .46f, w * .28f), cornerRadius = CornerRadius(w * .14f), style = s)
            drawRoundRect(color, topLeft = Offset(w * .46f, w * .36f), size = Size(w * .46f, w * .28f), cornerRadius = CornerRadius(w * .14f), style = s)
        }
        Glyph.REFRESH -> {
            drawArc(color, startAngle = -60f, sweepAngle = 300f, useCenter = false, topLeft = Offset(w * .18f, w * .18f), size = Size(w * .64f, w * .64f), style = s)
            drawPath(p(.62f, .08f, .8f, .24f, .56f, .3f).apply { close() }, color)
        }
        Glyph.CARET -> drawPath(p(.2f, .35f, .8f, .35f, .5f, .7f).apply { close() }, color)
        Glyph.TV -> {
            drawRoundRect(color, topLeft = Offset(w * .08f, w * .18f), size = Size(w * .84f, w * .56f), cornerRadius = CornerRadius(w * .08f), style = s)
            line(.35f, .88f, .65f, .88f)
        }
    }
}
