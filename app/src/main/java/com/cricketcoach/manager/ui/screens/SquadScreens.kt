package com.cricketcoach.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*

fun editableClub(vm: GameVM, clubId: String): Boolean {
    val s = vm.save ?: return false
    if (clubId == s.userClubId) return true
    return s.tournament?.userTeam == clubId && s.tournament?.stage != "DONE"
}

@Composable
fun SquadScreen(vm: GameVM, clubIdArg: String?) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val clubId = clubIdArg ?: s.userClubId
    val c = s.clubs[clubId] ?: return
    val editable = editableClub(vm, clubId)
    var tab by remember { mutableIntStateOf(0) }
    var roleDialog by remember { mutableStateOf<GPlayer?>(null) }
    ScreenScaffold(vm, if (editable) "${c.short} — Team Selection" else c.name) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ClubBadge(c, 40.dp); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, color = C.Gold, fontWeight = FontWeight.Bold)
                Sub("${c.squad.size} players • overseas ${if (c.kind == "NATION") "-" else "${Career.overseasCount(s, c)}/8"} • strength ${"%.0f".format(Career.strength(s, c))}")
            }
            if (editable && clubId == s.userClubId) {
                val rc = Registration.nextPending(s) ?: s.league.firstOrNull { Registration.required(s, it) && it.fixtures.any { f -> !f.played } }
                if (rc != null) { OutlineBtn(if (Registration.registered(s, rc)) "${Registration.short(rc)} squad" else "Register ${Registration.short(rc)} squad") { vm.nav(Screen.SquadRegistration(rc.id)) }; Spacer(Modifier.width(6.dp)) }
            }
            if (editable) OutlineBtn("Auto-pick") { TeamSelector.autoPick(c, s.players); vm.persist(); vm.msg("Best XI selected") }
        }
        Tabs(listOf("Playing XI", "Full squad", "Bowling plan", "Tactics") + if (clubId == s.userClubId) listOf("Academy") else emptyList(), tab) { tab = it }
        when (tab) {
            0 -> XiTab(vm, s, c, editable) { roleDialog = it }
            1 -> SquadTab(vm, s, c, editable) { roleDialog = it }
            2 -> BowlingTab(vm, s, c, editable)
            3 -> TacticsTab(vm, s, c, editable)
            4 -> AcademyTab(vm, s, c)
        }
    }
    roleDialog?.let { p ->
        AlertDialog(onDismissRequest = { roleDialog = null }, containerColor = C.Surface,
            title = { Text(p.name) },
            text = {
                Column {
                    val inXi = p.id in c.xi
                    Sub("${Consts.ROLE_NAMES[p.role]} • OVR ${p.overall()} • form ${p.form} • fitness ${p.fitness}${if (p.injured) " • INJURED" else ""}")
                    Spacer(Modifier.height(8.dp))
                    if (editable) {
                        if (!inXi) GreenButton("Add to Playing XI", Modifier.fillMaxWidth(), enabled = !p.injured) {
                            if (c.xi.size >= 11) vm.msg("XI is full — remove someone first") else { c.xi.add(p.id); if (c.impact == p.id) c.impact = ""; vm.persist() }; roleDialog = null
                        } else OutlineBtn("Remove from XI", Modifier.fillMaxWidth()) { c.xi.remove(p.id); c.bowlers.remove(p.id); vm.persist(); roleDialog = null }
                        if (inXi) {
                            OutlineBtn("Make Captain", Modifier.fillMaxWidth()) { c.captain = p.id; if (c.vice == p.id) c.vice = ""; vm.persist(); roleDialog = null }
                            OutlineBtn("Make Vice-captain", Modifier.fillMaxWidth()) { c.vice = p.id; if (c.captain == p.id) c.captain = ""; vm.persist(); roleDialog = null }
                            OutlineBtn("Make Wicket-keeper", Modifier.fillMaxWidth()) { c.keeper = p.id; vm.persist(); roleDialog = null }
                        } else OutlineBtn("Set as Impact Player (sub)", Modifier.fillMaxWidth(), enabled = !p.injured) { c.impact = p.id; vm.persist(); roleDialog = null }
                    }
                    OutlineBtn("View profile", Modifier.fillMaxWidth()) { roleDialog = null; vm.nav(Screen.Profile(p.id)) }
                }
            },
            confirmButton = { TextButton(onClick = { roleDialog = null }) { Text("Close") } })
    }
}

@Composable
private fun tags(c: Club, pid: String): String = buildList {
    if (c.captain == pid) add("C"); if (c.vice == pid) add("VC"); if (c.keeper == pid) add("WK"); if (c.impact == pid) add("IMPACT")
}.joinToString(" ")

@Composable
private fun XiTab(vm: GameVM, s: CareerSave, c: Club, editable: Boolean, onPick: (GPlayer) -> Unit) {
    LazyColumn(Modifier.padding(horizontal = 12.dp)) {
        item {
            val notes = buildList {
                if (c.xi.size != 11) add("XI has ${c.xi.size}/11 players")
                if (c.keeper !in c.xi) add("No wicket-keeper selected")
                if (c.captain !in c.xi) add("No captain selected")
                val os = c.xi.mapNotNull { s.players[it] }.count { TeamSelector.isOverseas(it, c) }
                if (c.kind != "NATION" && os > 4) add("Too many overseas players in XI ($os/4)")
                if (c.xi.mapNotNull { s.players[it] }.count { it.bowl >= 50 } < 5) add("Fewer than 5 genuine bowling options")
                if (c.xi.any { s.players[it]?.injured == true }) add("Injured player in XI")
            }
            if (notes.isEmpty()) Sub("✓ XI is valid. Tap a player for options; use arrows to set the batting order.") else notes.forEach { Text("⚠ $it", color = C.Gold, fontSize = 12.sp) }
            Spacer(Modifier.height(4.dp))
        }
        itemsIndexed(c.xi.toList()) { i, pid ->
            val p = s.players[pid] ?: return@itemsIndexed
            PlayerRow(p, s.year, onClick = { onPick(p) }, extra = "#${i + 1} ${tags(c, pid)}", trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingPill(p.overall())
                    if (editable) {
                        TextButton(onClick = { if (i > 0) { val l = c.xi; val x = l[i - 1]; l[i - 1] = l[i]; l[i] = x; vm.persist() } }, contentPadding = PaddingValues(4.dp)) { Text("▲", color = C.Gold) }
                        TextButton(onClick = { if (i < c.xi.size - 1) { val l = c.xi; val x = l[i + 1]; l[i + 1] = l[i]; l[i] = x; vm.persist() } }, contentPadding = PaddingValues(4.dp)) { Text("▼", color = C.Gold) }
                    }
                }
            })
        }
        item {
            val imp = s.players[c.impact]
            SectionTitle("Impact player (T20 substitute)")
            if (imp != null) PlayerRow(imp, s.year, onClick = { onPick(imp) }, extra = "IMPACT") else Sub("None selected — tap a bench player in Full squad to set one.")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SquadTab(vm: GameVM, s: CareerSave, c: Club, editable: Boolean, onPick: (GPlayer) -> Unit) {
    var sort by remember { mutableStateOf("OVR") }
    val ps = c.squad.mapNotNull { s.players[it] }.let { l ->
        when (sort) { "Role" -> l.sortedBy { it.role }; "Age" -> l.sortedBy { it.age(s.year) }; "Form" -> l.sortedByDescending { it.form }; else -> l.sortedByDescending { it.overall() } }
    }
    LazyColumn(Modifier.padding(horizontal = 12.dp)) {
        item { FlowRowSimple(listOf("OVR", "Role", "Age", "Form"), sort) { sort = it } }
        items(ps, key = { it.id }) { p ->
            PlayerRow(p, s.year, onClick = { onPick(p) }, highlight = p.id in c.xi,
                extra = "${if (p.id in c.xi) "XI " else ""}${tags(c, p.id)} • ${Money.fmt(p.salary)}/yr • ${p.contractYears}y${if (TeamSelector.isOverseas(p, c)) " • OS" else ""}")
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BowlingTab(vm: GameVM, s: CareerSave, c: Club, editable: Boolean) {
    LazyColumn(Modifier.padding(horizontal = 12.dp)) {
        item { Sub("Bowling rotation: the captain uses these bowlers in priority order (max quota per bowler applies; at least 5 options). Death and powerplay specialists are chosen automatically from the plan.") }
        itemsIndexed(c.bowlers.toList()) { i, pid ->
            val p = s.players[pid] ?: return@itemsIndexed
            CcmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", color = C.Gold, modifier = Modifier.width(26.dp))
                    Column(Modifier.weight(1f)) { Text(p.name, color = C.Text, fontWeight = FontWeight.Bold); Sub("${p.bowlType} • bowling ${p.bowl}") }
                    if (editable) {
                        TextButton(onClick = { if (i > 0) { val l = c.bowlers; val x = l[i - 1]; l[i - 1] = l[i]; l[i] = x; vm.persist() } }) { Text("▲", color = C.Gold) }
                        TextButton(onClick = { if (i < c.bowlers.size - 1) { val l = c.bowlers; val x = l[i + 1]; l[i + 1] = l[i]; l[i] = x; vm.persist() } }) { Text("▼", color = C.Gold) }
                        TextButton(onClick = { c.bowlers.remove(pid); vm.persist() }) { Text("✕", color = C.Red) }
                    }
                }
            }
        }
        item {
            if (editable) {
                SectionTitle("Add from XI")
                val rest = c.xi.filter { it !in c.bowlers }.mapNotNull { s.players[it] }.sortedByDescending { it.bowl }
                for (p in rest) CcmCard(onClick = { if (c.bowlers.size < 8) { c.bowlers.add(p.id); vm.persist() } }) {
                    Row { Text(p.name, color = C.Text, modifier = Modifier.weight(1f)); Sub("bowl ${p.bowl}  + add") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TacticsTab(vm: GameVM, s: CareerSave, c: Club, editable: Boolean) {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        SectionTitle("Batting approach")
        FlowRowSimple(listOf("0", "1", "2"), c.aggression.toString(), mapOf("0" to "Defensive", "1" to "Balanced", "2" to "Attacking")) { if (editable) { c.aggression = it.toInt(); vm.persist() } }
        Sub("Attacking raises scoring rate and boundary chances but also the risk of losing wickets. The match engine also adapts to the required rate and match phase (powerplay/death).")
        if (c.id == s.userClubId) {
            SectionTitle("Training")
            FlowRowSimple(listOf("0", "1", "2", "3"), c.trainFocus.toString(), mapOf("0" to "Balanced", "1" to "Batting", "2" to "Bowling", "3" to "Fitness")) { c.trainFocus = it.toInt(); vm.persist() }
            Sub("Intensity")
            FlowRowSimple(listOf("0", "1", "2"), c.trainIntensity.toString(), mapOf("0" to "Light", "1" to "Normal", "2" to "Intense")) { c.trainIntensity = it.toInt(); vm.persist() }
            Sub("Intense training develops players faster (towards their potential) but costs fitness and raises injury risk. Facilities and coaches (My Club) boost gains.")
        }
    }
}

@Composable
private fun AcademyTab(vm: GameVM, s: CareerSave, c: Club) {
    LazyColumn(Modifier.padding(horizontal = 12.dp)) {
        item { Sub("Youth academy (level ${c.academyLvl}). New fictional prospects join every off-season; better academies produce higher potential. Promote them when ready.") }
        items(c.academyList.toList()) { pid ->
            val p = s.players[pid] ?: return@items
            PlayerRow(p, s.year, onClick = { vm.nav(Screen.Profile(p.id)) }, extra = "Potential ${p.potential}", trailing = {
                Column {
                    TextButton(onClick = { vm.act { Career.promoteYouth(it, pid) } }) { Text("Promote", color = C.Green) }
                    TextButton(onClick = { vm.act { Career.releaseYouth(it, pid) } }) { Text("Release", color = C.Red) }
                }
            })
        }
        item { if (c.academyList.isEmpty()) Sub("No academy players yet."); Spacer(Modifier.height(24.dp)) }
    }
}

/* ======================= Player database ======================= */

@Composable
fun DatabaseScreen(vm: GameVM) {
    val s = vm.save
    var q by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("All") }
    var role by remember { mutableStateOf("All") }
    var scope by remember { mutableStateOf(if (s != null) "All" else "Real") }
    var sort by remember { mutableStateOf("OVR") }
    val countries = listOf("All") + Consts.NATIONS.map { it.country }
    data class Row(val id: String, val name: String, val country: String, val role: String, val ovr: Int, val age: Int, val club: String, val fictional: Boolean, val fa: Boolean)
    val all = remember(s, vm.tick) {
        if (s != null) s.players.values.filter { !it.academy }.map {
            Row(it.id, it.name, it.country, it.role, it.overall(), it.age(s.year), if (it.retired) "Retired" else s.clubs[it.clubId]?.short ?: "Free agent", it.fictional, it.clubId.isEmpty() && !it.retired)
        } else Repo.ds.players.map { rp ->
            val a = Repo.attrs[rp.id]
            Row(rp.id, rp.name, rp.country, rp.role, a?.overall ?: 50, rp.dob?.take(4)?.toIntOrNull()?.let { 2026 - it } ?: 0, rp.ipl ?: "", false, false)
        }
    }
    val list = all.filter {
        (country == "All" || it.country == country) && (role == "All" || it.role == role) &&
            (q.isBlank() || it.name.contains(q, true)) &&
            when (scope) { "Real" -> !it.fictional; "Free agents" -> it.fa; "Fictional" -> it.fictional; "Retired" -> it.club == "Retired"; else -> it.club != "Retired" || s == null }
    }.let { l -> when (sort) { "Name" -> l.sortedBy { it.name }; "Age" -> l.sortedBy { it.age }; else -> l.sortedByDescending { it.ovr } } }
    ScreenScaffold(vm, "Player Database") {
        Column(Modifier.padding(horizontal = 12.dp)) {
            OutlinedTextField(q, { q = it }, label = { Text("Search player") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            var showFilters by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sub("${list.size} players • $country • ${if (role == "All") "All roles" else Consts.ROLE_NAMES[role]} • $scope", Modifier.weight(1f))
                TextButton(onClick = { showFilters = !showFilters }) { Text(if (showFilters) "Hide filters" else "Filters", color = C.Gold) }
            }
            if (showFilters) {
                FlowRowSimple(countries, country) { country = it }
                FlowRowSimple(listOf("All", "BAT", "BOWL", "AR", "WK"), role, Consts.ROLE_NAMES) { role = it }
                if (s != null) FlowRowSimple(listOf("All", "Real", "Free agents", "Fictional", "Retired"), scope) { scope = it }
                FlowRowSimple(listOf("OVR", "Name", "Age"), sort) { sort = it }
            }
        }
        LazyColumn(Modifier.padding(horizontal = 12.dp)) {
            items(list.take(300), key = { it.id }) { r ->
                CcmCard(onClick = { vm.nav(Screen.Profile(r.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, color = C.Text, fontWeight = FontWeight.Bold)
                            Sub("${Consts.ROLE_NAMES[r.role]} • ${r.country} • ${if (r.age > 0) "${r.age}y" else "age n/a"} • ${r.club.ifEmpty { "-" }}${if (r.fictional) " • Fictional" else ""}")
                        }
                        RatingPill(r.ovr)
                    }
                }
            }
            item { if (list.size > 300) Sub("Showing top 300 — refine your search."); Spacer(Modifier.height(24.dp)) }
        }
    }
}
