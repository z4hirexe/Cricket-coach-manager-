package com.cricketcoach.manager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cricketcoach.manager.game.Club
import kotlin.math.cos
import kotlin.math.sin

/** The 12 cricket nations' flags, drawn in code (public national symbols, simplified for small sizes). */
object Flags {
    val CODES = setOf("IND", "AUS", "ENG", "PAK", "SA", "NZ", "BAN", "SL", "AFG", "WI", "IRE", "ZIM")
    fun has(c: Club?): Boolean = c != null && c.kind == "NATION" && c.short in CODES || c != null && c.id.startsWith("N_") && c.short in CODES
}

private fun DrawScope.star(cx: Float, cy: Float, r: Float, color: Color, points: Int = 5, inner: Float = 0.45f, rot: Float = -90f) {
    val p = Path()
    for (i in 0 until points * 2) {
        val rr = if (i % 2 == 0) r else r * inner
        val a = Math.toRadians((rot + i * 180.0 / points))
        val x = cx + rr * cos(a).toFloat(); val y = cy + rr * sin(a).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close(); drawPath(p, color)
}

/** Simplified Union Jack in the rectangle (0,0)-(w,h). */
private fun DrawScope.unionJack(w: Float, h: Float) {
    val navy = Color(0xFF012169); val red = Color(0xFFC8102E)
    drawRect(navy, Offset.Zero, Size(w, h))
    drawLine(Color.White, Offset(0f, 0f), Offset(w, h), h * 0.2f); drawLine(Color.White, Offset(w, 0f), Offset(0f, h), h * 0.2f)
    drawLine(red, Offset(0f, 0f), Offset(w, h), h * 0.07f); drawLine(red, Offset(w, 0f), Offset(0f, h), h * 0.07f)
    drawRect(Color.White, Offset(w * 0.42f, 0f), Size(w * 0.16f, h)); drawRect(Color.White, Offset(0f, h * 0.36f), Size(w, h * 0.28f))
    drawRect(red, Offset(w * 0.455f, 0f), Size(w * 0.09f, h)); drawRect(red, Offset(0f, h * 0.41f), Size(w, h * 0.18f))
}

@Composable
fun NationFlag(code: String, width: Dp, height: Dp = width * 0.62f, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width, height).clip(RoundedCornerShape(3.dp))) {
        val w = size.width; val h = size.height
        when (code) {
            "IND" -> {
                drawRect(Color(0xFFFF9933), Offset.Zero, Size(w, h / 3)); drawRect(Color.White, Offset(0f, h / 3), Size(w, h / 3)); drawRect(Color(0xFF138808), Offset(0f, 2 * h / 3), Size(w, h / 3))
                val r = h * 0.14f; val c = Offset(w / 2, h / 2); val navy = Color(0xFF000080)
                drawCircle(navy, r, c, style = Stroke(r * 0.16f))
                for (i in 0 until 24) { val a = Math.toRadians(i * 15.0); drawLine(navy, c, Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()), r * 0.06f) }
                drawCircle(navy, r * 0.18f, c)
            }
            "AUS" -> {
                drawRect(Color(0xFF012169), Offset.Zero, Size(w, h))
                unionJack(w / 2, h / 2)
                star(w * 0.25f, h * 0.75f, h * 0.14f, Color.White, 7, 0.5f)
                star(w * 0.75f, h * 0.83f, h * 0.07f, Color.White, 7, 0.5f); star(w * 0.62f, h * 0.45f, h * 0.07f, Color.White, 7, 0.5f)
                star(w * 0.75f, h * 0.2f, h * 0.07f, Color.White, 7, 0.5f); star(w * 0.87f, h * 0.4f, h * 0.07f, Color.White, 7, 0.5f)
                star(w * 0.81f, h * 0.55f, h * 0.04f, Color.White, 5, 0.5f)
            }
            "ENG" -> { drawRect(Color.White, Offset.Zero, Size(w, h)); val red = Color(0xFFCE1124); drawRect(red, Offset(w * 0.43f, 0f), Size(w * 0.14f, h)); drawRect(red, Offset(0f, h * 0.39f), Size(w, h * 0.22f)) }
            "PAK" -> {
                val g = Color(0xFF01411C)
                drawRect(Color.White, Offset.Zero, Size(w * 0.25f, h)); drawRect(g, Offset(w * 0.25f, 0f), Size(w * 0.75f, h))
                val c = Offset(w * 0.62f, h * 0.5f); drawCircle(Color.White, h * 0.3f, c); drawCircle(g, h * 0.26f, Offset(c.x + h * 0.09f, c.y - h * 0.06f))
                star(c.x + h * 0.14f, c.y - h * 0.1f, h * 0.1f, Color.White, 5, 0.42f, -60f)
            }
            "SA" -> {
                drawRect(Color(0xFFE03C31), Offset.Zero, Size(w, h / 2)); drawRect(Color(0xFF001489), Offset(0f, h / 2), Size(w, h / 2))
                val y = Path().apply { moveTo(0f, 0f); lineTo(w * 0.42f, h * 0.5f); lineTo(0f, h); }
                drawPath(y, Color.White, style = Stroke(h * 0.36f))
                drawRect(Color.White, Offset(w * 0.4f, h * 0.32f), Size(w * 0.6f, h * 0.36f))
                drawPath(y, Color(0xFF007749), style = Stroke(h * 0.2f))
                drawRect(Color(0xFF007749), Offset(w * 0.4f, h * 0.4f), Size(w * 0.6f, h * 0.2f))
                val tri = Path().apply { moveTo(0f, h * 0.16f); lineTo(w * 0.3f, h * 0.5f); lineTo(0f, h * 0.84f); close() }
                drawPath(tri, Color(0xFFFFB612)); val tri2 = Path().apply { moveTo(0f, h * 0.24f); lineTo(w * 0.24f, h * 0.5f); lineTo(0f, h * 0.76f); close() }
                drawPath(tri2, Color.Black)
            }
            "NZ" -> {
                drawRect(Color(0xFF012169), Offset.Zero, Size(w, h)); unionJack(w / 2, h / 2)
                for ((x, yy, r) in listOf(Triple(0.75f, 0.8f, 0.09f), Triple(0.64f, 0.45f, 0.075f), Triple(0.75f, 0.2f, 0.07f), Triple(0.86f, 0.4f, 0.075f))) {
                    star(w * x, h * yy, h * r * 1.3f, Color.White); star(w * x, h * yy, h * r, Color(0xFFC8102E))
                }
            }
            "BAN" -> { drawRect(Color(0xFF006A4E), Offset.Zero, Size(w, h)); drawCircle(Color(0xFFF42A41), h * 0.3f, Offset(w * 0.45f, h / 2)) }
            "SL" -> {
                drawRect(Color(0xFFFFBE29), Offset.Zero, Size(w, h))
                val m = h * 0.06f
                drawRect(Color(0xFF00534E), Offset(m, m), Size(w * 0.13f, h - 2 * m)); drawRect(Color(0xFFEB7400), Offset(m + w * 0.13f, m), Size(w * 0.13f, h - 2 * m))
                val px = m * 2 + w * 0.26f; val pw = w - px - m
                drawRect(Color(0xFF8D153A), Offset(px, m), Size(pw, h - 2 * m))
                // stylised lion holding a sword + 4 bo leaves
                val gold = Color(0xFFFFBE29); val cx = px + pw / 2; val cy = h / 2
                drawRoundRect(gold, Offset(cx - pw * 0.22f, cy - h * 0.08f), Size(pw * 0.44f, h * 0.22f), androidx.compose.ui.geometry.CornerRadius(h * 0.06f))
                drawCircle(gold, h * 0.1f, Offset(cx - pw * 0.18f, cy - h * 0.12f))
                drawLine(gold, Offset(cx - pw * 0.1f, cy - h * 0.3f), Offset(cx - pw * 0.05f, cy + h * 0.02f), h * 0.04f)
                for (lx in listOf(px + pw * 0.1f, px + pw * 0.9f)) for (ly in listOf(m + h * 0.1f, h - m - h * 0.1f)) drawCircle(gold, h * 0.05f, Offset(lx, ly))
            }
            "AFG" -> {
                drawRect(Color.Black, Offset.Zero, Size(w / 3, h)); drawRect(Color(0xFFD32011), Offset(w / 3, 0f), Size(w / 3, h)); drawRect(Color(0xFF007A36), Offset(2 * w / 3, 0f), Size(w / 3, h))
                drawCircle(Color.White, h * 0.2f, Offset(w / 2, h / 2), style = Stroke(h * 0.04f)); drawRect(Color.White, Offset(w / 2 - h * 0.05f, h / 2 - h * 0.08f), Size(h * 0.1f, h * 0.14f))
            }
            "WI" -> { // Cricket West Indies: maroon, sun, island with palm
                drawRect(Color(0xFF7B1C2E), Offset.Zero, Size(w, h))
                drawCircle(Color(0xFFFFC72C), h * 0.24f, Offset(w * 0.5f, h * 0.46f))
                val isl = Path().apply { moveTo(w * 0.2f, h * 0.82f); quadraticBezierTo(w * 0.5f, h * 0.55f, w * 0.8f, h * 0.82f); close() }
                drawPath(isl, Color(0xFF2E8B57))
                drawLine(Color(0xFF5D4037), Offset(w * 0.52f, h * 0.74f), Offset(w * 0.46f, h * 0.3f), h * 0.04f)
                for (a in listOf(-160f, -120f, -60f, -20f)) { val r = Math.toRadians(a.toDouble()); drawLine(Color(0xFF2E8B57), Offset(w * 0.46f, h * 0.3f), Offset(w * 0.46f + h * 0.2f * cos(r).toFloat(), h * 0.3f + h * 0.16f * sin(r).toFloat() + h * 0.08f), h * 0.05f) }
            }
            "IRE" -> { // Cricket Ireland: green with a shamrock
                drawRect(Color(0xFF169B62), Offset.Zero, Size(w, h))
                val c = Offset(w / 2, h * 0.45f); val r = h * 0.12f
                drawCircle(Color.White, r, Offset(c.x, c.y - r)); drawCircle(Color.White, r, Offset(c.x - r, c.y + r * 0.2f)); drawCircle(Color.White, r, Offset(c.x + r, c.y + r * 0.2f))
                drawLine(Color.White, Offset(c.x, c.y + r * 0.3f), Offset(c.x + r * 0.5f, c.y + r * 2.6f), h * 0.04f)
            }
            "ZIM" -> {
                val cols = listOf(Color(0xFF006400), Color(0xFFFFD200), Color(0xFFD40000), Color.Black, Color(0xFFD40000), Color(0xFFFFD200), Color(0xFF006400))
                cols.forEachIndexed { i, c -> drawRect(c, Offset(0f, h * i / 7f), Size(w, h / 7f + 1f)) }
                val tri = Path().apply { moveTo(0f, 0f); lineTo(w * 0.42f, h / 2); lineTo(0f, h); close() }
                drawPath(tri, Color.White); drawPath(tri, Color.Black, style = Stroke(h * 0.03f))
                star(w * 0.13f, h / 2, h * 0.13f, Color(0xFFD40000))
            }
            else -> drawRect(Color.Gray, Offset.Zero, Size(w, h))
        }
    }
}

/** Team identity as a flag-shaped tile: real national flag for the 12 nations, otherwise club colours + badge. */
@Composable
fun TeamFlag(c: Club?, width: Dp, height: Dp = width * 0.62f) {
    val shape = RoundedCornerShape(4.dp)
    Box(Modifier.size(width, height).clip(shape).border(1.dp, Color(0x55FFFFFF), shape)) {
        if (c != null && Flags.has(c)) NationFlag(c.short, width, height)
        else {
            val p = Color(c?.primary ?: 0xFF333333); val s = Color(c?.secondary ?: 0xFF777777)
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(p, p, s.copy(alpha = 0.9f)))), contentAlignment = Alignment.Center) {
                ClubBadge(c, height * 0.82f)
            }
        }
    }
}

/** Small custom glyphs for the sidebar (material-icons-core has no cricket/trophy/chart icons). */
@Composable
fun Glyph(kind: String, tint: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height; val sw = w * 0.1f
        when (kind) {
            "trophy" -> {
                val cup = Path().apply { moveTo(w * 0.25f, h * 0.12f); lineTo(w * 0.75f, h * 0.12f); lineTo(w * 0.7f, h * 0.45f); quadraticBezierTo(w * 0.5f, h * 0.65f, w * 0.3f, h * 0.45f); close() }
                drawPath(cup, tint)
                drawArc(tint, 90f, 180f, false, Offset(w * 0.08f, h * 0.16f), Size(w * 0.3f, h * 0.26f), style = Stroke(sw * 0.8f))
                drawArc(tint, -90f, 180f, false, Offset(w * 0.62f, h * 0.16f), Size(w * 0.3f, h * 0.26f), style = Stroke(sw * 0.8f))
                drawRect(tint, Offset(w * 0.45f, h * 0.58f), Size(w * 0.1f, h * 0.2f)); drawRect(tint, Offset(w * 0.3f, h * 0.78f), Size(w * 0.4f, h * 0.1f))
            }
            "chart" -> { for ((i, v) in listOf(0.45f, 0.7f, 0.35f, 0.9f).withIndex()) drawRect(tint, Offset(w * (0.1f + i * 0.22f), h * (0.92f - v)), Size(w * 0.15f, h * v)) }
            "bat" -> {
                rotate(-40f) {
                    drawRoundRect(tint, Offset(w * 0.4f, h * 0.05f), Size(w * 0.2f, h * 0.62f), androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
                    drawRect(tint, Offset(w * 0.46f, h * 0.64f), Size(w * 0.08f, h * 0.3f))
                }
                drawCircle(tint, w * 0.11f, Offset(w * 0.78f, h * 0.78f))
            }
            "gavel" -> {
                rotate(-45f) { drawRoundRect(tint, Offset(w * 0.22f, h * 0.1f), Size(w * 0.56f, h * 0.24f), androidx.compose.ui.geometry.CornerRadius(w * 0.05f)); drawRect(tint, Offset(w * 0.45f, h * 0.3f), Size(w * 0.1f, h * 0.55f)) }
                drawRect(tint, Offset(w * 0.5f, h * 0.84f), Size(w * 0.45f, h * 0.1f))
            }
            "coins" -> { for (i in 0..2) drawOval(tint, Offset(w * 0.15f, h * (0.62f - i * 0.2f)), Size(w * 0.7f, h * 0.26f)); drawOval(Color.Black.copy(alpha = 0.35f), Offset(w * 0.3f, h * 0.26f), Size(w * 0.4f, h * 0.12f)) }
            "stadium" -> { drawOval(tint, Offset(w * 0.05f, h * 0.2f), Size(w * 0.9f, h * 0.6f), style = Stroke(sw)); drawOval(tint, Offset(w * 0.3f, h * 0.38f), Size(w * 0.4f, h * 0.24f)) }
            "inbox" -> { drawRoundRect(tint, Offset(w * 0.08f, h * 0.2f), Size(w * 0.84f, h * 0.6f), androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = Stroke(sw)); val v = Path().apply { moveTo(w * 0.1f, h * 0.24f); lineTo(w * 0.5f, h * 0.55f); lineTo(w * 0.9f, h * 0.24f) }; drawPath(v, tint, style = Stroke(sw)) }
            "squad" -> { drawCircle(tint, w * 0.14f, Offset(w * 0.35f, h * 0.32f)); drawCircle(tint, w * 0.12f, Offset(w * 0.7f, h * 0.36f)); drawArc(tint, 180f, 180f, true, Offset(w * 0.1f, h * 0.52f), Size(w * 0.5f, h * 0.5f)); drawArc(tint, 180f, 180f, true, Offset(w * 0.5f, h * 0.58f), Size(w * 0.4f, h * 0.4f)) }
            "calendar" -> { drawRoundRect(tint, Offset(w * 0.1f, h * 0.18f), Size(w * 0.8f, h * 0.72f), androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = Stroke(sw)); drawRect(tint, Offset(w * 0.1f, h * 0.18f), Size(w * 0.8f, h * 0.18f)); for (i in 0..2) for (j in 0..1) drawRect(tint, Offset(w * (0.22f + i * 0.21f), h * (0.48f + j * 0.18f)), Size(w * 0.12f, h * 0.1f)) }
            "table" -> { for (i in 0..3) drawRect(tint, Offset(w * 0.1f, h * (0.15f + i * 0.2f)), Size(w * 0.8f, h * 0.1f)); drawRect(tint.copy(alpha = 0.5f), Offset(w * 0.1f, h * 0.15f), Size(w * 0.18f, h * 0.7f)) }
            "social" -> { drawRoundRect(tint, Offset(w * 0.1f, h * 0.1f), Size(w * 0.8f, h * 0.8f), androidx.compose.ui.geometry.CornerRadius(w * 0.24f), style = Stroke(sw)); drawCircle(tint, w * 0.17f, Offset(w * 0.5f, h * 0.5f), style = Stroke(sw)); drawCircle(tint, w * 0.05f, Offset(w * 0.72f, h * 0.28f)) }
            "search" -> { drawCircle(tint, w * 0.25f, Offset(w * 0.42f, h * 0.42f), style = Stroke(sw)); drawLine(tint, Offset(w * 0.6f, h * 0.6f), Offset(w * 0.88f, h * 0.88f), sw * 1.4f) }
            "xi" -> { drawOval(tint, Offset(w * 0.08f, h * 0.08f), Size(w * 0.84f, h * 0.84f), style = Stroke(sw)); for ((x, y) in listOf(0.5f to 0.25f, 0.3f to 0.45f, 0.7f to 0.45f, 0.38f to 0.7f, 0.62f to 0.7f)) drawCircle(tint, w * 0.06f, Offset(w * x, h * y)); drawRect(tint, Offset(w * 0.46f, h * 0.38f), Size(w * 0.08f, h * 0.24f)) }
            "exit" -> { drawRect(tint, Offset(w * 0.12f, h * 0.12f), Size(w * 0.45f, h * 0.76f), style = Stroke(sw)); drawLine(tint, Offset(w * 0.4f, h * 0.5f), Offset(w * 0.92f, h * 0.5f), sw); drawLine(tint, Offset(w * 0.75f, h * 0.33f), Offset(w * 0.92f, h * 0.5f), sw); drawLine(tint, Offset(w * 0.75f, h * 0.67f), Offset(w * 0.92f, h * 0.5f), sw) }
            "sun" -> { drawCircle(tint, w * 0.2f, Offset(w / 2, h / 2)); for (i in 0 until 8) { val a = Math.toRadians(i * 45.0); drawLine(tint, Offset(w / 2 + w * 0.3f * cos(a).toFloat(), h / 2 + h * 0.3f * sin(a).toFloat()), Offset(w / 2 + w * 0.44f * cos(a).toFloat(), h / 2 + h * 0.44f * sin(a).toFloat()), sw * 0.8f) } }
            "cloud" -> { drawCircle(tint, w * 0.18f, Offset(w * 0.35f, h * 0.55f)); drawCircle(tint, w * 0.24f, Offset(w * 0.55f, h * 0.45f)); drawCircle(tint, w * 0.16f, Offset(w * 0.75f, h * 0.6f)); drawRect(tint, Offset(w * 0.35f, h * 0.55f), Size(w * 0.4f, h * 0.21f)) }
            else -> drawCircle(tint, w * 0.3f, Offset(w / 2, h / 2))
        }
    }
}
