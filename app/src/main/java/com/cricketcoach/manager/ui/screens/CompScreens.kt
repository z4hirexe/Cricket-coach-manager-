package com.cricketcoach.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*

private var userTeamNames: Map<String, String> = emptyMap()
private fun teamName(id: String): String = (Consts.NATIONS + Consts.IPL).firstOrNull { it.id == id }?.name ?: userTeamNames[id] ?: id
private fun teamDef(id: String): TeamDef? = (Consts.NATIONS + Consts.IPL).firstOrNull { it.id == id }

@Composable
fun TournamentsScreen(vm: GameVM) {
    vm.save?.let { sv -> userTeamNames = mapOf(sv.userClubId to (sv.clubs[sv.userClubId]?.name ?: "")) }
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val tour = s.tournament
    if (tour == null) CreateTournament(vm, s) else TournamentHub(vm, s, tour)
}

@Composable
private fun CreateTournament(vm: GameVM, s: CareerSave) {
    var type by remember { mutableStateOf<String?>(null) }
    var team by remember { mutableStateOf<String?>(null) }
    var opp by remember { mutableStateOf<String?>(null) }
    var format by remember { mutableStateOf("T20") }
    var matches by remember { mutableIntStateOf(3) }
    var double by remember { mutableStateOf(false) }
    var ko by remember { mutableStateOf("SEMIS") }
    val custom = remember { mutableStateListOf<String>() }
    ScreenScaffold(vm, "Tournaments") {
        Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
            Sub("Run international and franchise tournaments alongside your club career, using real national squads (best available players by rating) and real IPL rosters. Results go into the record books and awards.")
            if (type == null) {
                for (tt in Tournaments.TYPES) CcmCard(onClick = { type = tt.id; team = null; opp = null; tt.fixedFormat?.let { format = it } ?: run { format = if (tt.id == "ASIA") "T20" else "ODI" } }) {
                    Text(tt.name, color = C.Gold, fontWeight = FontWeight.Bold); Sub(tt.desc)
                }
                if (s.awards.isNotEmpty()) { SectionTitle("Recent champions"); s.awards.take(8).forEach { Sub("${it.comp}: ${it.champion}") } }
            } else {
                val tt = Tournaments.TYPES.first { it.id == type }
                Text(tt.name, color = C.Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp); Sub(tt.desc)
                TextButton(onClick = { type = null }) { Text("← choose another tournament", color = C.Sub) }
                if (tt.fixedFormat == null) {
                    SectionTitle("Format")
                    FlowRowSimple(if (type == "ASIA") listOf("T20", "ODI") else listOf("T20", "ODI", "TEST"), format, mapOf("T20" to "T20 (20 ov)", "ODI" to "ODI (50 ov)", "TEST" to "Test (5-day)")) { format = it }
                }
                if (type == "CUSTOM") {
                    SectionTitle("Teams (${custom.size} selected, 3–12)")
                    Sub("Tap to include. Mixing nations, IPL franchises and your own club is allowed.")
                    for (row in (listOfNotNull(vm.save?.userClubId?.takeIf { vm.save?.mode == Modes.CLUB }) + Tournaments.teamsFor("CUSTOM")).chunked(4)) Row {
                        for (id in row) FilterChip(selected = id in custom, onClick = { if (id in custom) custom.remove(id) else if (custom.size < 12) custom.add(id) }, label = { Text(teamDef(id)?.short ?: vm.save?.clubs?.get(id)?.short ?: id, fontSize = 11.sp) }, modifier = Modifier.padding(end = 4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(double, { double = it }); Spacer(Modifier.width(6.dp)); Text("Double round-robin", color = C.Text) }
                    FlowRowSimple(listOf("NONE", "FINAL", "SEMIS", "IPL"), ko, mapOf("NONE" to "League only", "FINAL" to "Top-2 final", "SEMIS" to "Semis + final", "IPL" to "IPL playoffs")) { ko = it }
                }
                SectionTitle(if (type == "BILAT") "Your team" else "Team you will manage")
                val pool = if (type == "CUSTOM") custom.toList() else Tournaments.teamsFor(type!!)
                for (row in pool.chunked(3)) Row {
                    for (id in row) FilterChip(selected = team == id, onClick = { team = id; if (opp == id) opp = null }, label = { Text(teamName(id), fontSize = 12.sp) }, modifier = Modifier.padding(end = 4.dp))
                }
                if (type == "BILAT") {
                    SectionTitle("Opponent")
                    for (row in pool.filter { it != team }.chunked(3)) Row {
                        for (id in row) FilterChip(selected = opp == id, onClick = { opp = id }, label = { Text(teamName(id), fontSize = 12.sp) }, modifier = Modifier.padding(end = 4.dp))
                    }
                    SectionTitle("Series length")
                    FlowRowSimple(listOf("1", "2", "3", "5"), matches.toString(), mapOf("1" to "1 match", "2" to "2 matches", "3" to "3 matches", "5" to "5 matches")) { matches = it.toInt() }
                }
                Spacer(Modifier.height(12.dp))
                val ok = team != null && (type != "BILAT" || opp != null) && (type != "CUSTOM" || custom.size >= 3) && !(type == "CUSTOM" && ko == "SEMIS" && custom.size < 4) && !(type == "CUSTOM" && ko == "IPL" && custom.size < 4)
                GoldButton("Start ${tt.name}", Modifier.fillMaxWidth(), enabled = ok) {
                    vm.startTournament(type!!, team!!, format, if (type == "BILAT") listOf(team!!, opp!!) else custom.toList(), matches, double, ko)
                }
                if (type == "CUSTOM" && (ko == "SEMIS" || ko == "IPL") && custom.size < 4) Sub("Semis / IPL playoffs need at least 4 teams.")
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun TournamentHub(vm: GameVM, s: CareerSave, tour: Competition) {
    var tab by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Fixture?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    val me = tour.userTeam ?: ""
    ScreenScaffold(vm, tour.name) {
        CcmCard(Modifier.padding(horizontal = 12.dp), highlight = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClubBadge(s.clubs[me], 44.dp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("You manage ${s.clubs[me]?.name}", color = C.Text, fontWeight = FontWeight.Bold)
                    Sub("${tour.format} • ${tour.teams.size} teams • stage: ${tour.stage.lowercase()}")
                }
            }
            if (tour.stage == "DONE") {
                Text("🏆 Champions: ${tour.champion?.let { s.clubs[it]?.name } ?: "Series drawn"}", color = C.Gold, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                tour.runnerUp?.let { Sub("Runners-up: ${s.clubs[it]?.name}") }
                s.awards.firstOrNull { it.comp == tour.name }?.let { a ->
                    Sub("Most runs: ${s.players[a.orange]?.name ?: "-"} (${a.orangeRuns}) • Most wickets: ${s.players[a.purple]?.name ?: "-"} (${a.purpleWk})")
                    Sub("Player of the tournament: ${s.players[a.mvp]?.name ?: "-"}")
                }
                Spacer(Modifier.height(6.dp))
                GoldButton("Finish & create another", Modifier.fillMaxWidth()) { vm.closeTournament() }
            } else {
                val next = CompEngine.nextUnplayed(tour)
                if (next != null) Sub("Next: ${next.stage} — ${s.clubs[next.home]?.short} v ${s.clubs[next.away]?.short}")
                Spacer(Modifier.height(6.dp))
                Row {
                    GoldButton(if (next != null && (next.home == me || next.away == me)) "Play my match" else "Sim next match", Modifier.weight(1f)) { vm.playNextTournament() }
                    Spacer(Modifier.width(6.dp))
                    OutlineBtn("Sim to my match", Modifier.weight(1f)) { vm.simTournamentUntilMine() }
                }
                Row {
                    OutlineBtn("My squad & XI", Modifier.weight(1f)) { vm.nav(Screen.Squad(me)) }
                    Spacer(Modifier.width(6.dp))
                    OutlineBtn("Sim all", Modifier.weight(1f)) { vm.simWholeTournament() }
                    Spacer(Modifier.width(6.dp))
                    OutlineBtn("Abandon", Modifier.weight(1f)) { confirmClose = true }
                }
            }
        }
        Tabs(listOf("Table", "Fixtures", "Top performers"), tab) { tab = it }
        when (tab) {
            0 -> Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
                if (tour.groups.size > 1) tour.groups.indices.forEach { g -> SectionTitle("Group ${'A' + g}"); PointsTable(vm, s, tour, g, playoff = 2) }
                else PointsTable(vm, s, tour, null, playoff = when (tour.knockout) { "SEMIS", "IPL" -> 4; "FINAL" -> 2; else -> 0 })
                Sub("Points: win ${tour.pointsWin}, ${if (tour.format == "TEST") "draw" else "tie/NR"} ${tour.pointsDraw}. Ties on points are separated by wins, then net run rate.")
                val kos = tour.fixtures.filter { CompEngine.isKO(it) }
                if (kos.isNotEmpty()) { SectionTitle("Knockouts"); kos.forEach { f -> Text("${f.stage}: ${s.clubs[f.home]?.short} v ${s.clubs[f.away]?.short} — ${f.res?.text ?: "upcoming"}", color = C.Text, fontSize = 13.sp) } }
            }
            1 -> FixtureList(s, tour, null) { detail = it }
            else -> TopPerformers(vm, s, tour.bat, tour.bowlS)
        }
    }
    detail?.let { FixtureDetail(s, it) { detail = null } }
    if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false }, containerColor = C.Surface, title = { Text("Abandon tournament?") },
        text = { Text("Results already played stay in the record books.") },
        confirmButton = { TextButton(onClick = { confirmClose = false; vm.closeTournament() }) { Text("Abandon", color = C.Red) } },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Cancel") } })
}

@Composable
fun TopPerformers(vm: GameVM, s: CareerSave, bat: Map<String, BatAgg>, bowl: Map<String, BowlAgg>) {
    LazyColumn(Modifier.padding(horizontal = 10.dp)) {
        item { SectionTitle("Most runs"); TableHeader(listOf("Player" to 3f, "Team" to 1f, "Inn" to 0.7f, "Runs" to 0.9f, "HS" to 0.8f, "Avg" to 0.9f, "SR" to 0.9f)) }
        items(bat.entries.sortedByDescending { it.value.runs }.take(10)) { (pid, a) ->
            TableRowView(listOf((s.players[pid]?.name ?: pid) to 3f, a.team to 1f, "${a.inn}" to 0.7f, "${a.runs}" to 0.9f, "${a.hs}${if (a.hsNo) "*" else ""}" to 0.8f, f1(a.avg()) to 0.9f, f1(a.sr()) to 0.9f)) { vm.nav(Screen.Profile(pid)) }
        }
        item { SectionTitle("Most wickets"); TableHeader(listOf("Player" to 3f, "Team" to 1f, "Wkts" to 0.8f, "Avg" to 0.9f, "Econ" to 0.9f, "Best" to 0.9f)) }
        items(bowl.entries.filter { it.value.balls > 0 }.sortedWith(compareByDescending<Map.Entry<String, BowlAgg>> { it.value.wk }.thenBy { it.value.runs }).take(10)) { (pid, a) ->
            TableRowView(listOf((s.players[pid]?.name ?: pid) to 3f, a.team to 1f, "${a.wk}" to 0.8f, f1(a.avg()) to 0.9f, f2(a.econ()) to 0.9f, (if (a.bbW > 0) "${a.bbW}/${a.bbR}" else "-") to 0.9f)) { vm.nav(Screen.Profile(pid)) }
        }
        item { SectionTitle("Most sixes") }
        items(bat.entries.sortedByDescending { it.value.sixes }.take(5)) { (pid, a) -> TableRowView(listOf((s.players[pid]?.name ?: pid) to 3f, a.team to 1f, "${a.sixes} sixes" to 1.5f, "${a.fours} fours" to 1.5f)) }
        item { SectionTitle("Most catches") }
        items(bowl.entries.sortedByDescending { it.value.ct }.take(5)) { (pid, a) -> TableRowView(listOf((s.players[pid]?.name ?: pid) to 3f, "${a.ct} catches" to 2f)) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/* ======================= Statistics ======================= */

@Composable
fun StatisticsScreen(vm: GameVM) {
    val s = vm.save ?: return
    var tab by remember { mutableIntStateOf(0) }
    ScreenScaffold(vm, "Statistics & Records") {
        Tabs(listOf("Records", "Leaders", "Awards", "Real-world team records"), tab) { tab = it }
        when (tab) {
            0, 1 -> {
                val keys = s.records.keys.sortedWith(compareBy({ !it.startsWith("ALL") }, { it }))
                if (keys.isEmpty()) { Sub("No matches played yet.", Modifier.padding(16.dp)); return@ScreenScaffold }
                var key by remember { mutableStateOf(keys.first()) }
                val bk = s.records[key] ?: s.records[keys.first()]!!
                Column(Modifier.padding(horizontal = 10.dp)) {
                    Sub("Competition / format")
                    FlowRowSimple(keys, key, keys.associateWith { k -> s.records[k]!!.let { "${it.label.take(22)} • ${it.format}" } }) { key = it }
                }
                if (tab == 1) TopPerformers(vm, s, bk.bat, bk.bowl)
                else LazyColumn(Modifier.padding(horizontal = 10.dp)) {
                    item { SectionTitle("Highest individual scores") }
                    items(bk.highScores.take(10)) { r -> TableRowView(listOf((s.players[r.who]?.name ?: r.who) to 3f, "${r.a}${if (r.flag) "*" else ""} (${r.b})" to 1.4f, "${r.team} v ${r.opp}" to 1.8f, "S${r.season}" to 0.6f)) { vm.nav(Screen.Profile(r.who)) } }
                    item { SectionTitle("Best bowling figures") }
                    items(bk.bestBowl.take(10)) { r -> TableRowView(listOf((s.players[r.who]?.name ?: r.who) to 3f, "${r.a}/${r.b}" to 1.4f, "${r.team} v ${r.opp}" to 1.8f, "S${r.season}" to 0.6f)) { vm.nav(Screen.Profile(r.who)) } }
                    item { SectionTitle("Highest team totals") }
                    items(bk.teamHigh.take(10)) { r -> TableRowView(listOf(r.team to 1.5f, "${r.a}/${r.b}" to 1.4f, "v ${r.opp}" to 1.5f, r.comp.take(18) to 2f)) }
                    item { SectionTitle("Lowest completed totals") }
                    items(bk.teamLow.take(10)) { r -> TableRowView(listOf(r.team to 1.5f, "${r.a}" to 1.4f, "v ${r.opp}" to 1.5f, r.comp.take(18) to 2f)) }
                    item {
                        SectionTitle("Most hundreds / fifties")
                        bk.bat.entries.filter { it.value.f100 + it.value.f50 > 0 }.sortedWith(compareByDescending<Map.Entry<String, BatAgg>> { it.value.f100 }.thenByDescending { it.value.f50 }).take(8).forEach { (pid, a) ->
                            TableRowView(listOf((s.players[pid]?.name ?: pid) to 3f, "100s ${a.f100}" to 1.2f, "50s ${a.f50}" to 1.2f))
                        }
                        SectionTitle("Most fours")
                        bk.bat.entries.sortedByDescending { it.value.fours }.take(5).forEach { (pid, a) -> TableRowView(listOf((s.players[pid]?.name ?: pid) to 3f, "${a.fours}" to 1f)) }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
            2 -> LazyColumn(Modifier.padding(12.dp)) {
                item { Sub("Season & tournament awards (career mode). Player of the tournament = MVP points: runs + 2×sixes + 25×wickets + 8×catches.") }
                items(s.awards) { a ->
                    CcmCard {
                        Text("Season ${a.season} • ${a.comp}", color = C.Gold, fontWeight = FontWeight.Bold)
                        KV("Champion", a.champion); KV("Runner-up", a.runnerUp)
                        KV("Most runs", "${s.players[a.orange]?.name ?: "-"} (${a.orangeRuns})"); KV("Most wickets", "${s.players[a.purple]?.name ?: "-"} (${a.purpleWk})")
                        KV("Player of the tournament", "${s.players[a.mvp]?.name ?: "-"} (${a.mvpPts} pts)")
                    }
                }
                item {
                    SectionTitle("All-time (career mode)")
                    val all = s.players.values.filter { it.careerRuns > 0 || it.careerWk > 0 }
                    all.sortedByDescending { it.careerRuns }.take(5).forEach { KV("Runs: ${it.name}", "${it.careerRuns}") }
                    all.sortedByDescending { it.careerWk }.take(5).forEach { KV("Wickets: ${it.name}", "${it.careerWk}") }
                    if (s.awards.isEmpty()) Sub("Awards appear when a competition finishes.")
                }
            }
            else -> {
                var fmt by remember { mutableStateOf("ODI") }
                Column(Modifier.padding(horizontal = 10.dp)) {
                    FlowRowSimple(listOf("TEST", "ODI", "T20I", "IPL"), fmt) { fmt = it }
                    Sub("Real matches in Cricsheet coverage (${Repo.ds.meta.coverage[fmt]?.let { "${it.first} → ${it.last}" } ?: "-"}). Historical; not career-mode results.")
                }
                val set = Repo.ds.teamRecords[fmt]
                LazyColumn(Modifier.padding(horizontal = 10.dp)) {
                    item { SectionTitle("Highest team totals") }
                    items(set?.high ?: emptyList()) { r -> TableRowView(listOf(r.team to 2f, "${r.runs}/${r.wk}" to 1f, "v ${r.opp}" to 2f, r.date to 1.4f)) }
                    item { SectionTitle("Lowest completed totals") }
                    items(set?.low ?: emptyList()) { r -> TableRowView(listOf(r.team to 2f, "${r.runs}" to 1f, "v ${r.opp}" to 2f, r.date to 1.4f)) }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/* ======================= Season summary ======================= */

@Composable
fun SeasonSummaryScreen(vm: GameVM) {
    val s = vm.save ?: return
    ScreenScaffold(vm, "Season ${s.season} Review") {
        Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
            if (s.phase != "OFFSEASON") { Sub("The season is still in progress."); return@Column }
            Text("🏆", fontSize = 48.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            s.seasonSummary.forEach { Text("• $it", color = C.Text, fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp)) }
            SectionTitle("Champions")
            s.awards.filter { it.season == s.season }.forEach { a -> CcmCard { Text(a.comp, color = C.Gold, fontWeight = FontWeight.Bold); Sub("Champion ${a.champion} • Most runs ${s.players[a.orange]?.name ?: "-"} (${a.orangeRuns}) • Most wickets ${s.players[a.purple]?.name ?: "-"} (${a.purpleWk})") } }
            Spacer(Modifier.height(12.dp))
            Sub("Next season: ${if ((s.season + 1) % 3 == 1) "MEGA AUCTION year (retain up to 4)" else "mini auction available"}. Sponsors are renewed in pre-season.")
            Spacer(Modifier.height(8.dp))
            GoldButton("Start Season ${s.season + 1}", Modifier.fillMaxWidth()) { vm.beginNextSeason() }
            Spacer(Modifier.height(30.dp))
        }
    }
}
