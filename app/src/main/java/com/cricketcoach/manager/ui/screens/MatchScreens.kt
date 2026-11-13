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
import androidx.compose.ui.text.style.TextOverflow
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

/* PreMatchScreen (Match Centre) moved to MatchCentre.kt in v2.5 Phase E */

@Composable
internal fun SmallBtn(text: String, color: Color, modifier: Modifier = Modifier, dark: Boolean = true, onClick: () -> Unit) {
    Box(modifier.padding(2.dp).height(34.dp).clip(RoundedCornerShape(9.dp)).background(color).clickable { GameAudio.click(); onClick() }, contentAlignment = Alignment.Center) {
        Text(text, color = if (dark) Color.Black else C.Text, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
    }
}

/* LiveMatchScreen (fullscreen broadcast view) lives in LiveBroadcast.kt since v2.5 Phase F */

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
        val mine = comp.userTeam
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(comp.name, color = DS.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(compFormatNote(comp), color = DS.Muted, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (mine != null) { Spacer(Modifier.width(10.dp)); TogglePill("My matches", mineOnly) { mineOnly = !mineOnly } }
        }
        FixtureList(s, comp, if (mineOnly && mine != null) mine else null) { detail = it }
    }
    detail?.let { FixtureDetail(s, it, vm) { detail = null } }
}

@Composable
fun FixtureList(s: CareerSave, comp: Competition, team: String?, onOpen: (Fixture) -> Unit) {
    val list = comp.fixtures.filter { team == null || it.home == team || it.away == team }.sortedBy { it.day }
    val played = list.count { it.played }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${list.size} matches", color = DS.Sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp)); Text("·", color = DS.Muted, fontSize = 11.sp); Spacer(Modifier.width(6.dp))
                Text("$played played", color = DS.Muted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                if (team != null) Text("Your fixtures", color = DS.Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        items(list, key = { it.id }) { f -> FixtureRow(s, comp, f, onOpen) }
        if (list.isEmpty()) item { EmptyState("calendar", "No fixtures here yet", "Switch the filter or check another competition.") }
    }
}

@Composable
private fun FixtureRow(s: CareerSave, comp: Competition, f: Fixture, onOpen: (Fixture) -> Unit) {
    val h = s.clubs[f.home]; val a = s.clubs[f.away]
    val r = f.res
    val mine = f.home == comp.userTeam || f.away == comp.userTeam
    val homeWin = r?.winner == f.home; val awayWin = r?.winner == f.away
    DsCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), accent = if (mine) DS.Gold else null,
        onClick = if (f.played) ({ onOpen(f) }) else null, pad = 0.dp) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = if (r != null && r.text.isNotBlank()) 4.dp else 9.dp), verticalAlignment = Alignment.CenterVertically) {
            // date / round block
            Column(Modifier.width(46.dp)) {
                Text(if (f.day > 0) "DAY" else "ROUND", color = DS.Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(if (f.day > 0) "${f.day}" else f.stage.take(6), color = DS.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, maxLines = 1, lineHeight = 20.sp)
            }
            // home
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(h?.short ?: "?", color = if (homeWin) DS.Gold else DS.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                Spacer(Modifier.width(7.dp)); ClubBadge(h, 30.dp)
            }
            // scores
            Column(Modifier.width(74.dp).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (f.played && r != null) {
                    Text(r.hScore.ifEmpty { "–" }, color = if (homeWin) DS.Gold else DS.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                    Text(r.aScore.ifEmpty { "–" }, color = if (awayWin) DS.Gold else DS.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                } else Text("vs", color = DS.Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            // away
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                ClubBadge(a, 30.dp); Spacer(Modifier.width(7.dp))
                Text(a?.short ?: "?", color = if (awayWin) DS.Gold else DS.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            FixtureStatus(f)
        }
        if (r != null && r.text.isNotBlank()) Text(r.text, color = DS.Sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 56.dp, end = 10.dp, bottom = 8.dp))
    }
}

@Composable
private fun FixtureStatus(f: Fixture) {
    val r = f.res
    when {
        !f.played -> DsTag("Upcoming", DS.Muted)
        r == null -> DsTag("FT", DS.Sub)
        r.draw -> DsTag("Draw", DS.Gold)
        r.noResult -> DsTag("NR", DS.Sub)
        else -> DsTag("FT", DS.Accent)
    }
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
                    comp.series -> SeriesScoreView(vm, s, comp)
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

/** A bilateral series has a series score and match results, not a tournament points table. */
@Composable
fun SeriesScoreView(vm: GameVM, s: CareerSave, comp: Competition) {
    val fixtures = comp.fixtures.sortedBy { it.day }
    val teams = comp.teams.take(2).ifEmpty { fixtures.flatMap { listOf(it.home, it.away) }.distinct().take(2) }
    val wins = teams.associateWith { id -> fixtures.count { it.played && it.res?.winner == id } }
    val draws = fixtures.count { it.played && it.res?.winner == null }
    CcmCard(highlight = true) {
        Text("${fixtures.size}-MATCH ${comp.format} SERIES", color = C.Sub, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            teams.forEachIndexed { index, id ->
                if (index > 0) Text("–", color = C.Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    ClubBadge(s.clubs[id], 38.dp)
                    Text(s.clubs[id]?.name ?: id, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    Text("${wins[id] ?: 0}", color = C.Gold, fontWeight = FontWeight.Black, fontSize = 27.sp)
                }
            }
        }
        Text("${fixtures.count { it.played }} of ${fixtures.size} played${if (draws > 0) " · $draws draw / no result" else ""}", color = C.Sub, fontSize = 12.sp)
    }
    SectionTitle("MATCHES")
    fixtures.forEachIndexed { i, f ->
        CcmCard(onClick = if (f.played) ({ vm.openReport(comp.id, f.id) }) else null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MATCH ${i + 1}  ·  ${s.clubs[f.home]?.short ?: f.home} vs ${s.clubs[f.away]?.short ?: f.away}", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(if (f.played) f.res?.text ?: "Result unavailable" else "Upcoming · ${Schedule.fixtureDate(s, comp, f)}", color = if (f.played) C.Gold else C.Sub, fontSize = 12.sp)
                }
            }
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
