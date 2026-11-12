package com.cricketcoach.manager.ui.screens

import com.cricketcoach.manager.render3d.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.platform.LocalConfiguration
import com.cricketcoach.manager.audio.GameAudio

@Composable
fun PreMatchScreen(vm: GameVM) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick + vm.simTick
    val comp = vm.pendingComp(); val fx = vm.pendingFixture()
    if (comp == null || fx == null) { ScreenScaffold(vm, "Match Centre") { Sub("No match scheduled.", Modifier.padding(16.dp)) }; return }
    val me = comp.userTeam ?: s.userClubId
    val h = s.clubs[fx.home]!!; val a = s.clubs[fx.away]!!
    val myClub = s.clubs[me]!!
    val m = vm.prepareSim()
    var tossState by remember { mutableStateOf(if (m?.started == true) "DONE" else "CALL") }
    LaunchedEffect(Unit) { TeamSelector.validate(myClub, s.players).forEach { vm.msg(it) } }
    ScreenScaffold(vm, "Match Centre") {
        Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
            CcmCard(highlight = true) {
                Sub("${Stats.compLabel(comp)} • ${fx.stage} • ${comp.format}", Modifier.align(Alignment.CenterHorizontally))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { ClubBadge(h, 60.dp); Text(h.short, color = C.Text, fontWeight = FontWeight.Bold); Sub("Str ${"%.0f".format(Career.strength(s, h))}") }
                    Text("VS", color = C.Gold, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { ClubBadge(a, 60.dp); Text(a.short, color = C.Text, fontWeight = FontWeight.Bold); Sub("Str ${"%.0f".format(Career.strength(s, a))}") }
                }
                if (m != null) {
                    StadiumGraphic(h.capacity, h.primary)
                    KV("Venue", m.cfg.venue); KV("Pitch", m.pitchText()); KV("Weather", m.cfg.weather)
                    if (m.rainNote.isNotEmpty()) Text(m.rainNote, color = C.Blue, fontSize = 12.sp)
                    KV("Format", if (m.isTest) "Test (5 days)" else "${m.cfg.overs} overs a side${if (m.cfg.allowImpact) " • Impact Player rule" else ""}")
                }
            }
            CcmCard {
                Text("Your XI (${myClub.short})", color = C.Gold, fontWeight = FontWeight.Bold)
                myClub.xi.forEachIndexed { i, pid -> val p = s.players[pid]; if (p != null) Text("${i + 1}. ${p.name}${if (myClub.captain == pid) " (c)" else ""}${if (myClub.keeper == pid) " †" else ""} • ${p.overall()}${if (p.fitness < 60) " • tired" else ""}", color = C.Text, fontSize = 13.sp) }
                s.players[myClub.impact]?.let { Sub("Impact sub: ${it.name}") }
                Spacer(Modifier.height(6.dp))
                Row {
                    OutlineBtn("Edit XI & tactics", Modifier.weight(1f), enabled = m?.started != true) { vm.nav(Screen.Squad(me)) }
                    Spacer(Modifier.width(8.dp))
                    OutlineBtn("Opponent squad", Modifier.weight(1f)) { vm.nav(Screen.Squad(if (fx.home == me) fx.away else fx.home)) }
                }
                if (m?.started == true) Sub("Teams are locked after the toss.")
            }
            CcmCard {
                Text("Toss", color = C.Gold, fontWeight = FontWeight.Bold)
                when (tossState) {
                    "CALL" -> {
                        Sub("Call heads or tails.")
                        Row {
                            GoldButton("Heads", Modifier.weight(1f)) { vm.sim = null; vm.prepareSim(); tossState = if (vm.callToss(true)) "CHOOSE" else "DONE" }
                            Spacer(Modifier.width(8.dp))
                            GoldButton("Tails", Modifier.weight(1f)) { vm.sim = null; vm.prepareSim(); tossState = if (vm.callToss(false)) "CHOOSE" else "DONE" }
                        }
                    }
                    "CHOOSE" -> {
                        Text("You won the toss!", color = C.Green, fontWeight = FontWeight.Bold)
                        Row {
                            GreenButton("Bat first", Modifier.weight(1f)) { vm.chooseToss(true); tossState = "DONE" }
                            Spacer(Modifier.width(8.dp))
                            GreenButton("Bowl first", Modifier.weight(1f)) { vm.chooseToss(false); tossState = "DONE" }
                        }
                    }
                    else -> vm.sim?.let { sim -> Text("${sim.club(sim.tossWinner).name} won the toss and chose to ${sim.decision.lowercase()} first.", color = C.Text) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                GoldButton("Watch live", Modifier.weight(1f), enabled = tossState == "DONE") { vm.watchLive() }
                Spacer(Modifier.width(8.dp))
                OutlineBtn("Quick sim", Modifier.weight(1f)) { if (vm.sim?.started != true) vm.sim?.autoToss(); vm.quickSimMatch() }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
internal fun SmallBtn(text: String, color: Color, modifier: Modifier = Modifier, dark: Boolean = true, onClick: () -> Unit) {
    Box(modifier.padding(2.dp).height(34.dp).clip(RoundedCornerShape(9.dp)).background(color).clickable { GameAudio.click(); onClick() }, contentAlignment = Alignment.Center) {
        Text(text, color = if (dark) Color.Black else C.Text, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun ScoreBar(m: MatchSim, compact: Boolean) {
    val inn = m.cur
    Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xE61B5E20), Color(0xCC0F2A1F), Color(0x990F2A1F)))).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClubBadge(inn?.let { m.club(it.batTeam) }, if (compact) 28.dp else 38.dp)
            Spacer(Modifier.width(6.dp))
            Text(inn?.let { m.club(it.batTeam).short } ?: "", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text(inn?.scoreText() ?: "-", color = C.Gold, fontSize = if (compact) 22.sp else 28.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(4.dp))
            Text("(${inn?.oversText() ?: "0.0"}${if (!m.isTest && inn != null) "/${inn.maxBalls / 6}" else ""})", color = C.Sub, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("${m.phaseLabel()} • CRR ${f2(inn?.crr())}", color = C.Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                val tgt = inn?.target
                if (tgt != null && !m.finished) {
                    val need = tgt - inn.runs; val left = inn.maxBalls - inn.balls
                    Text(if (m.isTest) "Need $need" else "Need $need off $left • RRR ${if (left > 0) f2(need * 6.0 / left) else "-"}", color = C.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                } else if (m.innings.size > 1) Text(m.innings.dropLast(1).joinToString(" • ") { "${m.club(it.batTeam).short} ${it.scoreText()}" }, color = C.Sub, fontSize = 10.sp)
                Text("DRS ${inn?.reviews ?: 0}|${inn?.fieldReviews ?: 0}${m.winProbability()?.let { "  •  Win ${it}%" } ?: ""}", color = C.Sub, fontSize = 10.sp)
            }
        }
        if (inn != null) Row(Modifier.padding(top = 2.dp)) {
            for (b in inn.recent.toList().takeLast(12)) {
                val col = when { b == "W" -> C.Red; b == "6" -> C.Gold; b == "4" -> C.Green; b == "wd" || b == "nb" -> C.Blue; else -> C.CardHi }
                Box(Modifier.padding(end = 3.dp).size(20.dp).clip(CircleShape).background(col), contentAlignment = Alignment.Center) {
                    Text(b, color = if (col == C.CardHi) C.Text else Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PlayersStrip(m: MatchSim, s: CareerSave) {
    val inn = m.cur ?: return
    Row(Modifier.fillMaxWidth().background(Color(0xCC07140F)).padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((idx, star) in listOf(inn.striker to true, inn.nonStriker to false)) {
            val c = inn.bc.getOrNull(idx) ?: continue
            PlayerFace(c.pid, s.players[c.pid]?.name ?: c.pid, 28.dp, if (star) C.Gold else C.Sub)
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text("${if (star) "▶" else ""}${m.pn(c.pid)}", color = if (star) C.Gold else C.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${c.runs} (${c.balls})  ${c.fours}x4 ${c.sixes}x6", color = C.Sub, fontSize = 10.sp, maxLines = 1)
            }
        }
        val bw = inn.bw[inn.bowler]
        if (inn.bowler.isNotEmpty()) {
            PlayerFace(inn.bowler, s.players[inn.bowler]?.name ?: inn.bowler, 28.dp, C.Blue)
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(m.pn(inn.bowler), color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(bw?.let { "${it.oversText}-${it.maidens}-${it.runs}-${it.wk}" } ?: "", color = C.Sub, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun LiveMatchScreen(vm: GameVM) {
    val s = vm.save ?: return
    val m = vm.sim
    @Suppress("UNUSED_VARIABLE") val tk = vm.simTick
    if (m == null) { ScreenScaffold(vm, "Live Match") { Sub("No match in progress.", Modifier.padding(16.dp)) }; return }
    if (!m.started) m.autoToss()
    val me = vm.userSideId() ?: s.userClubId
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var view3d by remember { mutableStateOf(vm.settings.view3d) }
    var cam by remember { mutableIntStateOf(vm.settings.camMode) }
    var animBusy by remember { mutableStateOf(false) }
    var replayEv by remember { mutableStateOf<BallEv?>(null) }
    var playKey by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var wagonInn by remember { mutableIntStateOf(-1) }
    var speedX by remember { mutableIntStateOf(vm.settings.playSpeed.let { if (it in SPEEDS) it else 1 }) }
    val dur = (vm.settings.simSpeedMs * 6 / speedX).coerceIn(500, 4200)
    DisposableEffect(Unit) { GameAudio.setScene(GameAudio.Scene.MATCH); onDispose { GameAudio.setScene(GameAudio.Scene.MENU) } }
    // 2D mode: sound straight away
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
        }
    }
    fun step(f: () -> Unit) { replayEv = null; val before = m.events.size; f(); if (view3d && m.events.size > before) animBusy = true }
    val ev = replayEv ?: m.lastEv
    val inn = m.cur
    val batCol = inn?.let { Color(m.club(it.batTeam).primary) } ?: C.Green
    val bowlCol = inn?.let { Color(m.club(it.bowlTeam).primary) } ?: C.Blue

    val viewPane: @Composable (Modifier) -> Unit = { mod ->
        Box(mod.background(Color(0xFF07140F))) {
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
            else WagonWheel(m.events.filter { it.inn == m.innings.size - 1 }, Modifier.fillMaxSize().padding(top = 60.dp, bottom = 40.dp))
            Column(Modifier.align(Alignment.TopStart).fillMaxWidth()) { ScoreBar(m, landscape) }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                if (ev != null) Text(vm.comLines.firstOrNull { it.evId == ev.id && it.kind == ev.kind }?.text ?: ev.text, color = C.Text, fontSize = 11.sp, maxLines = 1, modifier = Modifier.fillMaxWidth().background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 2.dp))
                if (!m.finished) PlayersStrip(m, s)
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(4.dp)) {
                val camNames = listOf("TV", "Aerial", "Side", "Bat")
                SmallBtn(if (view3d) "2D" else "3D", Color(0xCC13362A), Modifier.width(54.dp), dark = false) { view3d = !view3d; vm.settings.view3d = view3d; animBusy = false }
                if (view3d) SmallBtn("🎥 ${camNames[cam]}", Color(0xCC13362A), Modifier.width(54.dp), dark = false) { cam = (cam + 1) % 4; vm.settings.camMode = cam }
                // playback speed: every speed is free
                SmallBtn("⏩ ${speedX}x", if (speedX > 1) C.Gold else Color(0xCC13362A), Modifier.width(54.dp), dark = speedX > 1) {
                    speedX = SPEEDS[(SPEEDS.indexOf(speedX) + 1) % SPEEDS.size]; vm.settings.playSpeed = speedX
                }
                if (replayEv != null) SmallBtn("LIVE", C.Red, Modifier.width(54.dp)) { replayEv = null }
            }
            if (m.finished) Box(Modifier.align(Alignment.Center).padding(12.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xEE0F2A1F)).padding(14.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏆 ${m.resultText}", color = C.Gold, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, textAlign = TextAlign.Center)
                    if (m.superOverText.isNotEmpty()) Sub(m.superOverText)
                    m.potm()?.let { pid ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            PlayerFace(pid, s.players[pid]?.name ?: pid, 40.dp); Spacer(Modifier.width(8.dp))
                            Column { Sub("Player of the Match"); Text(s.players[pid]?.name ?: pid, color = C.Text, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    GoldButton("Continue", Modifier.width(200.dp)) { vm.leaveMatch() }
                }
            }
        }
    }

    val controls: @Composable () -> Unit = {
        if (!m.finished) Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Row {
                SmallBtn("Ball", C.Green, Modifier.weight(1f)) { step { vm.stepBall() } }
                SmallBtn("Over", C.Green, Modifier.weight(1f)) { step { vm.stepOver() } }
                SmallBtn("Innings", C.Green, Modifier.weight(1f)) { step { vm.stepInnings() } }
                SmallBtn(if (vm.autoPlay) "Pause" else "Auto ▶", C.Gold, Modifier.weight(1f)) { vm.autoPlay = !vm.autoPlay }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallBtn("Sim end", C.CardHi, Modifier.weight(1f), dark = false) { step { vm.simRest() } }
                if (m.canDeclare()) SmallBtn("Declare", C.CardHi, Modifier.weight(1f), dark = false) { vm.declare() }
                for ((v, l) in listOf(0 to "Defend", 1 to "Normal", 2 to "Attack")) {
                    val sel = m.club(me).aggression == v
                    SmallBtn(l, if (sel) C.Gold else C.Card, Modifier.weight(1f), dark = sel) { vm.setAggression(v) }
                }
            }
        }
    }

    val tabNames = listOf("Commentary", "Highlights", "Scorecard", "Wagon", "Manhattan", "Worm", "Events")
    val tabsAndContent: @Composable (Modifier) -> Unit = { mod ->
        Column(mod) {
            LazyRow(Modifier.fillMaxWidth().background(C.Surface).padding(vertical = 3.dp)) {
                items(tabNames.size) { i ->
                    val sel = i == tab
                    Text(tabNames[i], color = if (sel) Color.Black else C.Sub, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 3.dp).clip(RoundedCornerShape(12.dp)).background(if (sel) C.Gold else C.Card).clickable { GameAudio.click(); tab = i }.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            val innNames = m.innings.mapIndexed { i, it -> "${m.club(it.batTeam).short}${if (m.isTest) " (${i + 1})" else ""}" }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> if (vm.comLines.isNotEmpty()) CommentaryFeed(vm) else LazyColumn(Modifier.padding(horizontal = 8.dp)) {
                        items(m.com.take(150)) { line ->
                            val col = when { line.contains("OUT") || line.contains("WICKET") -> C.Red; line.contains("SIX") -> C.Gold; line.contains("FOUR") -> C.Green; line.startsWith("End of over") -> C.Blue; else -> C.Text }
                            Text(line, color = col, fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp))
                            HorizontalDivider(color = Color(0xFF1E3D31))
                        }
                    }
                    1 -> {
                        val hl = m.events.filter { it.highlight }.reversed()
                        if (hl.isEmpty()) Sub("Boundaries, wickets and milestones appear here. Tap one to watch the 3D replay.", Modifier.padding(10.dp))
                        LazyColumn(Modifier.padding(horizontal = 6.dp)) {
                            items(hl.take(120)) { e ->
                                val (tag, col) = when (e.kind) { "SIX" -> "6" to C.Gold; "FOUR" -> "4" to C.Green; "WICKET" -> "W" to C.Red; else -> "★" to C.Blue }
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(C.Card).clickable {
                                    GameAudio.play("whoosh"); if (!view3d) { view3d = true; vm.settings.view3d = true }; vm.autoPlay = false; replayEv = e; playKey++
                                }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(26.dp).clip(CircleShape).background(col), contentAlignment = Alignment.Center) { Text(tag, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                    Spacer(Modifier.width(6.dp))
                                    PlayerFace(e.batter, s.players[e.batter]?.name ?: "", 26.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(e.text, color = C.Text, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 2)
                                    Text("▶", color = C.Gold, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                        }
                    }
                    2 -> Scorecard(m)
                    3 -> Column(Modifier.fillMaxSize().padding(4.dp)) {
                        val sel = if (wagonInn < 0) m.innings.size - 1 else wagonInn
                        LazyRow { items(innNames.size) { i -> Text(innNames[i], color = if (i == sel) Color.Black else C.Sub, fontSize = 11.sp, modifier = Modifier.padding(2.dp).clip(RoundedCornerShape(10.dp)).background(if (i == sel) C.Gold else C.Card).clickable { wagonInn = i }.padding(horizontal = 8.dp, vertical = 3.dp)) } }
                        WagonWheel(m.events.filter { it.inn == sel }, Modifier.fillMaxWidth().weight(1f))
                    }
                    4 -> Manhattan(m.events, innNames, if (m.isTest) 0 else m.cfg.overs, Modifier.fillMaxSize().padding(6.dp))
                    5 -> Worm(m.events, innNames, if (m.isTest) 0 else m.cfg.overs, Modifier.fillMaxSize().padding(6.dp))
                    else -> LazyColumn(Modifier.padding(10.dp)) {
                        if (m.rainNote.isNotEmpty()) item { Text(m.rainNote, color = C.Blue, fontSize = 12.sp) }
                        items(m.keyEvents.reversed()) { Text("• $it", color = C.Text, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                    }
                }
            }
        }
    }

    ScreenScaffold(vm, "${m.home.short} v ${m.away.short} • ${m.cfg.format} • ${m.cfg.venue}") {
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                viewPane(Modifier.weight(1.5f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight()) { controls(); tabsAndContent(Modifier.weight(1f)) }
            }
        } else {
            viewPane(Modifier.fillMaxWidth().height(250.dp))
            controls()
            tabsAndContent(Modifier.weight(1f))
        }
    }
}

@Composable
fun Scorecard(m: MatchSim) {
    LazyColumn(Modifier.padding(horizontal = 8.dp)) {
        m.innings.forEachIndexed { i, inn ->
            item {
                SectionTitle("${m.club(inn.batTeam).name} — ${inn.scoreText()} (${inn.oversText()} ov)${if (inn.followOn) " (f/o)" else ""}")
                TableHeader(listOf("Batter" to 3f, "" to 3f, "R" to 0.8f, "B" to 0.8f, "4s" to 0.7f, "6s" to 0.7f, "SR" to 1f))
            }
            items(inn.bc.filter { it.batted }) { c ->
                TableRowView(listOf(m.pn(c.pid) to 3f, (if (c.out) c.how else "not out") to 3f, "${c.runs}" to 0.8f, "${c.balls}" to 0.8f, "${c.fours}" to 0.7f, "${c.sixes}" to 0.7f,
                    (if (c.balls > 0) "%.0f".format(c.runs * 100.0 / c.balls) else "-") to 1f))
            }
            item {
                Sub("Extras ${inn.extras} • Total ${inn.scoreText()}")
                val dnb = inn.bc.filter { !it.batted }.joinToString { m.pn(it.pid) }
                if (dnb.isNotEmpty()) Sub("Yet to bat / DNB: $dnb")
                if (inn.fow.isNotEmpty()) Sub("FOW: " + inn.fow.joinToString(", "))
                Spacer(Modifier.height(4.dp))
                TableHeader(listOf("Bowler" to 3f, "O" to 1f, "M" to 0.7f, "R" to 0.9f, "W" to 0.7f, "Econ" to 1f))
            }
            items(inn.bw.values.toList()) { b ->
                TableRowView(listOf(m.pn(b.pid) to 3f, b.oversText to 1f, "${b.maidens}" to 0.7f, "${b.runs}" to 0.9f, "${b.wk}" to 0.7f, f2(b.econ) to 1f))
            }
            item {
                if (inn.parts.isNotEmpty()) { Text("Partnerships", color = C.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold); inn.parts.forEach { Sub(it) } }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/* ======================= Fixtures & tables ======================= */

@Composable
fun FixturesScreen(vm: GameVM) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    var div by remember { mutableIntStateOf(if (s.mode == Modes.CLUB) s.userClub().division - 1 else currentCompIndex(s)) }
    var mineOnly by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<Fixture?>(null) }
    ScreenScaffold(vm, "Fixtures & Results") {
        Tabs(s.league.map { compTab(s, it) }, div.coerceIn(0, (s.league.size - 1).coerceAtLeast(0))) { div = it }
        val comp = s.league.getOrNull(div) ?: return@ScreenScaffold
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(mineOnly, { mineOnly = it }); Spacer(Modifier.width(8.dp)); Text(if (comp.userTeam != null) "Only my matches" else "Only my matches (not in this division)", color = C.Text, fontSize = 13.sp)
        }
        FixtureList(s, comp, if (mineOnly && comp.userTeam != null) comp.userTeam else null) { detail = it }
    }
    detail?.let { FixtureDetail(s, it) { detail = null } }
}

@Composable
fun FixtureList(s: CareerSave, comp: Competition, team: String?, onOpen: (Fixture) -> Unit) {
    val list = comp.fixtures.filter { team == null || it.home == team || it.away == team }.sortedBy { it.day }
    LazyColumn(Modifier.padding(horizontal = 12.dp)) {
        items(list, key = { it.id }) { f ->
            val h = s.clubs[f.home]; val a = s.clubs[f.away]
            CcmCard(onClick = { if (f.played) onOpen(f) }, highlight = f.home == comp.userTeam || f.away == comp.userTeam) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(64.dp)) { Sub(if (f.day > 0) "Day ${f.day}" else f.stage, size = 11); Sub(f.stage.take(14), size = 10) }
                    ClubBadge(h, 26.dp); Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${h?.short ?: "?"} ${f.res?.hScore ?: ""}", color = if (f.res?.winner == f.home) C.Gold else C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${a?.short ?: "?"} ${f.res?.aScore ?: ""}", color = if (f.res?.winner == f.away) C.Gold else C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    ClubBadge(a, 26.dp)
                }
                f.res?.let { Sub(it.text) } ?: Sub("Upcoming")
            }
        }
        item { if (list.isEmpty()) Sub("No fixtures yet."); Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun FixtureDetail(s: CareerSave, f: Fixture, onClose: () -> Unit) {
    val r = f.res ?: return
    AlertDialog(onDismissRequest = onClose, containerColor = C.Surface,
        title = { Text("${s.clubs[f.home]?.short} v ${s.clubs[f.away]?.short} — ${f.stage}") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(r.text, color = C.Gold, fontWeight = FontWeight.Bold)
                if (r.potm.isNotEmpty()) Sub("Player of the match: ${s.players[r.potm]?.name ?: r.potm}")
                Spacer(Modifier.height(6.dp))
                r.card.forEach { Text(it, color = if (!it.startsWith("  ")) C.Green else C.Text, fontSize = 11.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}

@Composable
fun TableScreen(vm: GameVM) {
    val s = vm.save ?: return
    var div by remember { mutableIntStateOf(if (s.mode == Modes.CLUB) s.userClub().division - 1 else currentCompIndex(s)) }
    ScreenScaffold(vm, if (s.mode == Modes.CLUB) "League Tables" else "Points Tables") {
        Tabs(s.league.map { compTab(s, it) }, div.coerceIn(0, (s.league.size - 1).coerceAtLeast(0))) { div = it }
        val comp = s.league.getOrNull(div) ?: return@ScreenScaffold
        Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
            if (s.mode == Modes.CLUB) {
                Sub(when (comp.division) { 1 -> "Top 4 reach the playoffs (Qualifier 1, Eliminator, Qualifier 2, Final). Bottom 2 relegated."; 4 -> "Top 2 promoted."; else -> "Top 2 promoted • bottom 2 relegated." })
                PointsTable(vm, s, comp, null, promo = if (comp.division > 1) 2 else 0, releg = if (comp.division < 4) 2 else 0, playoff = if (comp.division == 1) 4 else 0)
            } else {
                Text(comp.name, color = C.Gold, fontWeight = FontWeight.Bold)
                Sub(compFormatNote(comp))
                when {
                    comp.groups.size > 1 -> comp.groups.indices.forEach { g -> SectionTitle("Group ${'A' + g}"); PointsTable(vm, s, comp, g, playoff = if (comp.knockout == "QF") 2 else 2) }
                    comp.fixtures.any { !CompEngine.isKO(it) } -> PointsTable(vm, s, comp, null, playoff = when (comp.knockout) { "IPL", "SEMIS" -> 4; "FINAL" -> 2; else -> 0 })
                    else -> Sub("Knockout competition - see the bracket below.")
                }
            }
            if (comp.fixtures.any { CompEngine.isKO(it) }) {
                SectionTitle("Playoffs")
                comp.fixtures.filter { CompEngine.isKO(it) }.forEach { f ->
                    Text("${f.stage}: ${s.clubs[f.home]?.short} v ${s.clubs[f.away]?.short} — ${f.res?.text ?: "upcoming"}", color = C.Text, fontSize = 13.sp)
                }
            }
            comp.champion?.let { Text("🏆 Champions: ${s.clubs[it]?.name}", color = C.Gold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
fun PointsTable(vm: GameVM, s: CareerSave, comp: Competition, group: Int?, promo: Int = 0, releg: Int = 0, playoff: Int = 0) {
    val rows = CompEngine.table(comp, group)
    TableHeader(listOf("#" to 0.5f, "Team" to 2.4f, "P" to 0.6f, "W" to 0.6f, "L" to 0.6f, (if (comp.format == "TEST") "D" else "NR") to 0.7f, "Pts" to 0.8f, "NRR" to 1.1f))
    rows.forEachIndexed { i, r ->
        val c = s.clubs[r.team]
        val marker = when { i < playoff -> "Q"; i < promo -> "↑"; i >= rows.size - releg -> "↓"; else -> "" }
        TableRowView(listOf("${i + 1}$marker" to 0.5f, (c?.name ?: r.team) to 2.4f, "${r.p}" to 0.6f, "${r.w}" to 0.6f, "${r.l}" to 0.6f, "${if (comp.format == "TEST") r.d else r.nr}" to 0.7f, "${r.pts}" to 0.8f,
            "%+.3f".format(r.nrr) to 1.1f), highlight = r.team == comp.userTeam) { vm.nav(Screen.Squad(r.team)) }
    }
}


/** live-match playback speeds - all free */
val SPEEDS = listOf(1, 2, 4, 6, 8)

/** Resolve who is on the field for [ev] from the live match + real dataset (batting hand, bowling arm/type). */
fun castFor(m: com.cricketcoach.manager.game.MatchSim, ev: BallEv?, players: Map<String, com.cricketcoach.manager.game.GPlayer>): Cast? {
    val inn = ev?.let { m.innings.getOrNull(it.inn) } ?: m.cur ?: return null
    val batter = ev?.batter ?: inn.bc.getOrNull(inn.striker)?.pid ?: return null
    val bowlerId = ev?.bowler ?: inn.bowler.ifEmpty { m.xiOf(inn.bowlTeam).lastOrNull() ?: return null }
    val ns = listOf(inn.bc.getOrNull(inn.nonStriker)?.pid, inn.bc.getOrNull(inn.striker)?.pid).firstOrNull { it != null && it != batter } ?: batter
    val xi = m.xiOf(inn.bowlTeam)
    val keeper = m.club(inn.bowlTeam).keeper.takeIf { it in xi && it != bowlerId } ?: xi.firstOrNull { players[it]?.role == "WK" && it != bowlerId } ?: xi.firstOrNull { it != bowlerId } ?: bowlerId
    val fielders = xi.filter { it != bowlerId && it != keeper }
    val rb = com.cricketcoach.manager.data.Repo.byId[batter]; val rw = com.cricketcoach.manager.data.Repo.byId[bowlerId]
    val left = rb?.bat?.lowercase()?.startsWith("left") == true
    val bstyle = rw?.bowl?.lowercase() ?: ""
    val leftArm = bstyle.startsWith("left") || bstyle.contains("slow left") || bstyle.contains("left-arm")
    val spin = players[bowlerId]?.bowlType == "SPIN"
    val whites = m.cfg.format == "TEST"
    fun kit(id: String) = m.club(id).let { Kit(it.primary.toInt(), it.secondary.toInt(), whites) }
    val bk = kit(inn.batTeam); var wk = kit(inn.bowlTeam)
    if (!whites && wk.primary == bk.primary) wk = Kit(0xFF1565C0.toInt(), wk.secondary, false)
    // who caught it / ran him out, as an actor slot the 3D planner understands
    var catcher = PlanCast.NONE; var outIsStriker = true; var roFielder = PlanCast.NONE
    if (ev != null && ev.kind == "WICKET") {
        val fp = ev.fielderPid
        fun slot(id: String?): Int = when { id == null -> PlanCast.NONE; id == bowlerId -> PlanCast.BOWLER; id == keeper -> PlanCast.KEEPER; else -> fielders.indexOf(id).takeIf { it in 0..8 } ?: PlanCast.NONE }
        when {
            ev.how.startsWith("c & b") -> catcher = PlanCast.BOWLER
            ev.how.startsWith("c †") -> catcher = PlanCast.KEEPER
            ev.how.startsWith("c ") -> catcher = slot(fp)
            ev.how.startsWith("run out") -> { outIsStriker = ev.outPid == null || ev.outPid == batter; roFielder = slot(fp).takeIf { it >= 0 } ?: PlanCast.NONE }
        }
    }
    return Cast(batter, left, ns, bowlerId, leftArm, spin, keeper, fielders, { players[it]?.country ?: "" }, bk, wk, catcher, outIsStriker, roFielder)
}


fun currentCompIndex(s: CareerSave): Int {
    val i = s.league.indexOfFirst { c -> c.stage != "DONE" && c.fixtures.any { !it.played } }
    return if (i >= 0) i else 0
}

fun compTab(s: CareerSave, c: Competition): String = if (s.mode == Modes.CLUB) "Div ${c.division}" else when (c.key) {
    "RANJI" -> "Ranji"; "SMAT" -> "SMAT"; "VHT" -> "Vijay Hazare"; "DULEEP" -> "Duleep"; "IRANI" -> "Irani Cup"; "IPL" -> "IPL"
    "INTL_TEST" -> "Tests"; "INTL_ODI" -> "ODIs"; "INTL_T20" -> "T20Is"; "ODI_WC" -> "World Cup"; "T20_WC" -> "T20 WC"; "CT" -> "Champions Trophy"
    else -> c.name.take(14)
}

fun compFormatNote(c: Competition): String = when (c.key) {
    "RANJI" -> "First-class (4-day; final 5-day). 32 Elite teams in 4 groups of 8, single round-robin. Win 6 pts, draw 1. Top 2 per group -> quarter-finals, semi-finals, final. (First-innings-lead points are simplified.)"
    "SMAT" -> "T20. 32 teams, 4 groups of 8. Win 4 pts. Top 2 per group -> quarter-finals, semi-finals, final."
    "VHT" -> "List A 50 overs. 32 teams, 4 groups of 8. Win 4 pts. Top 2 per group -> quarter-finals, semi-finals, final."
    "DULEEP" -> "Zonal first-class knockout (2025 format): QF North v East, Central v North East; South and West enter at the semi-finals."
    "IRANI" -> "One-off 5-day match: Ranji Trophy champions v Rest of India."
    "IPL" -> "10 franchises, 2 groups of 5 (real format): 14 league games each (70 matches), top 4 -> Qualifier 1, Eliminator, Qualifier 2, Final."
    "ODI_WC" -> "10 teams, single round-robin, top 4 -> semi-finals, final (ODI)."
    "T20_WC" -> "12 teams in 2 groups of 6, top 2 per group -> semi-finals, final."
    "CT" -> "8 teams in 2 groups of 4, top 2 per group -> semi-finals, final (ODI)."
    else -> if (c.series) "Bilateral series (${c.format})." else c.format
}
