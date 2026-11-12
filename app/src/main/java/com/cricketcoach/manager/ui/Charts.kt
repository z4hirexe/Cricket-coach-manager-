package com.cricketcoach.manager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.game.BallEv
import kotlin.math.*

private fun runColor(e: BallEv) = when (e.kind) { "SIX" -> C.Gold; "FOUR" -> C.Green; "WICKET" -> C.Red; else -> when (e.bat) { 1 -> Color(0xFFB0BEC5); 2 -> C.Blue; else -> Color(0xFFCE93D8) } }

private fun android.graphics.Canvas.label(text: String, x: Float, y: Float, color: Int, size: Float, center: Boolean = true) {
    val p = android.graphics.Paint().apply { this.color = color; textSize = size; isAntiAlias = true; textAlign = if (center) android.graphics.Paint.Align.CENTER else android.graphics.Paint.Align.LEFT }
    drawText(text, x, y, p)
}

/** Wagon wheel of every scoring shot (and dismissals) for the given deliveries. */
@Composable
fun WagonWheel(events: List<BallEv>, modifier: Modifier = Modifier) {
    val scoring = events.filter { (it.bat > 0 && it.kind != "WIDE") || it.kind == "WICKET" }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val r = min(size.width, size.height) / 2f * 0.92f; val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color(0xFF2E7D32), r, c); drawCircle(Color(0xFF388E3C), r * 0.45f, c)
            drawCircle(Color.White.copy(alpha = 0.7f), r, c, style = Stroke(2f)); drawCircle(Color.White.copy(alpha = 0.35f), r * 0.45f, c, style = Stroke(1.5f))
            drawRect(Color(0xFFCDB582), Offset(c.x - r * 0.04f, c.y - r * 0.32f), Size(r * 0.08f, r * 0.32f))
            val bat = Offset(c.x, c.y)
            for (e in scoring) {
                val a = Math.toRadians(e.angle.toDouble()); val d = min(1f, e.dist) * r * 0.98f
                // angle 0 = straight past the bowler (up on screen), off side to the right
                val end = Offset(bat.x + (sin(a) * d).toFloat(), bat.y - (cos(a) * d).toFloat())
                drawLine(runColor(e), bat, end, if (e.kind == "SIX" || e.kind == "FOUR") 3f else 2f, StrokeCap.Round)
                if (e.kind == "WICKET") drawCircle(C.Red, 5f, end)
            }
            drawContext.canvas.nativeCanvas.apply {
                label("OFF", c.x + r * 0.75f, c.y + 6f, 0xAAFFFFFF.toInt(), 26f); label("LEG", c.x - r * 0.75f, c.y + 6f, 0xAAFFFFFF.toInt(), 26f)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            for ((l, col) in listOf("1s" to Color(0xFFB0BEC5), "2s" to C.Blue, "3s" to Color(0xFFCE93D8), "4s" to C.Green, "6s" to C.Gold, "Out" to C.Red)) {
                Text("● $l  ", color = col, fontSize = 11.sp)
            }
        }
    }
}

/** Manhattan: runs per over (bars) with wicket markers, both innings side by side colours. */
@Composable
fun Manhattan(events: List<BallEv>, innNames: List<String>, maxOvers: Int, modifier: Modifier = Modifier) {
    val cols = listOf(C.Green, C.Gold, C.Blue, Color(0xFFFF7043))
    val byInn = events.groupBy { it.inn }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val overs = max(maxOvers, (events.maxOfOrNull { it.over } ?: 0) + 1).coerceAtLeast(5)
            val per = byInn.mapValues { (_, l) -> l.groupBy { it.over }.mapValues { (_, b) -> b.sumOf { it.runs } to b.count { it.kind == "WICKET" } } }
            val maxR = max(12, per.values.flatMap { it.values }.maxOfOrNull { it.first } ?: 12)
            val left = 34f; val bottom = size.height - 24f; val h = bottom - 8f; val w = size.width - left - 6f
            val innCount = max(1, per.size); val slot = w / overs; val bw = slot / innCount * 0.85f
            for (k in 0..maxR step (if (maxR > 24) 6 else 4)) { val y = bottom - h * k / maxR; drawLine(Color(0x33FFFFFF), Offset(left, y), Offset(size.width, y), 1f); drawContext.canvas.nativeCanvas.label("$k", 14f, y + 8f, 0xAAFFFFFF.toInt(), 20f) }
            per.keys.sorted().forEachIndexed { ii, inn ->
                val m = per[inn] ?: return@forEachIndexed
                for ((ov, rw) in m) {
                    if (ov >= overs) continue
                    val x = left + ov * slot + ii * bw; val bh = h * rw.first / maxR
                    drawRect(cols[ii % cols.size], Offset(x, bottom - bh), Size(bw, bh))
                    for (k in 0 until rw.second) drawCircle(C.Red, min(6f, bw / 2f), Offset(x + bw / 2f, bottom - bh - 8f - k * 13f))
                }
            }
            val step = if (overs > 30) 10 else 5
            for (o in 0..overs step step) drawContext.canvas.nativeCanvas.label("$o", left + o * slot, size.height - 4f, 0xAAFFFFFF.toInt(), 20f)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            innNames.forEachIndexed { i, n -> Text("■ $n  ", color = cols[i % cols.size], fontSize = 11.sp) }
            Text("● wicket", color = C.Red, fontSize = 11.sp)
        }
    }
}

/** Worm: cumulative runs by over for each innings. */
@Composable
fun Worm(events: List<BallEv>, innNames: List<String>, maxOvers: Int, modifier: Modifier = Modifier) {
    val cols = listOf(C.Green, C.Gold, C.Blue, Color(0xFFFF7043))
    val byInn = events.groupBy { it.inn }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val overs = max(maxOvers, (events.maxOfOrNull { it.over } ?: 0) + 1).coerceAtLeast(5)
            val series = byInn.toSortedMap().values.map { l -> var acc = 0; l.groupBy { it.over }.toSortedMap().map { (o, b) -> acc += b.sumOf { it.runs }; Triple(o + 1, acc, b.count { it.kind == "WICKET" }) } }
            val maxR = max(20, series.flatten().maxOfOrNull { it.second } ?: 20)
            val left = 40f; val bottom = size.height - 24f; val h = bottom - 8f; val w = size.width - left - 6f
            for (k in 0..4) { val v = maxR * k / 4; val y = bottom - h * v / maxR; drawLine(Color(0x33FFFFFF), Offset(left, y), Offset(size.width, y), 1f); drawContext.canvas.nativeCanvas.label("$v", 18f, y + 8f, 0xAAFFFFFF.toInt(), 20f) }
            series.forEachIndexed { i, s ->
                val path = Path(); path.moveTo(left, bottom)
                for ((o, r, wk) in s) {
                    val x = left + w * o / overs; val y = bottom - h * r / maxR; path.lineTo(x, y)
                    if (wk > 0) drawCircle(C.Red, 5f, Offset(x, y))
                }
                drawPath(path, cols[i % cols.size], style = Stroke(4f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            innNames.forEachIndexed { i, n -> Text("— $n  ", color = cols[i % cols.size], fontSize = 11.sp) }
        }
    }
}
