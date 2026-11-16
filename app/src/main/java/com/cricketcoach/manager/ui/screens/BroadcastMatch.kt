package com.cricketcoach.manager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/* =====================================================================================================================
 * v2.7 BROADCAST MATCH CENTRE
 * TV-style live match screen: broadcast header, full scorecard (6 tabs), 2D pitch view, ball tracker with per-ball history,
 * bowler / striker / non-striker analysis panels, automatic over summary with bowler change, new-batter selection and a
 * compact control bar.  Runs on the existing MatchSim; the 3D stadium view (LiveMatchScreen) stays available from the menu.
 * ===================================================================================================================== */

/** Broadcast palette (navy / royal blue chrome, white & light-grey scorecard panels, pitch greens, metallic gold). */
object TV {
    val Navy = Color(0xFF071233); val Navy2 = Color(0xFF0C1B4A); val Royal = Color(0xFF123B9E); val RoyalHi = Color(0xFF1F55D0)
    val Panel = Color(0xFFF5F7FB); val Panel2 = Color(0xFFE6EAF2); val PanelLine = Color(0xFFCBD3E1)
    val Ink = Color(0xFF0B1226); val Ink2 = Color(0xFF4A5570); val Gold = Color(0xFFE9B949); val GoldDeep = Color(0xFFB8862B)
    val Pitch = Color(0xFF2E8B3B); val PitchDark = Color(0xFF23702F); val Strip = Color(0xFFD9C58A)
    val Live = Color(0xFFE5484D); val Blue = Color(0xFF2F7DF6); val Green = Color(0xFF2FB36B); val Purple = Color(0xFF8B5CF6); val Grey = Color(0xFF8792A8)
    val Hair = Color(0x332F7DF6)
    val panelShape = RoundedCornerShape(6.dp)
    val GoldBrush = Brush.linearGradient(listOf(Color(0xFFF6DE8E), Color(0xFFE9B949), Color(0xFFB8862B)))
}

/** Colour of one delivery in the ball tracker / history. */
fun ballChipColor(e: BallEv): Color = when (e.kind) {
    "WICKET" -> TV.Live; "SIX" -> TV.Purple; "FOUR" -> TV.Blue; "WIDE", "NB" -> Color(0xFFF59E0B)
    "RUN" -> when (e.bat) { 1 -> Color(0xFF3F9D5A); 2 -> Color(0xFF2E8B57); else -> Color(0xFF1F7A4A) }
    else -> Color(0xFF9AA3B5)
}

private sealed class Overlay {
    object None : Overlay()
    class OverSummary(val inn: Int, val over: Int) : Overlay()
    object NewBatter : Overlay()
    object InningsBreak : Overlay()
    object Final : Overlay()
}

private fun fmtOv(d: Double) = "%.2f".format(d)
private fun sr(c: BatCard) = if (c.balls > 0) "%.1f".format(c.runs * 100.0 / c.balls) else "-"

@Composable
private fun Lbl(text: String, color: Color = TV.Ink2, size: Int = 8, modifier: Modifier = Modifier) =
    Text(text.uppercase(), color = color, fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, maxLines = 1, softWrap = false, modifier = modifier)

@Composable
private fun TvTab(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(26.dp).background(if (on) TV.Royal else Color.Transparent).clickable(role = Role.Tab) { GameAudio.click(); onClick() }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (on) Color.White else TV.Ink2, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun TvBtn(text: String, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (!enabled) Color(0xFF2A3350) else if (primary) TV.Gold else TV.Royal
    Box(modifier.height(34.dp).clip(RoundedCornerShape(6.dp)).background(bg).border(1.dp, if (primary && enabled) TV.GoldDeep else TV.Hair, RoundedCornerShape(6.dp))
        .semantics { contentDescription = text }.clickable(enabled = enabled, role = Role.Button) { GameAudio.click(); onClick() }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(text.uppercase(), color = if (!enabled) TV.Grey else if (primary) TV.Ink else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp, maxLines = 1, softWrap = false)
    }
}

/* ------------------------------------------------------------------------------------------------ screen */

@Composable
fun BroadcastMatchScreen(vm: GameVM) {
    val s = vm.save ?: return
    val m = vm.sim
    @Suppress("UNUSED_VARIABLE") val tk = vm.simTick
    if (m == null) { ScreenScaffold(vm, "Live Match") { Sub("No match in progress.", Modifier.padding(16.dp)) }; return }
    if (!m.started) m.autoToss()
    m.manualDecisions = true
    val me = vm.userSideId() ?: m.userTeam.takeIf { it.isNotEmpty() } ?: s.userClubId
    val fx = vm.pendingFixture()
    val comp = vm.pendingComp()

    var overlay by remember { mutableStateOf<Overlay>(if (m.finished) Overlay.Final else Overlay.None) }
    var paused by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var centre by remember { mutableIntStateOf(0) }           // 0 pitch, 1 commentary, 2 highlights, 4 wagon, 5 history
    var scoreTab by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<BallEv?>(null) }
    var speedX by remember { mutableIntStateOf(vm.settings.playSpeed.let { if (it in SPEEDS) it else 1 }) }
    var animKey by remember { mutableIntStateOf(0) }
    var innSeen by remember { mutableIntStateOf(m.innings.size) }
    var mobileTab by remember { mutableIntStateOf(0) }       // portrait: 0 scorecard, 1 players, 2 centre
    val ballMs = (vm.settings.simSpeedMs * 4 / speedX).coerceIn(350, 2600)

    DisposableEffect(Unit) { GameAudio.setScene(GameAudio.Scene.MATCH); onDispose { GameAudio.setScene(GameAudio.Scene.MENU) } }
    LaunchedEffect(m.events.size) {
        vm.updateCommentary(m)
        m.lastEv?.let { e -> if (e.kind in setOf("FOUR", "SIX", "WICKET")) { GameAudio.onBat(e.kind, e.how); GameAudio.onOutcome(e.kind, e.how, e.milestone) } }
        GameAudio.setMood(com.cricketcoach.manager.audio.CrowdMood.of(m))
    }
    // opening decision: the user's side is bowling first / a batter is pending after a resumed match
    LaunchedEffect(Unit) {
        if (!m.finished) when {
            m.needsBatter() -> overlay = Overlay.NewBatter
            // resumed mid-innings: summarise the last completed over; fresh innings: "start of innings" (-1)
            m.needsBowler() -> overlay = Overlay.OverSummary(m.innings.size - 1, if ((m.cur?.balls ?: 0) > 0) (m.lastOverDone?.second ?: 0) - 1 else -1)
        }
    }

    fun afterBall(innBefore: Int, overBefore: Pair<Int, Int>?) {
        animKey++
        when {
            m.finished -> { vm.applyMatch(); overlay = Overlay.Final }
            m.innings.size != innBefore -> overlay = Overlay.InningsBreak
            m.needsBatter() -> { GameAudio.play("wicket", 0.6f); overlay = Overlay.NewBatter }
            // lastOverDone.second = number of completed overs (1 after the first over); BallEv.over is zero-based
            m.lastOverDone != overBefore -> m.lastOverDone?.let { overlay = Overlay.OverSummary(it.first, it.second - 1) }
        }
    }
    fun ball() {
        if (m.finished || overlay != Overlay.None) return
        val ib = m.innings.size; val ob = m.lastOverDone
        vm.stepBall()
        afterBall(ib, ob)
    }
    // Auto play: advances until a decision is required (over complete, wicket, innings break, result).
    LaunchedEffect(vm.autoPlay, speedX, overlay, paused, detail) {
        while (vm.autoPlay && !paused && overlay == Overlay.None && detail == null && !m.finished) { ball(); delay(ballMs.toLong()) }
    }
    BackHandler(enabled = true) {
        when { confirmExit -> confirmExit = false; detail != null -> detail = null; m.finished -> vm.leaveMatch(); centre != 0 -> centre = 0; else -> { vm.autoPlay = false; paused = !paused } }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(TV.Navy)) {
        val wide = maxWidth >= 640.dp
        val tallPortrait = maxHeight > 700.dp
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).windowInsetsPadding(WindowInsets.navigationBars)) {
            BroadcastHeader(m, fx, comp, s)
            if (wide) Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(Modifier.weight(0.40f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScorecardPanel(m, s, comp, scoreTab, { scoreTab = it }, Modifier.weight(1f))
                    BallTracker(m, Modifier.height(96.dp)) { detail = it }
                }
                Box(Modifier.weight(0.34f).fillMaxHeight()) { CentreArea(vm, m, s, centre, animKey, ballMs, { centre = 0 }, { detail = it }) }
                PlayerAnalysis(m, s, comp, Modifier.weight(0.26f).fillMaxHeight())
            } else Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(if (tallPortrait) 190.dp else 150.dp)) { CentreArea(vm, m, s, centre, animKey, ballMs, { centre = 0 }, { detail = it }) }
                BallTracker(m, Modifier.height(74.dp)) { detail = it }
                Row(Modifier.fillMaxWidth().clip(TV.panelShape).background(TV.Panel2)) {
                    for ((i, t) in listOf("Scorecard", "Players").withIndex()) TvTab(t, mobileTab == i, Modifier.weight(1f)) { mobileTab = i }
                }
                if (mobileTab == 0) ScorecardPanel(m, s, comp, scoreTab, { scoreTab = it }, Modifier.weight(1f)) else PlayerAnalysis(m, s, comp, Modifier.weight(1f))
            }
            StatusStrip(m)
            ControlBar(vm, m, centre, speedX, onCentre = { centre = if (centre == it) 0 else it }, onSpeed = { speedX = SPEEDS[(SPEEDS.indexOf(speedX) + 1) % SPEEDS.size]; vm.settings.playSpeed = speedX },
                onMenu = { vm.autoPlay = false; paused = true }, onBall = { ball() }, onAuto = { vm.autoPlay = !vm.autoPlay })
        }

        /* ---- decision overlays ---- */
        when (val o = overlay) {
            is Overlay.OverSummary -> OverSummaryOverlay(vm, m, s, comp, o.inn, o.over, me, onBall = { detail = it }) {
                overlay = Overlay.None; if (m.finished) overlay = Overlay.Final
            }
            Overlay.NewBatter -> NewBatterOverlay(m, s, comp) { pid ->
                if (m.sendBatter(pid)) {
                    vm.bump()
                    val c = m.cur; val od = m.lastOverDone
                    // wicket on the last ball of the over: show the over summary (and the bowler change) next
                    overlay = if (c != null && od != null && c.balls > 0 && c.balls % 6 == 0 && !c.overStarted && od.second == c.balls / 6) Overlay.OverSummary(od.first, od.second - 1) else Overlay.None
                }
            }
            Overlay.InningsBreak -> InningsBreakOverlay(m) { overlay = if (m.needsBowler()) Overlay.OverSummary(m.innings.size - 1, -1) else Overlay.None; innSeen = m.innings.size }
            Overlay.Final -> Box(Modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
                FinalScoreboard(m, s, onScorecard = { overlay = Overlay.None; scoreTab = 5; mobileTab = 0 }, onContinue = { vm.leaveMatch() }, modifier = Modifier.padding(16.dp))
            }
            Overlay.None -> Unit
        }
        if (m.finished && overlay == Overlay.None) Box(Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 10.dp)) { TvBtn("Result") { overlay = Overlay.Final } }

        detail?.let { e -> BallDetailDialog(vm, m, s, e) { detail = null } }

        if (paused && !m.finished) MatchMenu(vm, m, onResume = { paused = false }, on3d = { paused = false; vm.nav(Screen.Live3D) }, onSettings = { paused = false; vm.nav(Screen.SettingsScr) }, onExit = { confirmExit = true })
        if (confirmExit) DsConfirm("Exit match?", "The match is kept exactly at this ball and your career is saved. Resume it any time from the Match Centre or the Calendar.",
            "Exit match", danger = true, onConfirm = { confirmExit = false; paused = false; vm.exitMatch() }, onDismiss = { confirmExit = false })
    }
}

/* ------------------------------------------------------------------------------------------------ header */

@Composable
private fun BroadcastHeader(m: MatchSim, fx: Fixture?, comp: Competition?, s: CareerSave) {
    val inn = m.cur
    val bi = boardInfo(m)
    val matchName = fx?.let { f -> if (comp?.series == true) "${f.stage}" else f.stage.takeIf { it != "League" } ?: comp?.name ?: m.cfg.compName } ?: m.cfg.compName
    val date = fx?.date?.takeIf { it.isNotBlank() } ?: "Season ${s.year}"
    Row(Modifier.fillMaxWidth().height(46.dp).background(Brush.horizontalGradient(listOf(TV.Navy2, TV.Royal, TV.Navy2))).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ClubBadge(m.home, 30.dp)
        Column(Modifier.padding(horizontal = 6.dp).widthIn(max = 230.dp)) {
            Text("${m.home.short} v ${m.away.short}", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
            val sub = buildString {
                append(matchName); append(" · "); append(m.cfg.format)
                if (m.isTest && bi.phase.isNotEmpty()) append(" · ").append(bi.phase.replace(" · ", " "))
            }
            Text(sub, color = TV.Gold, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ClubBadge(m.away, 30.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).widthIn(min = 60.dp)) {
            Text(m.cfg.venue, color = Color.White.copy(alpha = 0.9f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$date · ${m.cfg.pitch.lowercase().replaceFirstChar { it.uppercase() }} pitch · ${m.cfg.weather}", color = Color.White.copy(alpha = 0.6f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (inn != null) Row(Modifier.weight(1.4f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            val innLabel = if (m.isTest) listOf("1st", "2nd", "3rd", "4th").getOrElse(m.innings.size - 1) { "${m.innings.size}th" } + " INNS" else if (m.innings.size == 1) "1ST INNS" else "2ND INNS"
            HeaderStat(innLabel, m.club(inn.batTeam).short, Color.White)
            HeaderStat("SCORE", inn.scoreText(), TV.Gold, big = true)
            HeaderStat("OVERS", inn.oversText() + if (!m.isTest) "/${inn.maxBalls / 6}" else "", Color.White)
            HeaderStat("CRR", fmtOv(inn.crr()), Color.White)
            inn.target?.let { t ->
                val need = t - inn.runs; val left = inn.maxBalls - inn.balls
                HeaderStat("TARGET", "$t", Color.White)
                HeaderStat("NEED", if (need > 0) "$need" else "0", TV.Gold)
                if (!m.isTest) HeaderStat("RRR", if (left > 0 && need > 0) fmtOv(need * 6.0 / left) else "-", Color.White)
            }
            if (m.isTest && m.innings.size > 1 && inn.target == null) bi.rate2?.let { HeaderStat(it.first, it.second, Color.White) }
        }
        Spacer(Modifier.width(6.dp))
        Row(Modifier.clip(RoundedCornerShape(4.dp)).background(if (m.finished) TV.Grey else TV.Live).padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!m.finished) { val pulse = rememberInfiniteTransition(label = "live").animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a"); Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = pulse.value))); Spacer(Modifier.width(4.dp)) }
            Text(if (m.finished) "RESULT" else "LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun HeaderStat(label: String, value: String, color: Color, big: Boolean = false) {
    Column(Modifier.padding(horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Lbl(label, Color.White.copy(alpha = 0.55f), 7)
        Text(value, color = color, fontSize = if (big) 17.sp else 12.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false, lineHeight = if (big) 18.sp else 13.sp)
    }
}

@Composable
private fun StatusStrip(m: MatchSim) {
    val bi = boardInfo(m)
    val inn = m.cur
    Row(Modifier.fillMaxWidth().height(24.dp).background(TV.Navy2).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (inn != null) {
            Text(m.club(inn.batTeam).short, color = TV.Gold, fontWeight = FontWeight.Black, fontSize = 11.sp)
            Text("  ${inn.scoreText()}  (${inn.oversText()} ov)", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
            Spacer(Modifier.width(14.dp))
        }
        Text(bi.situation, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        // T20/ODI: powerplay / middle / death; Test: "Day n • Session n" (spec: day & session for Test matches)
        if (inn != null) Text(m.phaseLabel().uppercase(), color = TV.Gold, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
    }
}

/* ------------------------------------------------------------------------------------------------ scorecard */

@Composable
private fun ScorecardPanel(m: MatchSim, s: CareerSave, comp: Competition?, tab: Int, onTab: (Int) -> Unit, modifier: Modifier) {
    val cur = m.cur ?: return
    var innIdx by remember(m.innings.size) { mutableIntStateOf(m.innings.size - 1) }
    val inn = m.innings.getOrElse(innIdx) { cur }
    Column(modifier.clip(TV.panelShape).background(TV.Panel)) {
        Row(Modifier.fillMaxWidth().background(TV.Panel2).horizontalScroll(rememberScrollState())) {
            for ((i, t) in listOf("Batting", "Bowling", "Fall of Wkts", "Partnerships", "Extras", "Summary").withIndex()) TvTab(t, tab == i) { onTab(i) }
        }
        if (m.innings.size > 1 && tab < 5) Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 6.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((i, it) in m.innings.withIndex()) {
                val on = i == innIdx
                Box(Modifier.clip(RoundedCornerShape(3.dp)).background(if (on) TV.Gold else TV.Panel2).clickable { innIdx = i }.padding(horizontal = 6.dp, vertical = 1.dp)) {
                    Text("${m.club(it.batTeam).short} ${it.scoreText()}", color = TV.Ink, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> BattingCard(m, inn, inn === cur)
                1 -> BowlingCard(m, inn, inn === cur)
                2 -> SimpleList(if (inn.fow.isEmpty()) listOf("No wicket has fallen yet.") else inn.fow.mapIndexed { i, f -> "${i + 1}.  $f" })
                3 -> SimpleList(if (inn.parts.isEmpty() && inn.partBalls == 0) listOf("Innings yet to begin.") else inn.parts + listOf("Current: ${inn.partRuns} (${inn.partBalls}) ${m.pn(inn.bc.getOrNull(inn.striker)?.pid ?: "")} & ${m.pn(inn.bc.getOrNull(inn.nonStriker)?.pid ?: "")}").filter { !it.startsWith("Current") || inn === cur })
                4 -> ExtrasCard(m, inn, innIdx)
                else -> MatchSummaryCard(m)
            }
        }
    }
}

@Composable
private fun SimpleList(lines: List<String>) = LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
    items(lines) { l -> Text(l, color = TV.Ink, fontSize = 10.sp, modifier = Modifier.padding(vertical = 3.dp)); HorizontalDivider(color = TV.PanelLine) }
}

@Composable
private fun RowScope.Cl(t: String, w: Float, color: Color = TV.Ink, bold: Boolean = false, align: TextAlign = TextAlign.End) =
    Text(t, color = color, fontSize = 9.5.sp, fontWeight = if (bold) FontWeight.Black else FontWeight.SemiBold, textAlign = align, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(w))

@Composable
private fun RowScope.H(t: String, w: Float, align: TextAlign = TextAlign.End) =
    Text(t.uppercase(), color = TV.Ink2, fontSize = 8.sp, fontWeight = FontWeight.Black, textAlign = align, maxLines = 1, modifier = Modifier.weight(w))

@Composable
private fun BattingCard(m: MatchSim, inn: Inns, live: Boolean) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Row(Modifier.fillMaxWidth().background(TV.Panel2).padding(horizontal = 6.dp, vertical = 3.dp)) { H("Batting", 3.2f, TextAlign.Start); H("Runs", 1f); H("Balls", 1f); H("4s", .7f); H("6s", .7f); H("SR", 1.1f) } }
        items(inn.bc.withIndex().toList()) { (i, c) ->
            val atCrease = live && !c.out && c.batted && ((i == inn.striker && inn.pendingSlot != 0) || (i == inn.nonStriker && inn.pendingSlot != 1))
            val striker = atCrease && i == inn.striker
            val bg = if (atCrease) Color(0xFFFFF4D1) else if (i % 2 == 0) Color.White else TV.Panel
            Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 6.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(3.2f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (atCrease) Box(Modifier.size(6.dp).clip(CircleShape).background(if (striker) TV.Gold else TV.Green)); if (atCrease) Spacer(Modifier.width(3.dp))
                        Text(m.pn(c.pid) + (if (striker) " *" else ""), color = if (atCrease) TV.Royal else TV.Ink, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(when { c.out -> c.how; atCrease -> if (striker) "batting · on strike" else "batting"; c.batted -> "not out"; else -> "yet to bat" }, color = if (c.out) TV.Live.copy(alpha = .85f) else TV.Ink2, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (c.batted || c.out) { Cl("${c.runs}", 1f, bold = true); Cl("${c.balls}", 1f); Cl("${c.fours}", .7f); Cl("${c.sixes}", .7f); Cl(sr(c), 1.1f) } else { Cl("", 1f); Cl("", 1f); Cl("", .7f); Cl("", .7f); Cl("", 1.1f) }
            }
            HorizontalDivider(color = TV.PanelLine)
        }
        item {
            Row(Modifier.fillMaxWidth().background(TV.Panel2).padding(horizontal = 6.dp, vertical = 4.dp)) {
                Text("Extras ${inn.extras}", color = TV.Ink2, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Total ${inn.scoreText()}  (${inn.oversText()} ov, RR ${fmtOv(inn.crr())})", color = TV.Ink, fontSize = 9.5.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun BowlingCard(m: MatchSim, inn: Inns, live: Boolean) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Row(Modifier.fillMaxWidth().background(TV.Panel2).padding(horizontal = 6.dp, vertical = 3.dp)) { H("Bowling", 3f, TextAlign.Start); H("O", .9f); H("M", .7f); H("R", .9f); H("W", .7f); H("Econ", 1.1f) } }
        val list = inn.bw.values.toList()
        if (list.isEmpty()) item { Text("No over bowled yet.", color = TV.Ink2, fontSize = 10.sp, modifier = Modifier.padding(8.dp)) }
        items(list.withIndex().toList()) { (i, b) ->
            val now = live && inn.overStarted && b.pid == inn.bowler
            Row(Modifier.fillMaxWidth().background(if (now) Color(0xFFE3ECFF) else if (i % 2 == 0) Color.White else TV.Panel).padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(3f), verticalAlignment = Alignment.CenterVertically) {
                    if (now) { Box(Modifier.size(6.dp).clip(CircleShape).background(TV.Live)); Spacer(Modifier.width(3.dp)) }
                    Text(m.pn(b.pid), color = if (now) TV.Royal else TV.Ink, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Cl(b.oversText, .9f); Cl("${b.maidens}", .7f); Cl("${b.runs}", .9f); Cl("${b.wk}", .7f, bold = true); Cl(fmtOv(b.econ), 1.1f)
            }
            HorizontalDivider(color = TV.PanelLine)
        }
    }
}

@Composable
private fun ExtrasCard(m: MatchSim, inn: Inns, innIdx: Int) {
    val evs = m.events.filter { it.inn == innIdx }
    val wd = evs.filter { it.kind == "WIDE" }.sumOf { it.runs }; val nb = evs.filter { it.kind == "NB" }.sumOf { it.runs - it.bat }
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Extras conceded by ${m.club(inn.bowlTeam).name}", color = TV.Ink, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((k, v) in listOf("Wides" to wd, "No balls" to nb, "Byes" to 0, "Leg byes" to 0, "Total" to inn.extras)) Column(Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.dp, TV.PanelLine, RoundedCornerShape(4.dp)).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Lbl(k, TV.Ink2, 7); Text("$v", color = if (k == "Total") TV.Royal else TV.Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
            }
        }
        Text("Wides and no balls are recorded per delivery. Byes and leg byes are not produced by the match engine.", color = TV.Ink2, fontSize = 8.5.sp)
        HorizontalDivider(color = TV.PanelLine)
        Text("Free hits: ${evs.count { it.kind == "NB" } }  ·  Illegal deliveries: ${evs.count { !it.legal }}", color = TV.Ink, fontSize = 9.5.sp)
    }
}

@Composable
private fun MatchSummaryCard(m: MatchSim) {
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        item { Text("${m.home.name} v ${m.away.name}", color = TV.Ink, fontSize = 11.sp, fontWeight = FontWeight.Black); Text("${m.cfg.compName} · ${m.cfg.format} · ${m.cfg.venue}", color = TV.Ink2, fontSize = 8.5.sp) }
        item { if (m.tossWinner.isNotEmpty()) Text("Toss: ${m.club(m.tossWinner).name} chose to ${if (m.decision == "BAT") "bat" else "bowl"}", color = TV.Ink, fontSize = 9.5.sp, modifier = Modifier.padding(vertical = 3.dp)) }
        items(m.innings.withIndex().toList()) { (i, inn) ->
            val top = inn.bc.filter { it.batted }.sortedByDescending { it.runs }.take(2).joinToString(", ") { "${m.pn(it.pid)} ${it.runs}${if (!it.out) "*" else ""} (${it.balls})" }
            val bw = inn.bw.values.sortedWith(compareByDescending<BowlCard> { it.wk }.thenBy { it.runs }).take(2).joinToString(", ") { "${m.pn(it.pid)} ${it.wk}/${it.runs}" }
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.dp, TV.PanelLine, RoundedCornerShape(4.dp)).padding(6.dp)) {
                Row { Text("${m.club(inn.batTeam).short}  ${inn.scoreText()}", color = TV.Royal, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)); Text("${inn.oversText()} ov", color = TV.Ink2, fontSize = 9.sp) }
                if (top.isNotEmpty()) Text(top, color = TV.Ink, fontSize = 9.sp); if (bw.isNotEmpty()) Text(bw, color = TV.Ink2, fontSize = 9.sp)
            }
        }
        item { if (m.rainNote.isNotEmpty()) Text(m.rainNote, color = TV.Blue, fontSize = 9.sp) }
        item { if (m.finished) Text(m.resultText, color = TV.Ink, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp)) }
        item { Lbl("Key events", TV.Ink2, 8, Modifier.padding(top = 6.dp)) }
        items(m.keyEvents.reversed().take(60)) { Text("• $it", color = TV.Ink, fontSize = 9.sp, modifier = Modifier.padding(vertical = 1.dp)) }
    }
}

/* ------------------------------------------------------------------------------------------------ ball tracker */

@Composable
private fun BallChip(e: BallEv, size: Dp = 22.dp, onClick: () -> Unit) {
    val col = ballChipColor(e)
    Box(Modifier.size(size).clip(CircleShape).background(col).border(if (e.kind == "WICKET") 2.dp else 0.dp, if (e.kind == "WICKET") Color.White else Color.Transparent, CircleShape)
        .semantics { contentDescription = "Ball ${e.over}.${e.ball} ${Delivery.label(e)}" }.clickable(role = Role.Button) { GameAudio.click(); onClick() }, contentAlignment = Alignment.Center) {
        Text(Delivery.chip(e), color = Color.White, fontSize = if (Delivery.chip(e).length > 2) 7.sp else 9.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun BallTracker(m: MatchSim, modifier: Modifier, onBall: (BallEv) -> Unit) {
    val inn = m.cur ?: return
    val innIdx = m.innings.size - 1
    val curOver = inn.balls / 6
    val overs = remember(m.events.size, innIdx) {
        val byOver = m.events.filter { it.inn == innIdx }.groupBy { it.over }
        (byOver.keys + (if (inn.overStarted || inn.balls == 0) listOf(curOver) else emptyList())).distinct().sortedDescending().take(4).map { it to (byOver[it] ?: emptyList()) }
    }
    Column(modifier.clip(TV.panelShape).background(TV.Panel)) {
        Row(Modifier.fillMaxWidth().background(TV.Panel2).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(TV.Royal)); Spacer(Modifier.width(4.dp))
            Lbl("Ball tracker", TV.Ink, 8, Modifier.weight(1f))
            Lbl("tap a ball for its history", TV.Ink2, 7)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (overs.isEmpty()) Text("First ball of the innings to come.", color = TV.Ink2, fontSize = 9.sp)
            for ((ov, list) in overs) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    val bw = list.firstOrNull()?.bowler ?: inn.bowler.takeIf { inn.overStarted } ?: ""
                    Text("Ov ${ov + 1}", color = TV.Ink2, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(30.dp))
                    Text(m.pn(bw).take(12), color = TV.Ink, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(58.dp))
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (e in list) BallChip(e) { onBall(e) }
                        val legal = list.count { it.legal }
                        if (ov == curOver && inn.overStarted) repeat((6 - legal).coerceAtLeast(0)) { Box(Modifier.size(22.dp).clip(CircleShape).border(1.dp, TV.PanelLine, CircleShape)) }
                    }
                    val r = list.sumOf { it.runs }; val w = list.count { it.kind == "WICKET" }
                    Text("$r${if (w > 0) "-$w" else ""}", color = TV.Ink, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 4.dp).width(26.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------ centre: pitch / panels */

@Composable
private fun CentreArea(vm: GameVM, m: MatchSim, s: CareerSave, centre: Int, animKey: Int, ballMs: Int, onClose: () -> Unit, onBall: (BallEv) -> Unit) {
    Column(Modifier.fillMaxSize().clip(TV.panelShape).background(TV.Navy2).border(1.dp, TV.Hair, TV.panelShape)) {
        if (centre == 0) {
            PitchView2D(m, animKey, ballMs, Modifier.weight(1f).fillMaxWidth())
            val e = m.lastEv
            Row(Modifier.fillMaxWidth().background(Color(0xCC050B22)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (e != null && e.inn == m.innings.size - 1) {
                    Text("${e.over}.${e.ball}", color = TV.Gold, fontSize = 9.sp, fontWeight = FontWeight.Black); Spacer(Modifier.width(6.dp))
                    Text(vm.comLines.firstOrNull { it.evId == e.id && it.kind == e.kind }?.text ?: e.text, color = Color.White, fontSize = 9.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { onBall(e) })
                } else Text(boardInfo(m).situation, color = Color.White.copy(alpha = .8f), fontSize = 9.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        } else {
            val title = when (centre) { 1 -> "Commentary"; 2 -> "Highlights"; 4 -> "Wagon wheel & charts"; else -> "Ball history" }
            Row(Modifier.fillMaxWidth().height(26.dp).background(TV.Royal).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Lbl(title, Color.White, 9, Modifier.weight(1f))
                Box(Modifier.size(26.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { Text("✕", color = Color.White, fontSize = 12.sp) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().background(TV.Panel)) {
                when (centre) {
                    1 -> if (vm.comLines.isNotEmpty()) CommentaryFeed(vm) else LazyColumn(Modifier.padding(horizontal = 8.dp)) { items(m.com.take(150)) { line -> Text(line, color = TV.Ink, fontSize = 10.sp, modifier = Modifier.padding(vertical = 3.dp)); HorizontalDivider(color = TV.PanelLine) } }
                    2 -> {
                        val hl = m.events.filter { it.highlight }.reversed()
                        if (hl.isEmpty()) Text("Boundaries, wickets and milestones appear here.", color = TV.Ink2, fontSize = 10.sp, modifier = Modifier.padding(10.dp))
                        LazyColumn(Modifier.padding(horizontal = 6.dp)) { items(hl.take(150)) { e -> HistoryRow(m, e) { onBall(e) } } }
                    }
                    4 -> ChartsTabs(m)
                    else -> {
                        val all = m.events.reversed()
                        LazyColumn(Modifier.padding(horizontal = 6.dp)) {
                            items(all.take(400)) { e -> HistoryRow(m, e) { onBall(e) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(m: MatchSim, e: BallEv, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(4.dp)).background(Color.White).clickable { onClick() }.padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
        BallChip(e, 20.dp, onClick)
        Spacer(Modifier.width(6.dp))
        Text("${e.over}.${e.ball}", color = TV.Ink2, fontSize = 8.5.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(30.dp))
        Column(Modifier.weight(1f)) {
            Text("${m.pn(e.bowler)} to ${m.pn(e.batter)} · ${Delivery.label(e)}", color = TV.Ink, fontSize = 9.5.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (e.shot.isNotEmpty()) "${e.length} · ${e.line.lowercase()} · ${e.shot}" else e.text, color = TV.Ink2, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${e.scoreRuns}/${e.scoreWk}", color = TV.Ink2, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChartsTabs(m: MatchSim) {
    var chart by remember { mutableIntStateOf(0) }
    var sel by remember(m.innings.size) { mutableIntStateOf(m.innings.size - 1) }
    val innNames = m.innings.mapIndexed { i, it -> "${m.club(it.batTeam).short}${if (m.isTest) " (${i + 1})" else ""}" }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(TV.Panel2)) { for ((i, t) in listOf("Wagon", "Manhattan", "Worm", "Pitch map").withIndex()) TvTab(t, chart == i) { chart = i } }
        if (chart == 0 || chart == 3) Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { for ((i, n) in innNames.withIndex()) Box(Modifier.clip(RoundedCornerShape(3.dp)).background(if (i == sel) TV.Gold else Color.White).clickable { sel = i }.padding(horizontal = 6.dp, vertical = 2.dp)) { Text(n, color = TV.Ink, fontSize = 8.sp, fontWeight = FontWeight.Black) } }
        Box(Modifier.weight(1f).fillMaxWidth().padding(4.dp)) {
            when (chart) {
                0 -> WagonWheel(m.events.filter { it.inn == sel }, Modifier.fillMaxSize())
                1 -> Manhattan(m.events, innNames, if (m.isTest) 0 else m.cfg.overs, Modifier.fillMaxSize())
                2 -> Worm(m.events, innNames, if (m.isTest) 0 else m.cfg.overs, Modifier.fillMaxSize())
                else -> PitchMap(m.events.filter { it.inn == sel }, Modifier.fillMaxSize(), dark = false)
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------ 2D pitch */

private val FIELD_PACE = arrayOf(160f to 17f, 150f to 18f, 95f to 26f, 55f to 28f, 18f to 30f, 342f to 30f, 300f to 27f, 255f to 24f, 205f to 58f)
private val FIELD_SPIN = arrayOf(150f to 7f, 120f to 50f, 95f to 24f, 55f to 26f, 15f to 28f, 345f to 28f, 300f to 25f, 250f to 22f, 210f to 52f)

/** Top-down broadcast pitch: fielders, batters, bowler, keeper, the last ball's pitch point, shot direction and travel. */
@Composable
private fun PitchView2D(m: MatchSim, animKey: Int, ballMs: Int, modifier: Modifier) {
    val inn = m.cur
    val e = m.lastEv?.takeIf { inn != null && it.inn == m.innings.size - 1 }
    val prog = remember { Animatable(1f) }
    LaunchedEffect(animKey) { if (animKey > 0) { prog.snapTo(0f); prog.animateTo(1f, tween((ballMs * 0.85f).toInt().coerceIn(300, 1800), easing = LinearEasing)) } }
    val spin = inn?.let { m.players[it.bowler]?.bowlType == "SPIN" } ?: false
    val batCol = inn?.let { Color(m.club(it.batTeam).primary) } ?: TV.Gold
    val fieldCol = inn?.let { Color(m.club(it.bowlTeam).primary) }?.let { if (it == batCol) Color(0xFF1565C0) else it } ?: TV.Blue
    val p = prog.value
    Canvas(modifier.background(Brush.radialGradient(listOf(TV.Pitch, TV.PitchDark, Color(0xFF17421F))))) {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = min(size.width, size.height) / 2f * 0.95f
        // outfield rings + 30-yard circle
        drawCircle(Color(0xFF3A9A47), r, Offset(cx, cy)); drawCircle(Color(0xFF358F42), r * 0.5f, Offset(cx, cy))
        drawCircle(Color.White.copy(alpha = .85f), r, Offset(cx, cy), style = Stroke(2.5f))
        drawCircle(Color.White.copy(alpha = .45f), r * 0.5f, Offset(cx, cy), style = Stroke(1.5f))
        // pitch strip: striker end at bottom (cy + pl/2), bowler end at top
        val pl = r * 0.46f; val pw = r * 0.09f
        drawRect(TV.Strip, Offset(cx - pw / 2, cy - pl / 2), Size(pw, pl))
        for (dy in listOf(-pl / 2, pl / 2)) drawLine(Color.White, Offset(cx - pw / 2, cy + dy), Offset(cx + pw / 2, cy + dy), 1.5f)
        drawRect(Color(0xFF3B3B3B), Offset(cx - 2.5f, cy + pl / 2 - 2f), Size(5f, 4f)); drawRect(Color(0xFF3B3B3B), Offset(cx - 2.5f, cy - pl / 2 - 2f), Size(5f, 4f))
        // fielders: angle 0 = straight past bowler (up), off side right
        fun at(angle: Float, dist: Float): Offset { val a = Math.toRadians(angle.toDouble()); val d = (dist / 70f).coerceAtMost(0.98f) * r; return Offset(cx + (sin(a) * d).toFloat(), cy + pl / 2 - (cos(a) * d).toFloat()) }
        val pos = if (spin) FIELD_SPIN else FIELD_PACE
        for ((a, d) in pos) { val o = at(a, d); drawCircle(Color.Black.copy(alpha = .25f), 6f, o + Offset(1.5f, 2f)); drawCircle(fieldCol, 6f, o); drawCircle(Color.White, 6f, o, style = Stroke(1.2f)) }
        val kp = Offset(cx, cy + pl / 2 + r * 0.07f); drawCircle(fieldCol, 6f, kp); drawCircle(Color.White, 6f, kp, style = Stroke(1.2f))
        // batters & bowler
        val strikerPos = Offset(cx + pw * 0.7f, cy + pl / 2 - r * 0.03f); val nonPos = Offset(cx - pw * 0.7f, cy - pl / 2 + r * 0.03f)
        val runUp = if (p < 0.35f) (0.35f - p) / 0.35f else 0f
        val bowlerPos = Offset(cx - pw * 0.8f, cy - pl / 2 - r * 0.02f - runUp * r * 0.16f)
        for (o in listOf(strikerPos, nonPos)) { drawCircle(batCol, 6.5f, o); drawCircle(TV.Gold, 6.5f, o, style = Stroke(1.5f)) }
        drawCircle(fieldCol, 6.5f, bowlerPos); drawCircle(Color.White, 6.5f, bowlerPos, style = Stroke(1.5f))
        // ball: run-up (0-.35) → delivery to pitch point (.35-.6) → to bat (.6-.7) → shot (.7-1)
        if (e != null) {
            val pitchPt = Offset(cx + e.pitchX * pw * 0.5f, cy + pl / 2 - e.pitchY * pl)
            val batPt = Offset(cx, cy + pl / 2 - r * 0.012f)
            val a = Math.toRadians(e.angle.toDouble()); val sd = min(1.2f, e.dist) * r * 0.98f
            val end = Offset(cx + (sin(a) * sd).toFloat(), cy + pl / 2 - (cos(a) * sd).toFloat())
            val ballPos = when {
                p < 0.35f -> Offset(bowlerPos.x + pw * 0.3f, bowlerPos.y)
                p < 0.6f -> { val u = (p - 0.35f) / 0.25f; Offset(bowlerPos.x + (pitchPt.x - bowlerPos.x) * u, bowlerPos.y + (pitchPt.y - bowlerPos.y) * u) }
                p < 0.7f -> { val u = (p - 0.6f) / 0.1f; Offset(pitchPt.x + (batPt.x - pitchPt.x) * u, pitchPt.y + (batPt.y - pitchPt.y) * u) }
                else -> { val u = ((p - 0.7f) / 0.3f).let { it * (2 - it) }; if (e.kind == "WIDE" || (e.bat == 0 && e.kind != "WICKET")) Offset(kp.x, batPt.y + (kp.y - batPt.y) * u) else Offset(batPt.x + (end.x - batPt.x) * u, batPt.y + (end.y - batPt.y) * u) }
            }
            if (p >= 0.6f) drawCircle(Color.White, 3.5f, pitchPt); if (p >= 0.6f) drawCircle(TV.Live, 3.5f, pitchPt, style = Stroke(1.5f))
            if (p > 0.7f && e.bat > 0 || (p > 0.7f && e.kind == "WICKET" && e.dist > 0.2f)) {
                val col = when (e.kind) { "SIX" -> TV.Purple; "FOUR" -> TV.Blue; "WICKET" -> TV.Live; else -> Color.White }
                drawLine(col, batPt, ballPos, 2.5f)
            }
            drawCircle(Color.Black.copy(alpha = .3f), 4.5f, ballPos + Offset(1f, 1.5f)); drawCircle(Color(0xFFE53935), 4f, ballPos); drawCircle(Color.White, 4f, ballPos, style = Stroke(1f))
            if (p >= 1f && e.kind in setOf("FOUR", "SIX", "WICKET")) {
                val col = when (e.kind) { "SIX" -> TV.Purple; "FOUR" -> TV.Blue; else -> TV.Live }
                drawCircle(col.copy(alpha = .35f), 14f, end); drawCircle(col, 14f, end, style = Stroke(2f))
            }
        }
    }
}

/** Where the deliveries pitched (striker's view: off side right). */
@Composable
fun PitchMap(events: List<BallEv>, modifier: Modifier, dark: Boolean = true, highlight: BallEv? = null) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val pw = w * 0.56f; val px = (w - pw) / 2f
        drawRect(if (dark) Color(0xFF0B1A3C) else TV.Panel2, Offset.Zero, size)
        drawRect(TV.Strip, Offset(px, 0f), Size(pw, h))
        // length bands
        val bands = listOf(0.05f to "Yorker", 0.18f to "Full", 0.34f to "Good", 0.46f to "Back of", 0.58f to "Short", 0.7f to "Bouncer")
        for ((f, _) in bands) drawLine(Color.Black.copy(alpha = .15f), Offset(px, h - f * h * 1.2f), Offset(px + pw, h - f * h * 1.2f), 1f)
        drawRect(Color(0xFF3B3B3B), Offset(w / 2 - 4f, h - 6f), Size(8f, 5f))
        drawLine(Color.White, Offset(px, h - 10f), Offset(px + pw, h - 10f), 1.5f)
        val list = events.filter { it.shot.isNotEmpty() }
        for (e in list) {
            val o = Offset(w / 2 + e.pitchX * pw * 0.5f, h - 10f - e.pitchY * h * 1.2f)
            val col = when (e.kind) { "WICKET" -> TV.Live; "FOUR" -> TV.Blue; "SIX" -> TV.Purple; "WIDE", "NB" -> Color(0xFFF59E0B); "RUN" -> TV.Green; else -> Color.White.copy(alpha = .9f) }
            val hi = highlight != null && highlight.id == e.id
            drawCircle(col, if (hi) 7f else 4f, o); if (hi) drawCircle(Color.Black, 7f, o, style = Stroke(2f)) else drawCircle(Color.Black.copy(alpha = .4f), 4f, o, style = Stroke(1f))
        }
    }
}

/* ------------------------------------------------------------------------------------------------ right: player analysis */

@Composable
private fun PlayerAnalysis(m: MatchSim, s: CareerSave, comp: Competition?, modifier: Modifier) {
    val inn = m.cur ?: return
    val innIdx = m.innings.size - 1
    val evs = m.events.filter { it.inn == innIdx }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val bowler = inn.bowler.takeIf { it.isNotEmpty() }
        BowlerPanel(m, s, comp, inn, bowler, evs.filter { it.bowler == bowler })
        val st = inn.bc.getOrNull(inn.striker); val ns = inn.bc.getOrNull(inn.nonStriker)
        val stOk = st != null && inn.pendingSlot != 0 && !st.out; val nsOk = ns != null && inn.pendingSlot != 1 && !ns.out
        BatterPanel(m, s, comp, inn, if (stOk) st else null, true, evs)
        BatterPanel(m, s, comp, inn, if (nsOk) ns else null, false, evs)
    }
}

@Composable
private fun PanelHead(title: String, right: String = "", color: Color = TV.Royal) = Row(Modifier.fillMaxWidth().background(color).padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(title, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    if (right.isNotEmpty()) Text(right, color = TV.Gold, fontSize = 10.5.sp, fontWeight = FontWeight.Black, maxLines = 1)
}

@Composable
private fun MiniStat(k: String, v: String, modifier: Modifier = Modifier) = Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { Lbl(k, TV.Ink2, 7); Text(v, color = TV.Ink, fontSize = 10.5.sp, fontWeight = FontWeight.Black, maxLines = 1) }

@Composable
private fun Meter(label: String, frac: Float, color: Color, modifier: Modifier = Modifier) = Column(modifier) {
    Row { Lbl(label, TV.Ink2, 7, Modifier.weight(1f)); Text("${(frac * 100).toInt()}", color = TV.Ink2, fontSize = 7.5.sp, fontWeight = FontWeight.Bold) }
    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(TV.PanelLine)) { Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0.02f, 1f)).background(color)) }
}

private fun formLabel(f: Int) = when { f >= 80 -> "Hot"; f >= 60 -> "Good"; f >= 40 -> "Average"; else -> "Poor" }

@Composable
private fun BowlerPanel(m: MatchSim, s: CareerSave, comp: Competition?, inn: Inns, pid: String?, evs: List<BallEv>) {
    val p = pid?.let { s.players[it] }
    Column(Modifier.fillMaxWidth().clip(TV.panelShape).background(TV.Panel)) {
        val card = pid?.let { inn.bw[it] }
        PanelHead(if (p != null) "${p.name}  ·  ${if (p.bowlType == "SPIN") "Spin" else "Pace"}" else "Bowler", if (card != null) "${card.wk}/${card.runs}" else "")
        if (p == null || card == null) { Text(if (m.needsBowler()) "Choose the bowler for this over." else "Bowler to be confirmed at the start of the over.", color = TV.Ink2, fontSize = 9.sp, modifier = Modifier.padding(8.dp)); return@Column }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) { MiniStat("O", card.oversText, Modifier.weight(1f)); MiniStat("M", "${card.maidens}", Modifier.weight(1f)); MiniStat("R", "${card.runs}", Modifier.weight(1f)); MiniStat("W", "${card.wk}", Modifier.weight(1f)); MiniStat("Econ", fmtOv(card.econ), Modifier.weight(1.2f)) }
        val rec = comp?.bowlS?.get(p.id)
        Text(if (rec != null && rec.balls > 0) "${comp.name.take(18)}: ${rec.wk} w · avg ${f1(rec.avg())} · econ ${f2(rec.econ())}" else "Career: ${p.careerWk} w in ${p.careerM} m", color = TV.Ink2, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 7.dp))
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PitchMap(evs, Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(4.dp)), dark = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Meter("Stamina", p.fitness / 100f, if (p.fitness > 60) TV.Green else TV.Live)
                Meter("Form · ${formLabel(p.form)}", p.form / 100f, TV.Blue)
                Meter("Workload · spell ${inn.spell[p.id] ?: 0}", ((inn.spell[p.id] ?: 0) / 6f).coerceAtMost(1f), TV.Gold)
                val dots = evs.count { it.legal && it.runs == 0 }
                Text("${evs.size} balls · $dots dots · ${evs.count { it.kind == "FOUR" || it.kind == "SIX" }} bdry", color = TV.Ink2, fontSize = 7.5.sp)
            }
        }
    }
}

@Composable
private fun BatterPanel(m: MatchSim, s: CareerSave, comp: Competition?, inn: Inns, c: BatCard?, striker: Boolean, evs: List<BallEv>) {
    val p = c?.let { s.players[it.pid] }
    Column(Modifier.fillMaxWidth().clip(TV.panelShape).background(TV.Panel)) {
        val pos = c?.let { inn.bc.indexOf(it) + 1 } ?: 0
        PanelHead(if (p != null) "$pos  ${p.name}" + (if (striker) "  *" else "") else if (striker) "Striker" else "Non-striker", if (c != null) "${c.runs} (${c.balls})" else "", if (striker) TV.Royal else TV.Navy2)
        if (p == null || c == null) { Text("Waiting for the next batter.", color = TV.Ink2, fontSize = 9.sp, modifier = Modifier.padding(8.dp)); return@Column }
        val mine = evs.filter { it.batter == c.pid }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) { MiniStat("Runs", "${c.runs}", Modifier.weight(1f)); MiniStat("Balls", "${c.balls}", Modifier.weight(1f)); MiniStat("4s", "${c.fours}", Modifier.weight(.8f)); MiniStat("6s", "${c.sixes}", Modifier.weight(.8f)); MiniStat("SR", sr(c), Modifier.weight(1.2f)) }
        Text((comp?.let { batSummary(it, p) } ?: "") + "  ·  ${p.role}", color = TV.Ink2, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 7.dp))
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WagonWheel(mine, Modifier.weight(1f).height(72.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val agg = m.club(inn.batTeam).aggression
                val attackBalls = mine.count { it.bat >= 4 || (it.shot.contains("drive", true) || it.shot.contains("loft", true) || it.shot.contains("pull", true) || it.shot.contains("sweep", true) || it.shot.contains("cut", true)) }
                val aggr = if (mine.isEmpty()) (0.3f + agg * 0.25f) else (attackBalls.toFloat() / mine.size).coerceIn(0.05f, 1f)
                Meter("Aggression", aggr, if (aggr > 0.6f) Color(0xFFF59E5B) else TV.Blue)
                Meter("Form · ${formLabel(p.form)}", p.form / 100f, TV.Green)
                Text("Approach: ${when (agg) { 0 -> "Defensive"; 2 -> "Attacking"; else -> "Balanced" }}", color = TV.Ink, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                val dots = mine.count { it.legal && it.bat == 0 && it.kind != "WICKET" }
                Text("Dots ${dots} · Def ${mine.count { it.shot.contains("defensive", true) || it.shot.contains("block", true) || it.shot == "Left alone" }} · Bdry ${c.fours + c.sixes}", color = TV.Ink2, fontSize = 7.5.sp, maxLines = 1)
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------ ball detail */

@Composable
private fun BallDetailDialog(vm: GameVM, m: MatchSim, s: CareerSave, e: BallEv, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(0.94f).clip(RoundedCornerShape(8.dp)).background(TV.Panel).border(1.dp, TV.Gold, RoundedCornerShape(8.dp))) {
            Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(TV.Navy2, TV.Royal))).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BallChip(e, 26.dp) {}
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Over ${e.over}.${e.ball}  ·  ${Delivery.label(e)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text("${m.pn(e.bowler)} to ${m.pn(e.batter)}  ·  ${m.club(e.batTeam).short} ${e.scoreRuns}/${e.scoreWk} after this ball", color = TV.Gold, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(28.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { Text("✕", color = Color.White, fontSize = 14.sp) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PitchMap(listOf(e), Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(4.dp)), dark = true, highlight = e)
                    WagonWheel(if (e.bat > 0 || e.kind == "WICKET") listOf(e) else emptyList(), Modifier.weight(1f).height(120.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailTile("Runs", "${e.runs}" + if (e.extras > 0) " (${e.extras} extra)" else "", Modifier.weight(1f))
                    DetailTile("Line", e.line.ifEmpty { "-" }, Modifier.weight(1.2f))
                    DetailTile("Length", e.length.ifEmpty { "-" }, Modifier.weight(1.2f))
                    DetailTile("Speed", if (e.speed > 0) "${e.speed} km/h" else "-", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailTile("Shot / response", e.shot.ifEmpty { "-" }, Modifier.weight(2f))
                    DetailTile("Footwork", e.foot.ifEmpty { "-" }, Modifier.weight(1.1f))
                    DetailTile("Approach", e.approach.ifEmpty { "-" }, Modifier.weight(1f))
                }
                val dirTxt = if (e.bat > 0 || (e.kind == "WICKET" && e.dist > 0.2f)) {
                    val sector = when (((e.angle % 360) + 360) % 360) { in 0f..22.5f, in 337.5f..360f -> "straight down the ground"; in 22.5f..67.5f -> "through the off side (mid-off / cover)"; in 67.5f..112.5f -> "square on the off side (point)"; in 112.5f..157.5f -> "behind square on the off side (third man)"; in 157.5f..202.5f -> "fine behind the wicket"; in 202.5f..247.5f -> "behind square on the leg side (fine leg)"; in 247.5f..292.5f -> "square on the leg side"; else -> "through mid-wicket / mid-on" }
                    "Ball went $sector" + (if (e.kind == "SIX") ", over the rope" else if (e.kind == "FOUR") ", to the boundary" else ", ${"%.0f".format(e.dist * 70)} m")
                } else if (e.kind == "WIDE") "Through to the keeper - no contact" else if (e.shot == "Left alone") "Left through to the keeper" else "Blocked in front of the stumps"
                DetailTile("Where it went", dirTxt, Modifier.fillMaxWidth())
                if (e.kind == "WICKET") DetailTile("Dismissal", "${s.players[e.outPid ?: e.batter]?.name ?: m.pn(e.batter)}  ${e.how}" + (e.fielderPid?.let { " · fielder ${m.pn(it)}" } ?: ""), Modifier.fillMaxWidth(), accent = TV.Live)
                if (e.milestone) DetailTile("Milestone", "Landmark reached on this ball", Modifier.fillMaxWidth(), accent = TV.Gold)
                DetailTile("Why", e.note.ifEmpty { "-" }, Modifier.fillMaxWidth())
                val com = vm.comLines.filter { it.evId == e.id }.reversed().map { it.text }.ifEmpty { listOf(e.text) }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.dp, TV.PanelLine, RoundedCornerShape(4.dp)).padding(8.dp)) {
                    Lbl("Commentary", TV.Ink2, 7)
                    for (l in com) Text(l, color = TV.Ink, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TvBtn("Close", primary = false) { onClose() } }
            }
        }
    }
}

@Composable
private fun DetailTile(k: String, v: String, modifier: Modifier = Modifier, accent: Color = TV.Royal) = Column(modifier.clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.dp, TV.PanelLine, RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 5.dp)) {
    Lbl(k, accent, 7); Text(v, color = TV.Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}

/* ------------------------------------------------------------------------------------------------ over summary + bowler change */

@Composable
private fun OverlayFrame(title: String, subtitle: String, maxW: Dp = 820.dp, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xB3020614)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = maxW).fillMaxWidth(0.96f).fillMaxHeight(0.94f).clip(RoundedCornerShape(10.dp)).background(TV.Panel).border(1.5.dp, TV.Gold, RoundedCornerShape(10.dp))
            .semantics { contentDescription = "Overlay: $title" }) {
            Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(TV.Navy2, TV.Royal, TV.Navy2))).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title.uppercase(), color = TV.Gold, fontFamily = Display, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(subtitle, color = Color.White.copy(alpha = .85f), fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            content()
        }
    }
}

@Composable
private fun OverSummaryOverlay(vm: GameVM, m: MatchSim, s: CareerSave, comp: Competition?, innIdx: Int, over: Int, me: String, onBall: (BallEv) -> Unit, onDone: () -> Unit) {
    val inn = m.innings.getOrNull(innIdx) ?: run { onDone(); return }
    val balls = if (over >= 0) m.overEvents(innIdx, over) else emptyList()
    val userBowling = m.needsBowler()
    var chosen by remember { mutableStateOf<String?>(null) }
    val runs = balls.sumOf { it.runs }; val wk = balls.count { it.kind == "WICKET" }; val ex = balls.sumOf { it.extras }
    val lastBowler = balls.firstOrNull()?.bowler ?: inn.lastBowler
    val bwCard = inn.bw[lastBowler]
    val startOfInnings = over < 0
    OverlayFrame(if (startOfInnings) "Start of innings" else "End of over ${over + 1}", "${m.club(inn.batTeam).name} ${inn.scoreText()} (${inn.oversText()} ov)  ·  CRR ${fmtOv(inn.crr())}" + (inn.target?.let { t -> val need = t - inn.runs; val left = inn.maxBalls - inn.balls; "  ·  need $need" + if (!m.isTest && left > 0) " from $left balls · RRR ${fmtOv(need * 6.0 / left)}" else "" } ?: "")) {
        Row(Modifier.weight(1f).fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!startOfInnings) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        DetailTile("Over", "${over + 1}", Modifier.weight(1f)); DetailTile("Runs", "$runs", Modifier.weight(1f)); DetailTile("Wickets", "$wk", Modifier.weight(1f), if (wk > 0) TV.Live else TV.Royal); DetailTile("Extras", "$ex", Modifier.weight(1f))
                        DetailTile("Score", inn.scoreText(), Modifier.weight(1.2f), TV.GoldDeep); DetailTile("CRR", fmtOv(inn.crr()), Modifier.weight(1f))
                        inn.target?.let { t -> val need = t - inn.runs; val left = inn.maxBalls - inn.balls; DetailTile("RRR", if (!m.isTest && left > 0 && need > 0) fmtOv(need * 6.0 / left) else "-", Modifier.weight(1f)) }
                    }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.dp, TV.PanelLine, RoundedCornerShape(4.dp))) {
                        Row(Modifier.fillMaxWidth().background(TV.Panel2).padding(horizontal = 8.dp, vertical = 3.dp)) { Lbl("Ball by ball · ${m.pn(lastBowler)}", TV.Ink, 8, Modifier.weight(1f)); Lbl("tap for history", TV.Ink2, 7) }
                        for (e in balls) Row(Modifier.fillMaxWidth().clickable { onBall(e) }.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            BallChip(e, 20.dp) { onBall(e) }; Spacer(Modifier.width(6.dp))
                            Text("${e.over}.${e.ball}", color = TV.Ink2, fontSize = 8.5.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${m.pn(e.batter)} · ${Delivery.label(e)}" + (if (e.extras > 0) " (+${e.extras} extra)" else ""), color = TV.Ink, fontSize = 9.5.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${e.length} ${e.line.lowercase()} · ${e.shot}", color = TV.Ink2, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.dp, TV.PanelLine, RoundedCornerShape(4.dp))) {
                        Row(Modifier.fillMaxWidth().background(TV.Panel2).padding(horizontal = 8.dp, vertical = 3.dp)) { Lbl("Batting analysis", TV.Ink, 8) }
                        for ((slot, i) in listOf(0 to inn.striker, 1 to inn.nonStriker)) {
                            val c = inn.bc.getOrNull(i) ?: continue
                            if (c.out || inn.pendingSlot == slot) continue
                            val inOver = balls.filter { it.batter == c.pid }
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Row { Text(m.pn(c.pid) + if (i == inn.striker) " *" else "", color = TV.Royal, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)); Text("${c.runs} (${c.balls})", color = TV.Ink, fontSize = 10.sp, fontWeight = FontWeight.Black) }
                                Text("SR ${sr(c)} · ${c.fours}x4 ${c.sixes}x6" + (if (inOver.isNotEmpty()) " · this over ${inOver.sumOf { it.bat }} off ${inOver.count { it.legal }}" else ""), color = TV.Ink2, fontSize = 8.5.sp)
                            }
                            HorizontalDivider(color = TV.PanelLine)
                        }
                        Text("Partnership ${inn.partRuns} (${inn.partBalls})", color = TV.Ink2, fontSize = 8.5.sp, modifier = Modifier.padding(8.dp))
                    }
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.dp, TV.PanelLine, RoundedCornerShape(4.dp))) {
                        Row(Modifier.fillMaxWidth().background(TV.Panel2).padding(horizontal = 8.dp, vertical = 3.dp)) { Lbl("Bowling analysis", TV.Ink, 8) }
                        if (bwCard != null) Column(Modifier.padding(8.dp)) {
                            Text(m.pn(bwCard.pid), color = TV.Royal, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Text("${bwCard.oversText} ov · ${bwCard.maidens} m · ${bwCard.runs} r · ${bwCard.wk} w · econ ${fmtOv(bwCard.econ)}", color = TV.Ink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            val verdict = when { wk >= 2 -> "Match-turning over."; wk == 1 && runs <= 6 -> "Wicket and tight - excellent."; runs == 0 -> "Maiden over."; runs <= 4 -> "Tight over."; runs >= 15 -> "Expensive over - went the distance."; runs >= 10 -> "Leaked runs."; else -> "Steady over." }
                            Text(verdict, color = TV.Ink2, fontSize = 8.5.sp, modifier = Modifier.padding(top = 2.dp))
                            Text("Spell: ${inn.spell[bwCard.pid] ?: 0} over(s) · fitness ${s.players[bwCard.pid]?.fitness ?: 0}%", color = TV.Ink2, fontSize = 8.5.sp)
                        } else Text(if (startOfInnings) "New innings - pick the opening bowler." else "-", color = TV.Ink2, fontSize = 9.sp, modifier = Modifier.padding(8.dp))
                    }
                }
            }
            if (userBowling) BowlerPicker(m, s, inn, chosen, Modifier.weight(1f).fillMaxHeight()) { chosen = it }
        }
        Row(Modifier.fillMaxWidth().background(TV.Panel2).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (userBowling) (if (chosen == null) "Select a bowler to start the next over." else "${m.pn(chosen!!)} will bowl the next over.") else if (m.finished) m.resultText else if (inn.closed) "Innings over." else "${m.club(inn.bowlTeam).short} will choose their bowler.", color = TV.Ink, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (m.isTest && m.canDeclare()) TvBtn("Declare", primary = false) { vm.declare(); onDone() }
            TvBtn(if (userBowling) "Confirm bowler" else if (m.finished || inn.closed) "Continue" else "Next over", enabled = !userBowling || chosen != null) {
                if (userBowling) chosen?.let { if (m.setBowler(it)) { vm.bump(); onDone() } } else onDone()
            }
        }
    }
}

@Composable
private fun BowlerPicker(m: MatchSim, s: CareerSave, inn: Inns, chosen: String?, modifier: Modifier, onPick: (String) -> Unit) {
    val opts = remember(m.events.size, inn.overStarted) { m.bowlerOptions() }
    Column(modifier.clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.dp, TV.Gold, RoundedCornerShape(4.dp))) {
        Row(Modifier.fillMaxWidth().background(TV.Royal).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("CHANGE BOWLER", color = TV.Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Text(if (m.isTest) "no over limit" else "max ${m.bowlerQuota()} ov each", color = Color.White.copy(alpha = .8f), fontSize = 8.sp)
        }
        Row(Modifier.fillMaxWidth().background(TV.Panel2).padding(horizontal = 8.dp, vertical = 2.dp)) { H("Bowler", 2.6f, TextAlign.Start); H("O-M-R-W", 1.6f); H("Econ", .9f); H("Fit", .8f); H("Left", .8f) }
        LazyColumn(Modifier.weight(1f)) {
            items(opts) { o ->
                val p = s.players[o.pid]; val c = o.card
                val sel = chosen == o.pid
                Row(Modifier.fillMaxWidth().background(if (sel) Color(0xFFFFF4D1) else if (!o.eligible) TV.Panel else Color.White)
                    .clickable(enabled = o.eligible, role = Role.RadioButton) { GameAudio.click(); onPick(o.pid) }.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(2.6f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).border(1.5.dp, if (o.eligible) TV.Royal else TV.PanelLine, CircleShape).background(if (sel) TV.Royal else Color.Transparent)); Spacer(Modifier.width(5.dp))
                            Text(p?.name ?: o.pid, color = if (!o.eligible) TV.Grey else TV.Ink, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(if (!o.eligible) o.reason else "${if (p?.bowlType == "SPIN") "Spin" else "Pace"} · ${p?.role ?: ""} · bowl ${p?.bowl ?: 0}" + (if (o.inPlan) " · in plan" else "") + (if (o.spell > 0 && o.pid != inn.lastBowler) " · spell ${o.spell}" else ""), color = if (!o.eligible) TV.Live else TV.Ink2, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Cl(if (c != null) "${c.oversText}-${c.maidens}-${c.runs}-${c.wk}" else "-", 1.6f, if (o.eligible) TV.Ink else TV.Grey)
                    Cl(if (c != null && c.balls > 0) fmtOv(c.econ) else "-", .9f, if (o.eligible) TV.Ink else TV.Grey)
                    Cl("${p?.fitness ?: 0}", .8f, if ((p?.fitness ?: 0) < 50) TV.Live else if (o.eligible) TV.Ink else TV.Grey)
                    Cl(if (m.isTest) "∞" else "${o.oversLeft.coerceAtLeast(0)}", .8f, if (o.eligible) TV.Ink else TV.Grey)
                }
                HorizontalDivider(color = TV.PanelLine)
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------ new batter */

@Composable
private fun NewBatterOverlay(m: MatchSim, s: CareerSave, comp: Competition?, onPick: (String) -> Unit) {
    val inn = m.cur ?: return
    val w = m.lastWicketEv
    val outPid = w?.outPid ?: w?.batter
    val outCard = inn.bc.firstOrNull { it.pid == outPid }
    val avail = m.availableBatters()
    var chosen by remember { mutableStateOf<String?>(avail.firstOrNull()?.pid) }
    val wkLeft = (inn.maxWk - inn.wk).coerceAtLeast(0)
    OverlayFrame("Wicket · new batter", "${m.club(inn.batTeam).name} ${inn.scoreText()} (${inn.oversText()} ov)  ·  ${wkLeft} wicket${if (wkLeft == 1) "" else "s"} in hand", 760.dp) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DetailTile("Out", (s.players[outPid ?: ""]?.name ?: m.pn(outPid ?: "")) + (outCard?.let { "  ${it.runs} (${it.balls})" } ?: ""), Modifier.weight(2f), TV.Live)
            DetailTile("Dismissal", outCard?.how ?: w?.how ?: "-", Modifier.weight(1.6f), TV.Live)
            DetailTile("Wicket", "${inn.wk}", Modifier.weight(.7f)); DetailTile("Score", inn.scoreText(), Modifier.weight(.9f)); DetailTile("Over", inn.oversText(), Modifier.weight(.8f)); DetailTile("Left", "$wkLeft", Modifier.weight(.7f))
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.dp, TV.Gold, RoundedCornerShape(4.dp))) {
            Row(Modifier.fillMaxWidth().background(TV.Royal).padding(horizontal = 8.dp, vertical = 5.dp)) { Text("SELECT NEXT BATTER", color = TV.Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f)); Text("team-sheet order shown · any available batter may be sent in", color = Color.White.copy(alpha = .8f), fontSize = 8.sp) }
            Row(Modifier.fillMaxWidth().background(TV.Panel2).padding(horizontal = 8.dp, vertical = 2.dp)) { H("#", .4f, TextAlign.Start); H("Batter", 2.6f, TextAlign.Start); H("Role", .8f); H("OVR", .7f); H("Form", .8f); H("Fit", .7f); H("Record", 2.2f) }
            LazyColumn(Modifier.weight(1f)) {
                items(avail) { c ->
                    val p = s.players[c.pid]; val sel = chosen == c.pid; val pos = inn.bc.indexOf(c) + 1
                    Row(Modifier.fillMaxWidth().background(if (sel) Color(0xFFFFF4D1) else Color.White).clickable(role = Role.RadioButton) { GameAudio.click(); chosen = c.pid }.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cl("$pos", .4f, TV.Ink2, align = TextAlign.Start)
                        Column(Modifier.weight(2.6f)) {
                            Text(p?.name ?: c.pid, color = TV.Ink, fontSize = 10.5.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${if (p?.role == "BOWL") "Lower order" else if (pos <= 3) "Top order" else if (pos <= 7) "Middle order" else "Lower order"} · tech ${p?.tech ?: 0} · power ${p?.power ?: 0}", color = TV.Ink2, fontSize = 8.sp, maxLines = 1)
                        }
                        Cl(p?.role ?: "", .8f, TV.Ink2); Cl("${p?.overall() ?: 0}", .7f, TV.Ink, bold = true); Cl(formLabel(p?.form ?: 50), .8f, if ((p?.form ?: 0) >= 60) TV.Green else TV.Ink2); Cl("${p?.fitness ?: 0}", .7f, if ((p?.fitness ?: 0) < 50) TV.Live else TV.Ink)
                        Text(p?.let { comp?.let { cp -> batSummary(cp, it) } } ?: "", color = TV.Ink2, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(2.2f))
                    }
                    HorizontalDivider(color = TV.PanelLine)
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(TV.Panel2).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val other = inn.bc.getOrNull(if (inn.pendingSlot == 0) inn.nonStriker else inn.striker)
            Text("At the crease: ${other?.let { "${m.pn(it.pid)} ${it.runs} (${it.balls})" } ?: "-"}  ·  new batter ${if (inn.pendingSlot == 0) "takes strike" else "at the non-striker's end"}", color = TV.Ink, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2)
            TvBtn("Send in batter", enabled = chosen != null) { chosen?.let(onPick) }
        }
    }
}

/* ------------------------------------------------------------------------------------------------ innings break */

@Composable
private fun InningsBreakOverlay(m: MatchSim, onDone: () -> Unit) {
    val done = m.innings.getOrNull(m.innings.size - 2) ?: run { onDone(); return }
    val next = m.cur ?: return
    OverlayFrame("Innings break", "${m.club(done.batTeam).name} ${done.scoreText()} (${done.oversText()} ov)" + (next.target?.let { "  ·  ${m.club(next.batTeam).short} need $it to win" } ?: ""), 720.dp) {
        Row(Modifier.weight(1f).fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.dp, TV.PanelLine, RoundedCornerShape(4.dp))) {
                Row(Modifier.fillMaxWidth().background(TV.Panel2).padding(6.dp)) { Lbl("Top batting", TV.Ink, 8) }
                for (c in done.bc.filter { it.batted }.sortedByDescending { it.runs }.take(5)) Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) { Text(m.pn(c.pid), color = TV.Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${c.runs}${if (!c.out) "*" else ""} (${c.balls})", color = TV.Royal, fontSize = 10.sp, fontWeight = FontWeight.Black) }
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.dp, TV.PanelLine, RoundedCornerShape(4.dp))) {
                Row(Modifier.fillMaxWidth().background(TV.Panel2).padding(6.dp)) { Lbl("Top bowling", TV.Ink, 8) }
                for (b in done.bw.values.sortedWith(compareByDescending<BowlCard> { it.wk }.thenBy { it.runs }).take(5)) Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) { Text(m.pn(b.pid), color = TV.Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${b.wk}/${b.runs} (${b.oversText})", color = TV.Royal, fontSize = 10.sp, fontWeight = FontWeight.Black) }
            }
        }
        Row(Modifier.fillMaxWidth().background(TV.Panel2).padding(8.dp), horizontalArrangement = Arrangement.End) { TvBtn("Start ${m.club(next.batTeam).short} innings") { onDone() } }
    }
}

/* ------------------------------------------------------------------------------------------------ control bar + menu */

@Composable
private fun CtlBtn(label: String, modifier: Modifier = Modifier, active: Boolean = false, enabled: Boolean = true, accent: Color = TV.Gold, onClick: () -> Unit) {
    Box(modifier.height(34.dp).clip(RoundedCornerShape(5.dp)).background(if (active) accent else TV.Navy2).border(1.dp, if (active) accent else TV.Hair, RoundedCornerShape(5.dp))
        .semantics { contentDescription = label }.clickable(enabled = enabled, role = Role.Button) { GameAudio.click(); onClick() }.padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
        Text(label.uppercase(), color = if (!enabled) TV.Grey else if (active) TV.Ink else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ControlBar(vm: GameVM, m: MatchSim, centre: Int, speedX: Int, onCentre: (Int) -> Unit, onSpeed: () -> Unit, onMenu: () -> Unit, onBall: () -> Unit, onAuto: () -> Unit) {
    val live = !m.finished
    Row(Modifier.fillMaxWidth().background(TV.Navy).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        CtlBtn("☰ Menu") { onMenu() }
        CtlBtn("Highlights", active = centre == 2) { onCentre(2) }
        CtlBtn("Commentary", active = centre == 1) { onCentre(1) }
        CtlBtn("Pitch", active = centre == 0) { onCentre(0) }
        CtlBtn("Wagon", active = centre == 4) { onCentre(4) }
        CtlBtn("Ball history", active = centre == 5) { onCentre(5) }
        Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(24.dp).background(TV.Hair))
        if (m.isTest) CtlBtn("Declare", enabled = live && m.canDeclare(), accent = TV.Live) { vm.declare() }
        CtlBtn("Sim end", enabled = live, accent = TV.Live) { vm.autoPlay = false; vm.simRest() }
        CtlBtn("${speedX}x", active = speedX > 1) { onSpeed() }
        CtlBtn(if (vm.autoPlay) "❚❚ Pause" else "▶ Auto play", active = vm.autoPlay, enabled = live, accent = TV.Green) { onAuto() }
        CtlBtn("Next ball ›", active = true, enabled = live && !vm.autoPlay) { onBall() }
    }
}

@Composable
private fun MatchMenu(vm: GameVM, m: MatchSim, onResume: () -> Unit, on3d: () -> Unit, onSettings: () -> Unit, onExit: () -> Unit) {
    val me = vm.userSideId() ?: m.userTeam
    Box(Modifier.fillMaxSize().background(Color(0xCC020614)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.width(340.dp).clip(RoundedCornerShape(10.dp)).background(TV.Panel).border(1.5.dp, TV.Gold, RoundedCornerShape(10.dp))) {
            Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(TV.Navy2, TV.Royal))).padding(12.dp)) { Text("MATCH OPTIONS", color = TV.Gold, fontFamily = Display, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (me.isNotEmpty() && !m.finished) {
                    Lbl("Batting approach", TV.Ink2, 8)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(TV.Panel2).padding(2.dp)) {
                        val agg = m.club(me).aggression
                        for ((v, l) in listOf(0 to "Defend", 1 to "Balanced", 2 to "Attack")) Box(Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(4.dp)).background(if (agg == v) TV.Royal else Color.Transparent).clickable { vm.setAggression(v) }, contentAlignment = Alignment.Center) { Text(l.uppercase(), color = if (agg == v) Color.White else TV.Ink2, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                    }
                }
                TvBtn("Resume", Modifier.fillMaxWidth()) { onResume() }
                TvBtn("3D stadium view", Modifier.fillMaxWidth(), primary = false) { on3d() }
                TvBtn("Settings", Modifier.fillMaxWidth(), primary = false) { onSettings() }
                TvBtn("Exit match (saved)", Modifier.fillMaxWidth(), primary = false) { onExit() }
            }
        }
    }
}
