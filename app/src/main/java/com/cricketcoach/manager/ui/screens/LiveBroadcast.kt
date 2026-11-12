package com.cricketcoach.manager.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.render3d.*
import com.cricketcoach.manager.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.cos
import kotlin.math.sin

/*
 * v2.5 Phase F - true fullscreen broadcast match view.
 * The stadium fills the whole screen; a compact control rail sits on the right, a TV-style scoreboard along the bottom,
 * and Commentary / Highlights / Scorecard / Wagon open as a slide-in panel over the stadium.
 * Broadcast-inspired (navy + metallic gold), not a copy of any real broadcaster's graphics.
 */

private val Navy = Color(0xF20A1224)
private val NavyDeep = Color(0xF2060B1A)
private val Hair = Color(0x33E9B949)
private val Live = Color(0xFFE5484D)

/* ===================================================================================================== immersive */

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** Hides the system bars (swipe to peek) and keeps the screen awake while a match is on. */
@Composable
private fun BroadcastImmersive(keepOn: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(keepOn) {
        val w = view.context.findActivity()?.window
        if (keepOn) w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // the match is always fullscreen (maximum stadium space); the app-wide Display > Fullscreen setting covers the menus
        if (w != null && android.os.Build.FINGERPRINT != "robolectric") {
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
            if (insets?.isVisible(WindowInsetsCompat.Type.systemBars()) == true) WindowCompat.getInsetsController(w, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/* ===================================================================================================== icons */

/** Small line icons drawn in code so the whole rail shares one consistent style. */
enum class RIcon { BALL, OVER, INNINGS, PLAY, PAUSE, SIMEND, DECLARE, DEFEND, NORMAL, ATTACK, MIC, STAR, LIST, WAGON, CAM, VIEW }

@Composable
private fun RailGlyph(icon: RIcon, color: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) { drawGlyph(icon, color) }
}

private fun DrawScope.drawGlyph(icon: RIcon, col: Color) {
    val w = size.width; val h = size.height; val sw = w * 0.11f
    val st = Stroke(sw, cap = StrokeCap.Round)
    fun tri(x0: Float, w0: Float) = Path().apply { moveTo(x0, h * 0.18f); lineTo(x0 + w0, h * 0.5f); lineTo(x0, h * 0.82f); close() }
    when (icon) {
        RIcon.BALL -> { drawCircle(col, w * 0.40f, style = st); drawArc(col, -60f, 120f, false, Offset(w * 0.02f, h * 0.22f), Size(w * 0.5f, h * 0.56f), style = Stroke(sw * 0.7f)) }
        RIcon.OVER -> for (i in 0 until 6) drawCircle(col, w * 0.09f, Offset(w * (0.22f + 0.28f * (i % 3)), h * (0.34f + 0.32f * (i / 3))))
        RIcon.INNINGS -> { drawPath(tri(w * 0.14f, w * 0.5f), col); drawLine(col, Offset(w * 0.78f, h * 0.2f), Offset(w * 0.78f, h * 0.8f), sw * 1.4f, StrokeCap.Round) }
        RIcon.PLAY -> drawPath(tri(w * 0.26f, w * 0.56f), col)
        RIcon.PAUSE -> { drawLine(col, Offset(w * 0.34f, h * 0.2f), Offset(w * 0.34f, h * 0.8f), sw * 1.8f, StrokeCap.Round); drawLine(col, Offset(w * 0.66f, h * 0.2f), Offset(w * 0.66f, h * 0.8f), sw * 1.8f, StrokeCap.Round) }
        RIcon.SIMEND -> { drawPath(tri(w * 0.06f, w * 0.38f), col); drawPath(tri(w * 0.40f, w * 0.38f), col); drawLine(col, Offset(w * 0.88f, h * 0.2f), Offset(w * 0.88f, h * 0.8f), sw * 1.2f, StrokeCap.Round) }
        RIcon.DECLARE -> { drawLine(col, Offset(w * 0.24f, h * 0.12f), Offset(w * 0.24f, h * 0.9f), sw, StrokeCap.Round)
            drawPath(Path().apply { moveTo(w * 0.26f, h * 0.14f); lineTo(w * 0.84f, h * 0.30f); lineTo(w * 0.26f, h * 0.50f); close() }, col) }
        RIcon.DEFEND -> drawPath(Path().apply { moveTo(w * 0.5f, h * 0.1f); lineTo(w * 0.86f, h * 0.24f); quadraticBezierTo(w * 0.84f, h * 0.72f, w * 0.5f, h * 0.92f)
            quadraticBezierTo(w * 0.16f, h * 0.72f, w * 0.14f, h * 0.24f); close() }, col, style = st)
        RIcon.NORMAL -> { drawCircle(col, w * 0.38f, style = st); drawCircle(col, w * 0.12f) }
        RIcon.ATTACK -> for (k in 0..1) { val y = h * (0.52f - 0.3f * k)
            drawPath(Path().apply { moveTo(w * 0.18f, y + h * 0.28f); lineTo(w * 0.5f, y); lineTo(w * 0.82f, y + h * 0.28f) }, col, style = st) }
        RIcon.MIC -> { drawRoundRect(col, Offset(w * 0.36f, h * 0.08f), Size(w * 0.28f, h * 0.48f), CornerRadius(w * 0.14f))
            drawArc(col, 0f, 180f, false, Offset(w * 0.22f, h * 0.28f), Size(w * 0.56f, h * 0.44f), style = st)
            drawLine(col, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.9f), sw, StrokeCap.Round) }
        RIcon.STAR -> drawPath(Path().apply { for (i in 0 until 10) { val r = if (i % 2 == 0) w * 0.44f else w * 0.19f; val a = Math.toRadians(-90.0 + 36 * i)
            val x = w / 2 + r * cos(a).toFloat(); val y = h * 0.54f + r * sin(a).toFloat(); if (i == 0) moveTo(x, y) else lineTo(x, y) }; close() }, col)
        RIcon.LIST -> for (i in 0..2) { val y = h * (0.26f + 0.24f * i); drawCircle(col, w * 0.06f, Offset(w * 0.16f, y)); drawLine(col, Offset(w * 0.32f, y), Offset(w * 0.88f, y), sw, StrokeCap.Round) }
        RIcon.WAGON -> { drawCircle(col, w * 0.42f, style = Stroke(sw * 0.8f))
            for (a in listOf(-120.0, -60.0, -20.0, 40.0, 110.0, 170.0)) { val r = Math.toRadians(a); val l = w * (0.24f + 0.16f * ((a.toInt() + 200) % 3) / 2f)
                drawLine(col, Offset(w / 2, h * 0.62f), Offset(w / 2 + l * cos(r).toFloat(), h * 0.62f + l * sin(r).toFloat()), sw * 0.8f, StrokeCap.Round) } }
        RIcon.CAM -> {   // broadcast camera body + lens
            drawRoundRect(col, Offset(w * 0.08f, h * 0.3f), androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.42f), androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = st)
            drawPath(Path().apply { moveTo(w * 0.7f, h * 0.44f); lineTo(w * 0.94f, h * 0.3f); lineTo(w * 0.94f, h * 0.72f); lineTo(w * 0.7f, h * 0.58f); close() }, col, style = st)
        }
        RIcon.VIEW -> {  // cube (2D / 3D switch)
            val a = Offset(w * 0.5f, h * 0.12f); val b = Offset(w * 0.86f, h * 0.32f); val c = Offset(w * 0.86f, h * 0.72f); val d = Offset(w * 0.5f, h * 0.9f)
            val e = Offset(w * 0.14f, h * 0.72f); val f = Offset(w * 0.14f, h * 0.32f); val o = Offset(w * 0.5f, h * 0.52f)
            drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); lineTo(e.x, e.y); lineTo(f.x, f.y); close() }, col, style = st)
            drawLine(col, f, o, sw, StrokeCap.Round); drawLine(col, b, o, sw, StrokeCap.Round); drawLine(col, o, d, sw, StrokeCap.Round)
        }
    }
}

@Composable
private fun BarDiv() = Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(26.dp).background(Hair))

/** Pause menu: Resume / Settings / Exit Match (exit asks for confirmation; nothing is lost). */
@Composable
private fun PauseMenu(m: MatchSim, onResume: () -> Unit, onSettings: () -> Unit, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xB3020610)).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center) {
        Column(Modifier.width(300.dp).clip(RoundedCornerShape(14.dp)).background(DS.Navy2).border(1.dp, DS.Gold.copy(alpha = 0.45f), RoundedCornerShape(14.dp)).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MATCH PAUSED", color = DS.Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(4.dp))
            val inn = m.cur
            Text("${m.home.short} v ${m.away.short}", color = DS.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1)
            if (inn != null) Text("${m.club(inn.batTeam).short} ${inn.scoreText()} (${inn.oversText()} ov)", color = DS.Sub, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.height(14.dp))
            DsButton("Resume", Modifier.fillMaxWidth()) { onResume() }
            Spacer(Modifier.height(8.dp))
            DsButton("Settings", Modifier.fillMaxWidth(), kind = DsKind.SECONDARY) { onSettings() }
            Spacer(Modifier.height(8.dp))
            DsButton("Exit match", Modifier.fillMaxWidth(), kind = DsKind.DANGER) { onExit() }
        }
    }
}

/* ===================================================================================================== rail */

private val LocalRailH = compositionLocalOf { 42.dp }

@Composable
private fun RailBtn(label: String, icon: RIcon, modifier: Modifier = Modifier, active: Boolean = false, accent: Color = C.Gold, enabled: Boolean = true, narrow: Boolean = false, onClick: () -> Unit) {
    val bg = if (active) accent else Color(0xFF13203F)
    val fg = when { !enabled -> C.Sub.copy(alpha = 0.4f); active -> Color(0xFF0B1226); else -> C.Text }
    Column(modifier.height(LocalRailH.current).clip(RoundedCornerShape(9.dp)).background(bg)
        .border(BorderStroke(1.dp, if (active) accent else Color(0x29E9C46A)), RoundedCornerShape(9.dp))
        .semantics { contentDescription = label }
        .clickable(enabled = enabled, role = Role.Button) { GameAudio.click(); onClick() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        RailGlyph(icon, if (active || !enabled) fg else accent.copy(alpha = 0.95f), 15.dp)
        Text(label.uppercase(), color = fg, fontSize = if (label.length > 8 || narrow) 7.5.sp else 8.5.sp, letterSpacing = if (narrow) 0.sp else 0.4.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun RailLabel(text: String) = if (LocalRailH.current < 40.dp) Unit else Text(text, color = C.Gold.copy(alpha = 0.8f), fontSize = 7.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
    modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 1.dp))

/* ===================================================================================================== scoreboard */

private fun ballColor(b: String): Color = when {
    b == "W" -> Live; b == "6" -> C.Gold; b == "4" -> Color(0xFF3B82F6); b.startsWith("wd") || b.startsWith("nb") -> Color(0xFF8B5CF6)
    b == "0" || b == "•" -> Color(0xFF2A3A63); else -> Color(0xFF3C4E7A)
}

@Composable
private fun BallDot(b: String, size: Dp = 17.dp) {
    val col = ballColor(b)
    Box(Modifier.padding(end = 3.dp).size(size).clip(CircleShape).background(col), contentAlignment = Alignment.Center) {
        Text(if (b == "0") "•" else b, color = if (col == C.Gold) Color(0xFF1A1200) else Color.White, fontSize = if (b.length > 1) 7.sp else 9.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
    }
}

/** Everything the scoreboard needs, worked out once per ball. */
class BoardInfo(val inn: Inns?, val situation: String, val phase: String, val rate2: Pair<String, String>?, val rate3: Pair<String, String>?, val testLine: String?)

fun boardInfo(m: MatchSim): BoardInfo {
    val inn = m.cur ?: return BoardInfo(null, "Toss to come", "", null, null, null)
    val bat = m.club(inn.batTeam).short; val bowl = m.club(inn.bowlTeam).short
    val tgt = inn.target
    fun lead(): Int = m.innings.filter { it.batTeam == inn.batTeam }.sumOf { it.runs } - m.innings.filter { it.batTeam != inn.batTeam }.sumOf { it.runs }
    if (m.isTest) {
        val day = minOf(m.testBallsUsed / 540 + 1, 5); val session = (m.testBallsUsed % 540) / 180 + 1
        val ord = listOf("1st", "2nd", "3rd", "4th").getOrElse(m.innings.size - 1) { "${m.innings.size}th" }
        val ld = lead()
        val lt = when {
            m.innings.size == 1 -> "$bat batting first"
            tgt != null -> "$bat need ${tgt - inn.runs} to win"
            ld > 0 -> "$bat lead by $ld"; ld < 0 -> "$bat trail by ${-ld}"; else -> "Scores level"
        }
        val situation = if (m.finished) m.resultText else lt + (if (inn.followOn) " (following on)" else "")
        return BoardInfo(inn, situation, "DAY $day · SESSION $session", "LEAD" to (if (m.innings.size == 1) "-" else "%+d".format(ld)),
            if (tgt != null) "TGT" to "$tgt" else "OV LEFT" to "${maxOf(0, (m.testBallsTotal - m.testBallsUsed) / 6)}", "$ord INNINGS · DAY $day · SESSION $session")
    }
    val left = inn.maxBalls - inn.balls
    val phase = m.phaseLabel().uppercase()
    return if (tgt != null) {
        val need = tgt - inn.runs
        val rrr = if (left > 0 && need > 0 && !m.finished) "%.2f".format(need * 6.0 / left) else "-"
        val wp = m.winProbability()
        BoardInfo(inn, if (m.finished) m.resultText else "$bat need $need off $left ball${if (left == 1) "" else "s"}${wp?.let { " · win $it%" } ?: ""}", phase,
            "RRR" to rrr, "TGT" to "$tgt", null)
    } else {
        val proj = if (inn.balls >= 6) (inn.runs + inn.crr() * left / 6.0).toInt() else null
        val toss = if (m.tossWinner.isNotEmpty()) "${m.club(m.tossWinner).short} won the toss and chose to ${if (m.decision == "BAT") "bat" else "bowl"}" else ""
        BoardInfo(inn, if (m.finished) m.resultText else if (inn.balls < 12 && toss.isNotEmpty()) toss else "$bat v $bowl · ${proj?.let { "projected $it" } ?: "innings under way"}", phase,
            "PROJ" to (proj?.toString() ?: "-"), null, null)
    }
}

@Composable
fun TvScoreboard(m: MatchSim, modifier: Modifier = Modifier) {
    val bi = boardInfo(m)
    val inn = bi.inn
    val batClub = inn?.let { m.club(it.batTeam) }
    val teamCol = batClub?.let { Color(it.primary) } ?: C.Green
    Column(modifier.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
        .drawBehind { drawLine(Brush.horizontalGradient(listOf(Color.Transparent, C.Gold, C.GoldLight, C.Gold, Color.Transparent)), Offset(0f, 0f), Offset(size.width, 0f), 3f) }) {
        Row(Modifier.fillMaxWidth().height(52.dp).background(Color(0xF50E1A33)), verticalAlignment = Alignment.CenterVertically) {
            // team slab
            Row(Modifier.fillMaxHeight().width(82.dp).background(Brush.horizontalGradient(listOf(teamCol, teamCol.copy(alpha = 0.55f), Color.Transparent))).padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ClubBadge(batClub, 28.dp); Spacer(Modifier.width(5.dp))
                Text(batClub?.short ?: "", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, softWrap = false)
            }
            // score + overs
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.Center) {
                Text(inn?.scoreText() ?: "0/0", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 27.sp, maxLines = 1, softWrap = false, lineHeight = 27.sp)
                Text("${inn?.oversText() ?: "0.0"}${if (!m.isTest && inn != null) " / ${inn.maxBalls / 6}" else ""} OV", color = C.Gold, fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp, maxLines = 1, softWrap = false)
            }
            VDiv()
            // batters
            Column(Modifier.weight(1.25f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.Center) {
                if (inn != null) for (idx in listOf(inn.striker, inn.nonStriker)) {
                    val c = inn.bc.getOrNull(idx) ?: continue
                    val strike = idx == inn.striker
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(20.dp)) {
                        Box(Modifier.width(3.dp).height(13.dp).background(if (strike) C.Gold else Color.Transparent))
                        Text(m.pn(c.pid), color = if (strike) Color.White else C.Text.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = if (strike) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 5.dp))
                        Text("${c.runs}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
                        Text(" (${c.balls})", color = C.Sub, fontSize = 10.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
            VDiv()
            // bowler + last six balls
            Column(Modifier.weight(1.1f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.Center) {
                val bw = inn?.bw?.get(inn.bowler)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(20.dp)) {
                    Text(if (inn != null && inn.bowler.isNotEmpty()) m.pn(inn.bowler) else "-", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(bw?.let { "${it.oversText}-${it.runs}-${it.wk}" } ?: "", color = C.GoldLight, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
                }
                Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    val last = inn?.recent?.toList()?.takeLast(6) ?: emptyList()
                    for (b in last) BallDot(b)
                    repeat(6 - last.size) { Box(Modifier.padding(end = 3.dp).size(17.dp).border(1.dp, Color(0x33FFFFFF), CircleShape)) }
                }
            }
            VDiv()
            // rates
            Column(Modifier.width(IntrinsicSize.Max).padding(horizontal = 9.dp), verticalArrangement = Arrangement.Center) {
                RateRow("CRR", f2(inn?.crr()))
                bi.rate2?.let { RateRow(it.first, it.second, it.first == "RRR") }
                bi.rate3?.let { RateRow(it.first, it.second, true) }
            }
        }
        // situation strip
        Row(Modifier.fillMaxWidth().height(21.dp).background(NavyDeep).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().background(Brush.horizontalGradient(listOf(C.GoldDeep, C.Gold))).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text(if (m.finished) "RESULT" else bi.phase.ifBlank { "LIVE" }, color = Color(0xFF1A1200), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, maxLines = 1, softWrap = false)
            }
            val bits = ArrayList<String>()
            if (inn != null && !m.finished) {
                bits += "P'SHIP ${inn.partRuns} (${inn.partBalls})"
                inn.fow.lastOrNull()?.let { bits += "LAST WKT ${it.replace(" (", " ").removeSuffix(")")}" }
            }
            if (bi.testLine != null && !m.finished) bits.add(0, bi.testLine.substringBefore(" · "))
            Text(bits.joinToString("   •   "), color = C.Text.copy(alpha = 0.85f), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp).weight(1f, fill = false))
            Spacer(Modifier.weight(0.01f).widthIn(min = 8.dp))
            Text(bi.situation, color = C.GoldLight, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun RateRow(k: String, v: String, hi: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(16.dp)) {
        Text(k, color = C.Sub, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, modifier = Modifier.width(46.dp), maxLines = 1, softWrap = false)
        Text(v, color = if (hi) C.GoldLight else Color.White, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun VDiv() = Box(Modifier.width(1.dp).fillMaxHeight(0.7f).background(Hair))

/* ===================================================================================================== screen */

@Composable
fun LiveMatchScreen(vm: GameVM, initialPanel: Int = 0, initialPaused: Boolean = false) {
    val s = vm.save ?: return
    val m = vm.sim
    @Suppress("UNUSED_VARIABLE") val tk = vm.simTick
    if (m == null) { ScreenScaffold(vm, "Live Match") { Sub("No match in progress.", Modifier.padding(16.dp)) }; return }
    if (!m.started) m.autoToss()
    val me = vm.userSideId() ?: s.userClubId
    var view3d by remember { mutableStateOf(vm.settings.view3d) }
    var cam by remember { mutableIntStateOf(vm.settings.camMode) }
    var animBusy by remember { mutableStateOf(false) }
    var replayEv by remember { mutableStateOf<BallEv?>(null) }
    var playKey by remember { mutableIntStateOf(0) }
    /** 0 none, 1 commentary, 2 highlights, 3 scorecard, 4 wagon & charts */
    var panel by remember { mutableIntStateOf(initialPanel) }
    var chart by remember { mutableIntStateOf(0) }
    var wagonInn by remember { mutableIntStateOf(-1) }
    var speedX by remember { mutableIntStateOf(vm.settings.playSpeed.let { if (it in SPEEDS) it else 1 }) }
    val dur = (vm.settings.simSpeedMs * 6 / speedX).coerceIn(500, 4200)
    /** v2.6 pause menu (Resume / Settings / Exit Match). There is no Back button during a match: system Back opens this menu. */
    var paused by remember { mutableStateOf(initialPaused) }
    var confirmExit by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = true) {
        when { confirmExit -> confirmExit = false; m.finished -> vm.leaveMatch(); panel != 0 -> panel = 0; else -> { vm.autoPlay = false; paused = !paused } }
    }
    BroadcastImmersive(vm.settings.keepScreenOn)
    DisposableEffect(Unit) { GameAudio.setScene(GameAudio.Scene.MATCH); onDispose { GameAudio.setScene(GameAudio.Scene.MENU) } }
    // ball-by-ball commentary (English / Hindi, situation-aware); 2D mode: sound + voice straight away, 3D: at the animation moment
    LaunchedEffect(m.events.size) {
        val line = vm.updateCommentary(m)
        if (!view3d) { m.lastEv?.let { GameAudio.onBat(it.kind, it.how); GameAudio.onOutcome(it.kind, it.how, it.milestone) }; line?.let { vm.speakBall(it.evId) } }
        GameAudio.setMood(com.cricketcoach.manager.audio.CrowdMood.of(m))
    }
    LaunchedEffect(m.innings.size) { if (m.innings.size > 1 && !m.finished) GameAudio.play("horn", 0.7f) }
    LaunchedEffect(m.finished) { if (m.finished) { GameAudio.play("horn"); GameAudio.play("applause"); if (vm.comLines.none { it.kind == "RESULT" }) GameAudio.speak(m.resultText) } }
    LaunchedEffect(vm.autoPlay, view3d, speedX) {
        while (vm.autoPlay && !m.finished) {
            if (view3d) { withTimeoutOrNull(32_000L / speedX + 2_000L) { snapshotFlow { animBusy }.first { !it } }; delay(300L / speedX) } else delay(vm.settings.simSpeedMs.toLong() / speedX)
            if (!vm.autoPlay) break
            replayEv = null
            val before = m.events.size
            vm.stepBall()
            if (view3d && m.events.size > before) animBusy = true
            // Settings > Match > Pause auto-play after a wicket
            if (vm.settings.autoPauseWicket && m.events.size > before && m.lastEv?.kind == "WICKET" && !m.finished) { vm.autoPlay = false; vm.msg("Wicket! Auto-play paused - change your plans or press Auto.") }
        }
    }
    fun step(f: () -> Unit) { replayEv = null; val before = m.events.size; f(); if (view3d && m.events.size > before) animBusy = true }
    fun toggle(p: Int) { panel = if (panel == p) 0 else p }
    val ev = replayEv ?: m.lastEv
    val inn = m.cur
    val batCol = inn?.let { Color(m.club(it.batTeam).primary) } ?: C.Green
    val bowlCol = inn?.let { Color(m.club(it.bowlTeam).primary) } ?: C.Blue

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF040A18))) {
        // v2.6: no side rail - the stadium gets the full width; controls sit in a compact bar under the scoreboard
        val railW = 0.dp
        val panelW = (maxWidth * 0.42f).coerceIn(280.dp, 380.dp)
        val railBtnH = if (maxHeight < 400.dp) 38.dp else 42.dp
        val bottomH = 73.dp + railBtnH + 10.dp

        /* ---- stadium: full-bleed, behind everything ---- */
        Box(Modifier.fillMaxSize()) {
            val legacy: @Composable () -> Unit = { Stadium3DView(ev, batCol, if (bowlCol == batCol) Color(0xFF1565C0) else bowlCol, cam, dur, replayEv != null, Modifier.fillMaxSize(), playKey = playKey, onDone = { animBusy = false }) }
            if (view3d && vm.settings.gfx >= Gfx.MEDIUM) {
                val fmt = m.cfg.format
                val light = when { fmt == "TEST" && (m.cfg.weather == "Overcast" || m.cfg.weather == "Cloudy") -> Lighting.OVERCAST; fmt == "TEST" -> Lighting.DAY; fmt == "ODI" -> Lighting.DUSK; else -> Lighting.NIGHT }
                val wear = remember(m.events.size) { val tot = m.innings.sumOf { it.balls }; val cap = if (fmt == "TEST") 2700f else (m.cfg.overs * 12f).coerceAtLeast(60f); (0.08f + 0.92f * tot / cap).coerceIn(0f, 1f) }
                Match3DView(ev, remember(ev?.id, inn?.bowlTeam) { castFor(m, ev, s.players) }, cam, dur, replayEv != null, vm.settings.gfx, light, fmt == "TEST",
                    Modifier.fillMaxSize(), playKey = playKey, pitchWear = wear, rate = speedX.toFloat(),
                    onImpact = { e -> GameAudio.onBat(e.kind, e.how) }, onOutcome = { e -> GameAudio.onOutcome(e.kind, e.how, e.milestone); vm.speakBall(e.id) },
                    onDone = { animBusy = false }, fallback = legacy)
            } else if (view3d) legacy()
            else Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF14532D), Color(0xFF0B2E1A), Color(0xFF040A18))))) {
                WagonWheel(m.events.filter { it.inn == m.innings.size - 1 }, Modifier.fillMaxSize().padding(start = 40.dp, end = railW + 40.dp, top = 56.dp, bottom = 110.dp))
            }
            // broadcast vignette so overlays stay readable
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x99000000), 0.18f to Color.Transparent, 0.72f to Color.Transparent, 1f to Color(0xB3000000))))
        }

        /* ---- overlays (kept clear of the notch / punch-hole) ---- */
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            // top-left match bug
            Row(Modifier.align(Alignment.TopStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Navy).border(1.dp, Hair, CircleShape).semantics { contentDescription = if (m.finished) "Leave match" else "Pause menu" }
                    .clickable(role = Role.Button) { GameAudio.click(); vm.autoPlay = false; if (m.finished) vm.leaveMatch() else paused = true }, contentAlignment = Alignment.Center) {
                    if (m.finished) Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = C.Text, modifier = Modifier.size(18.dp)) else RailGlyph(RIcon.PAUSE, C.Text, 15.dp)
                }
                Spacer(Modifier.width(8.dp))
                Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Navy).border(1.dp, Hair, RoundedCornerShape(10.dp)).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    ClubBadge(m.home, 24.dp)
                    Column(Modifier.padding(horizontal = 7.dp)) {
                        Text("${m.home.short}  v  ${m.away.short}", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false, lineHeight = 15.sp)
                        Text("${m.cfg.compName} · ${m.cfg.format}", color = C.Sub, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                    ClubBadge(m.away, 24.dp)
                    if (!m.finished) {
                        Spacer(Modifier.width(8.dp))
                        val pulse = rememberInfiniteTransition(label = "live").animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Live.copy(alpha = pulse.value)))
                        Text(" LIVE", color = Live, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                    }
                }
            }
            // top-right view controls (left of the rail)
            Row(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = railW + 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (replayEv != null) ViewPill("● LIVE", Live, true) { replayEv = null }
                if (!m.finished) Row(Modifier.padding(start = 4.dp).height(30.dp).clip(RoundedCornerShape(8.dp)).background(Navy).border(1.dp, Hair, RoundedCornerShape(8.dp)).padding(2.dp)) {
                    val agg = m.club(me).aggression
                    for ((v, l) in listOf(0 to "Defend", 1 to "Normal", 2 to "Attack")) {
                        val acc = when (v) { 0 -> Color(0xFF7FB2FF); 1 -> C.Gold; else -> Color(0xFFF59E5B) }
                        Box(Modifier.fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(if (agg == v) acc else Color.Transparent).semantics { contentDescription = l }
                            .clickable(role = Role.Button) { GameAudio.click(); vm.setAggression(v) }.padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                            Text(l.uppercase(), color = if (agg == v) Color(0xFF0B1226) else C.Sub, fontSize = 8.5.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
                        }
                    }
                }
                ViewPill("${speedX}x", C.Gold, speedX > 1) { speedX = SPEEDS[(SPEEDS.indexOf(speedX) + 1) % SPEEDS.size]; vm.settings.playSpeed = speedX }
                m.winProbability()?.let { wp ->
                    val chasing = inn?.let { m.club(it.batTeam).short } ?: ""
                    Column(Modifier.padding(start = 4.dp).clip(RoundedCornerShape(8.dp)).background(Navy).border(1.dp, Hair, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("WIN %", color = C.Sub, fontSize = 7.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$chasing $wp", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Box(Modifier.padding(start = 5.dp).width(34.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF26355E))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(wp / 100f).background(C.Gold))
                            }
                        }
                    }
                }
            }

            /* ---- slide-in panel ---- */
            AnimatedVisibility(panel != 0, Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 8.dp, bottom = bottomH + 26.dp),
                enter = slideInHorizontally(tween(220)) { it / 2 } + fadeIn(tween(220)), exit = slideOutHorizontally(tween(180)) { it / 2 } + fadeOut(tween(180))) {
                val shown = panel.takeIf { it != 0 } ?: 1
                Column(Modifier.width(panelW).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Color(0xEE081127)).border(1.dp, Hair, RoundedCornerShape(12.dp))) {
                    Row(Modifier.fillMaxWidth().height(32.dp).background(Brush.horizontalGradient(listOf(Color(0xFF13214A), Color(0xFF0A1430)))).padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        val (title, ic) = when (shown) { 1 -> "Commentary" to RIcon.MIC; 2 -> "Highlights" to RIcon.STAR; 3 -> "Scorecard" to RIcon.LIST; else -> "Wagon & charts" to RIcon.WAGON }
                        RailGlyph(ic, C.Gold, 13.dp); Spacer(Modifier.width(6.dp))
                        Text(title.uppercase(), color = C.Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                        Box(Modifier.size(32.dp).semantics { contentDescription = "Close panel" }.clickable(role = Role.Button) { panel = 0 }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, null, tint = C.Sub, modifier = Modifier.size(16.dp))
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val innNames = m.innings.mapIndexed { i, it -> "${m.club(it.batTeam).short}${if (m.isTest) " (${i + 1})" else ""}" }
                        when (shown) {
                            1 -> if (vm.comLines.isNotEmpty()) CommentaryFeed(vm) else LazyColumn(Modifier.padding(horizontal = 8.dp)) {
                                items(m.com.take(150)) { line ->
                                    val col = when { line.contains("OUT") || line.contains("WICKET") -> C.Red; line.contains("SIX") -> C.Gold; line.contains("FOUR") -> C.Green; line.startsWith("End of over") -> C.Blue; else -> C.Text }
                                    Text(line, color = col, fontSize = 11.sp, modifier = Modifier.padding(vertical = 3.dp))
                                    HorizontalDivider(color = Color(0xFF1B2A52))
                                }
                            }
                            2 -> {
                                val hl = m.events.filter { it.highlight }.reversed()
                                if (hl.isEmpty()) Sub("Boundaries, wickets and milestones appear here. Tap one to watch the replay.", Modifier.padding(10.dp))
                                LazyColumn(Modifier.padding(horizontal = 6.dp)) {
                                    items(hl.take(120)) { e ->
                                        val (tag, col) = when (e.kind) { "SIX" -> "6" to C.Gold; "FOUR" -> "4" to Color(0xFF3B82F6); "WICKET" -> "W" to Live; else -> "★" to Color(0xFF8B5CF6) }
                                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF111C3D)).clickable {
                                            GameAudio.play("whoosh"); if (!view3d) { view3d = true; vm.settings.view3d = true }; vm.autoPlay = false; replayEv = e; playKey++
                                        }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(24.dp).clip(CircleShape).background(col), contentAlignment = Alignment.Center) { Text(tag, color = if (col == C.Gold) Color.Black else Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp) }
                                            Spacer(Modifier.width(6.dp))
                                            Text("${e.over}.${e.ball}", color = C.Sub, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                                            Text(e.text, color = C.Text, fontSize = 10.5.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            RailGlyph(RIcon.PLAY, C.Gold, 14.dp)
                                        }
                                    }
                                }
                            }
                            3 -> Scorecard(m)
                            else -> Column(Modifier.fillMaxSize().padding(6.dp)) {
                                LazyRow {
                                    items(4) { i -> val l = listOf("Wagon", "Manhattan", "Worm", "Events")[i]
                                        Text(l, color = if (i == chart) Color(0xFF0B1226) else C.Sub, fontSize = 10.sp, fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(2.dp).clip(RoundedCornerShape(10.dp)).background(if (i == chart) C.Gold else Color(0xFF16234A)).clickable { chart = i }.padding(horizontal = 9.dp, vertical = 5.dp)) }
                                }
                                when (chart) {
                                    0 -> {
                                        val sel = if (wagonInn < 0) m.innings.size - 1 else wagonInn
                                        LazyRow { items(innNames.size) { i -> Text(innNames[i], color = if (i == sel) Color(0xFF0B1226) else C.Sub, fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(2.dp).clip(RoundedCornerShape(8.dp)).background(if (i == sel) C.GoldLight else Color(0xFF16234A)).clickable { wagonInn = i }.padding(horizontal = 8.dp, vertical = 3.dp)) } }
                                        WagonWheel(m.events.filter { it.inn == sel }, Modifier.fillMaxWidth().weight(1f))
                                    }
                                    1 -> Manhattan(m.events, innNames, if (m.isTest) 0 else m.cfg.overs, Modifier.fillMaxSize().padding(4.dp))
                                    2 -> Worm(m.events, innNames, if (m.isTest) 0 else m.cfg.overs, Modifier.fillMaxSize().padding(4.dp))
                                    else -> LazyColumn(Modifier.padding(6.dp)) {
                                        if (m.rainNote.isNotEmpty()) item { Text(m.rainNote, color = C.Blue, fontSize = 11.sp) }
                                        items(m.keyEvents.reversed()) { Text("• $it", color = C.Text, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            /* ---- bottom: commentary ticker + TV scoreboard ---- */
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 8.dp, end = railW + 6.dp)) {
                if (ev != null) Row(Modifier.padding(bottom = 4.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC050A18)).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (replayEv != null) Text("REPLAY  ", color = C.Gold, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Text("${ev.over}.${ev.ball}  ", color = C.Gold, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(vm.comLines.firstOrNull { it.evId == ev.id && it.kind == ev.kind }?.text ?: ev.text, color = C.Text, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TvScoreboard(m, Modifier.fillMaxWidth())
                // compact match controls right under the scoreboard
                val camNames = listOf("TV", "Aerial", "Side", "Bat")
                val live = !m.finished
                CompositionLocalProvider(LocalRailH provides railBtnH) {
                    Row(Modifier.fillMaxWidth().background(NavyDeep).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val w = Modifier.width(if (railBtnH < 40.dp) 56.dp else 62.dp)
                        RailBtn("Ball", RIcon.BALL, w, enabled = live) { step { vm.stepBall() } }
                        RailBtn("Over", RIcon.OVER, w, enabled = live) { step { vm.stepOver() } }
                        RailBtn("Innings", RIcon.INNINGS, w, enabled = live) { step { vm.stepInnings() } }
                        RailBtn(if (vm.autoPlay) "Pause" else "Auto", if (vm.autoPlay) RIcon.PAUSE else RIcon.PLAY, w, active = vm.autoPlay, enabled = live) { vm.autoPlay = !vm.autoPlay }
                        RailBtn("Sim end", RIcon.SIMEND, w, enabled = live) { step { vm.simRest() } }
                        if (m.isTest) RailBtn("Declare", RIcon.DECLARE, w, enabled = live && m.canDeclare()) { vm.declare() }
                        BarDiv()
                        RailBtn("Commentary", RIcon.MIC, w, active = panel == 1) { toggle(1) }
                        RailBtn("Scorecard", RIcon.LIST, w, active = panel == 3) { toggle(3) }
                        RailBtn("Highlights", RIcon.STAR, w, active = panel == 2) { toggle(2) }
                        RailBtn("Wagon", RIcon.WAGON, w, active = panel == 4) { toggle(4) }
                        BarDiv()
                        RailBtn("Cam ${camNames[cam]}", RIcon.CAM, w, enabled = view3d) { cam = (cam + 1) % 4; vm.settings.camMode = cam }
                        RailBtn(if (view3d) "2D" else "3D", RIcon.VIEW, w) { view3d = !view3d; vm.settings.view3d = view3d; animBusy = false }
                    }
                }
            }

            /* ---- result card ---- */
            if (m.finished && panel == 0) Box(Modifier.align(Alignment.Center).padding(end = railW).clip(RoundedCornerShape(16.dp))
                .background(Color(0xF50A1224)).border(1.dp, C.Gold.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(horizontal = 22.dp, vertical = 14.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("FULL TIME", color = C.Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text(m.resultText, color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 380.dp))
                    if (m.superOverText.isNotEmpty()) Sub(m.superOverText)
                    m.potm()?.let { pid ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            PlayerFace(pid, s.players[pid]?.name ?: pid, 38.dp); Spacer(Modifier.width(8.dp))
                            Column { Text("PLAYER OF THE MATCH", color = C.Sub, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp); Text(s.players[pid]?.name ?: pid, color = C.Text, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineBtn("Scorecard", Modifier.width(130.dp)) { panel = 3 }
                        GoldButton("Continue", Modifier.width(150.dp)) { vm.leaveMatch() }
                    }
                }
            }
        }
        /* ---- pause menu ---- */
        if (paused && !m.finished) PauseMenu(m, onResume = { paused = false }, onSettings = { paused = false; vm.nav(Screen.SettingsScr) }, onExit = { confirmExit = true })
        if (confirmExit) DsConfirm("Exit match?", "The match is kept exactly at this ball and your career is saved. Resume it any time from the Match Centre or the Calendar.",
            "Exit match", danger = true, onConfirm = { confirmExit = false; paused = false; vm.exitMatch() }, onDismiss = { confirmExit = false })
    }
}

@Composable
private fun ViewPill(text: String, accent: Color = C.Text, on: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.padding(start = 4.dp).height(30.dp).widthIn(min = 40.dp).clip(RoundedCornerShape(8.dp)).background(if (on) accent else Navy)
        .border(1.dp, if (on) accent else Hair, RoundedCornerShape(8.dp)).semantics { contentDescription = text }
        .clickable(role = Role.Button) { GameAudio.click(); onClick() }.padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (on) Color(0xFF0B1226) else C.Text, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, maxLines = 1, softWrap = false)
    }
}
