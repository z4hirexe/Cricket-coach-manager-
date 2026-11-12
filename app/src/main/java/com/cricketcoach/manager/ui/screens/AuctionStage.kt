package com.cricketcoach.manager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*
import kotlinx.coroutines.delay

/*
 * Phase 6 - Auction presentation: an auction-hall stage with spotlights, an auctioneer at the lectern whose gavel strikes on
 * "going once / twice / sold", the lot card, an animated bid ticker, a wall of team paddles (the leading paddle is raised)
 * and a SOLD / UNSOLD stamp. All artwork is drawn in code (generic auctioneer silhouette, original team badges).
 */

internal val StageTop = Color(0xFF0B1230)
internal val StageBottom = Color(0xFF02040C)

internal fun logColor(line: String) = when {
    line.startsWith("🔨") -> C.Gold; line.startsWith("YOU") -> C.Positive; line.startsWith("UNSOLD") -> C.Red; line.startsWith("LOT") -> C.Blue; else -> C.Sub
}

/* ----------------------------------------------------------------- backdrop */

@Composable
internal fun StageBackdrop(tint: Color) {
    val inf = rememberInfiniteTransition(label = "stage")
    val sweep by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse), label = "sweep")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(StageTop, StageBottom)))
        // LED wall glow tinted by the leading team
        drawRect(Brush.verticalGradient(listOf(tint.copy(alpha = 0.28f), Color.Transparent), 0f, h * 0.55f))
        // dotted LED matrix
        val step = 14f
        var y = 6f
        while (y < h * 0.42f) { var x = 6f; while (x < w) { drawCircle(Color.White.copy(alpha = 0.035f), 1.4f, Offset(x, y)); x += step }; y += step }
        // spotlight cones
        fun cone(cx: Float, spread: Float, a: Float) {
            val path = Path().apply { moveTo(cx - 10f, 0f); lineTo(cx + 10f, 0f); lineTo(cx + spread, h); lineTo(cx - spread, h); close() }
            drawPath(path, Brush.verticalGradient(listOf(Color.White.copy(alpha = a), Color.Transparent), 0f, h * 0.95f))
        }
        cone(w * 0.36f + sweep * w * 0.04f, w * 0.16f, 0.10f)
        cone(w * 0.55f - sweep * w * 0.05f, w * 0.18f, 0.08f)
        cone(w * 0.10f, w * 0.10f, 0.07f)
        // stage floor with a gold rim
        drawOval(Brush.radialGradient(listOf(tint.copy(alpha = 0.22f), Color.Transparent), Offset(w * 0.42f, h * 1.02f), w * 0.4f), Offset(w * 0.02f, h * 0.84f), Size(w * 0.8f, h * 0.36f))
        drawOval(C.Gold.copy(alpha = 0.25f), Offset(w * 0.04f, h * 0.88f), Size(w * 0.76f, h * 0.3f), style = Stroke(2f))
    }
}

@Composable
internal fun LedChip(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xCC050A18)).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, maxLines = 1)
    }
}

/* ----------------------------------------------------------------- auctioneer */

/** Generic auctioneer silhouette at a lectern; the gavel strikes whenever [strikeKey] changes. */
@Composable
internal fun Auctioneer(strikeKey: Int, countdown: Int, modifier: Modifier = Modifier) {
    val swing = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(strikeKey) {
        if (first) { first = false; return@LaunchedEffect }
        swing.animateTo(1f, tween(140, easing = FastOutLinearInEasing))
        swing.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 260f))
    }
    val inf = rememberInfiniteTransition(label = "breath")
    val breath by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "b")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val cx = w * 0.45f
        val bob = breath * 2f
        // body: suit + shirt + tie
        val torso = Path().apply {
            moveTo(cx - w * 0.30f, h * 0.70f); quadraticBezierTo(cx - w * 0.30f, h * 0.36f + bob, cx - w * 0.10f, h * 0.33f + bob)
            lineTo(cx + w * 0.10f, h * 0.33f + bob); quadraticBezierTo(cx + w * 0.30f, h * 0.36f + bob, cx + w * 0.30f, h * 0.70f); close()
        }
        drawPath(torso, Brush.verticalGradient(listOf(Color(0xFF1E2A4A), Color(0xFF0C1226))))
        val shirt = Path().apply { moveTo(cx - w * 0.07f, h * 0.335f + bob); lineTo(cx + w * 0.07f, h * 0.335f + bob); lineTo(cx, h * 0.52f); close() }
        drawPath(shirt, Color(0xFFE8ECF5))
        val tie = Path().apply { moveTo(cx - w * 0.02f, h * 0.35f + bob); lineTo(cx + w * 0.02f, h * 0.35f + bob); lineTo(cx + w * 0.03f, h * 0.50f); lineTo(cx, h * 0.53f); lineTo(cx - w * 0.03f, h * 0.50f); close() }
        drawPath(tie, C.Gold)
        // head (featureless, softly lit)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFB98A68), Color(0xFF6E4B35)), Offset(cx - w * 0.03f, h * 0.18f + bob), w * 0.14f), w * 0.11f, Offset(cx, h * 0.21f + bob))
        drawArc(Color(0xFF1B1410), 180f, 180f, true, Offset(cx - w * 0.115f, h * 0.085f + bob), Size(w * 0.23f, h * 0.15f))
        // gavel arm + gavel (pivot at the right hand)
        val pivot = Offset(cx + w * 0.30f, h * 0.55f)
        val angle = -55f + 55f * swing.value
        drawLine(Color(0xFF1E2A4A), Offset(cx + w * 0.22f, h * 0.42f + bob), pivot, w * 0.07f)
        rotate(angle, pivot) {
            drawLine(Color(0xFF8D5A2B), pivot, Offset(pivot.x + w * 0.20f, pivot.y), w * 0.025f)
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFA86B36), Color(0xFF5E3615))), Offset(pivot.x + w * 0.17f, pivot.y - h * 0.06f), Size(w * 0.09f, h * 0.12f), CornerRadius(4f))
            drawRect(C.Gold, Offset(pivot.x + w * 0.17f, pivot.y - h * 0.012f), Size(w * 0.09f, h * 0.024f))
        }
        // strike flash on the sound block
        val block = Offset(pivot.x + w * 0.21f, h * 0.66f)
        if (swing.value > 0.7f) drawCircle(C.GoldLight.copy(alpha = (swing.value - 0.7f) * 2.5f), w * 0.12f, block)
        drawRoundRect(Color(0xFF4A2A10), Offset(block.x - w * 0.08f, block.y - h * 0.015f), Size(w * 0.16f, h * 0.035f), CornerRadius(3f))
        // lectern
        val lec = Path().apply { moveTo(w * 0.02f, h * 0.68f); lineTo(w * 0.98f, h * 0.68f); lineTo(w * 0.88f, h); lineTo(w * 0.12f, h); close() }
        drawPath(lec, Brush.verticalGradient(listOf(Color(0xFF2A1A0C), Color(0xFF120A04)), h * 0.68f, h))
        drawRect(C.Gold, Offset(w * 0.02f, h * 0.675f), Size(w * 0.96f, h * 0.02f))
        drawCircle(C.GoldBrush, w * 0.09f, Offset(w * 0.5f, h * 0.84f))
        drawCircle(Color(0xFF120A04), w * 0.065f, Offset(w * 0.5f, h * 0.84f))
        drawCircle(C.Gold, w * 0.03f, Offset(w * 0.5f, h * 0.84f))
        if (countdown > 0) drawCircle(C.Red.copy(alpha = 0.25f + 0.15f * breath), w * 0.02f, Offset(w * 0.9f, h * 0.73f))
    }
}

@Composable
internal fun GoingPips(countdown: Int) {
    Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf("ONCE", "TWICE", "SOLD").forEachIndexed { i, l ->
            val on = countdown > i
            val col by animateColorAsState(if (on) (if (i == 2) C.Red else C.Gold) else Color(0x33FFFFFF), label = "pip")
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(col.copy(alpha = if (on) 1f else 0.25f)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                Text(l, color = if (on) Color.Black else C.Sub, fontSize = 7.5.sp, lineHeight = 9.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
internal fun TagChip(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = 0.16f)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(text.uppercase(), color = color, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, maxLines = 1)
    }
}

@Composable
internal fun MiniBar(label: String, v: Int, col: Color, max: Int = 99) {
    val f by animateFloatAsState((v / max.toFloat()).coerceIn(0f, 1f), tween(700), label = "bar")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Text(label, color = C.Sub, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x22FFFFFF))) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(col.copy(alpha = 0.6f), col))))
        }
        Text("$v", color = C.Text, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(26.dp))
    }
}

/* ----------------------------------------------------------------- SOLD / UNSOLD stamp */

@Composable
internal fun SoldStamp(s: CareerSave, pid: String, club: Club?, price: Double, mine: Boolean) {
    val p = s.players[pid]
    val sold = club != null
    val scale = remember { Animatable(2.4f) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(pid, price) {
        scale.snapTo(2.4f); ring.snapTo(0f)
        scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f))
    }
    LaunchedEffect(pid, price) { delay(120); ring.animateTo(1f, tween(700, easing = LinearOutSlowInEasing)) }
    val col = when { !sold -> Color(0xFF9AA4B8); mine -> C.Positive; else -> C.Gold }
    Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
        // team-colour burst behind the stamp
        Canvas(Modifier.fillMaxSize()) {
            val ctr = Offset(size.width / 2, size.height / 2)
            val base = club?.let { Color(it.primary) } ?: Color(0xFF39404F)
            drawCircle(Brush.radialGradient(listOf(base.copy(alpha = 0.55f), Color.Transparent), ctr, size.minDimension * 0.7f), size.minDimension * 0.7f, ctr)
            drawCircle(col.copy(alpha = (1f - ring.value) * 0.8f), size.minDimension * (0.2f + 0.5f * ring.value), ctr, style = Stroke(6f * (1f - ring.value) + 1f))
            if (sold) for (i in 0 until 16) {
                val ang = Math.toRadians(i * 22.5).toFloat()
                val r0 = size.minDimension * (0.22f + 0.25f * ring.value); val r1 = r0 + 26f
                drawLine(col.copy(alpha = (1f - ring.value) * 0.9f), Offset(ctr.x + r0 * kotlin.math.cos(ang), ctr.y + r0 * kotlin.math.sin(ang)),
                    Offset(ctr.x + r1 * kotlin.math.cos(ang), ctr.y + r1 * kotlin.math.sin(ang)), 4f)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.scale(scale.value).rotate(-8f).clip(RoundedCornerShape(10.dp)).border(5.dp, col, RoundedCornerShape(10.dp)).background(Color(0x66000000))
                .padding(horizontal = 26.dp, vertical = 2.dp)) {
                Text(if (sold) "SOLD!" else "UNSOLD", color = col, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 72.sp, letterSpacing = 4.sp, lineHeight = 76.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (p != null) PlayerFace(p.id, p.name, 54.dp, col)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(p?.name?.uppercase() ?: "", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 26.sp)
                    if (sold) Text("to ${club!!.name} for ${Money.fmt(price)}", color = C.GoldLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    else Text("No bids — goes to the free-agent pool", color = C.Sub, fontSize = 13.sp)
                    if (mine) Text("WELCOME TO THE SQUAD!", color = C.Positive, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                }
                if (club != null) { Spacer(Modifier.width(12.dp)); ClubBadge(club, 54.dp) }
            }
        }
    }
}
