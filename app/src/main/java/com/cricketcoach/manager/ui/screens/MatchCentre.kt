package com.cricketcoach.manager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*

/* =====================================================================================================
 * v2.5 Phase E: Match Centre (team sheet, batting order, bowling plan, match info, opponent) with a
 * mandatory pre-match flow: registered tournament squad -> XI -> C / VC / WK -> bowling plan ->
 * confirm team sheet -> toss -> play.  Plus the tournament Squad Registration screen.
 * ===================================================================================================== */

private val Navy = Color(0xFF0A1530)
private val NavyHi = Color(0xFF13234A)
private val Line = Color(0x333D5A99)
private val Ok = Color(0xFF43C66B)
private val Warn = Color(0xFFFBBF24)

private fun formOf(s: CareerSave, team: String): List<Char> =
    (s.league + listOfNotNull(s.tournament)).flatMap { c -> c.fixtures.filter { it.played && (it.home == team || it.away == team) }.map { c to it } }
        .sortedBy { it.second.day }.takeLast(5).map { (_, f) ->
            val r = f.res
            when { r == null -> '-'; r.noResult -> 'N'; r.draw -> 'D'; r.winner == team -> 'W'; r.winner == null -> 'D'; else -> 'L' }
        }

@Composable
private fun FormDots(form: List<Char>) {
    Row {
        if (form.isEmpty()) Text("no games yet", color = C.Sub, fontSize = 9.sp)
        form.forEach { ch ->
            val col = when (ch) { 'W' -> Ok; 'L' -> C.Red; else -> Color(0xFF8899BB) }
            Box(Modifier.padding(end = 2.dp).size(15.dp).clip(RoundedCornerShape(4.dp)).background(col.copy(alpha = 0.22f)).border(1.dp, col.copy(alpha = 0.7f), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                Text("$ch", color = col, fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/** Compact tappable chip (min 32 dp tall tap area). */
@Composable
private fun Pill(text: String, on: Boolean, color: Color = C.Gold, enabled: Boolean = true, minW: Dp = 0.dp, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 32.dp).widthIn(min = minW).clickable(enabled = enabled) { GameAudio.click(); onClick() }.padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.height(24.dp).clip(RoundedCornerShape(7.dp)).background(if (on) color else Color(0x1AFFFFFF)).border(1.dp, if (on) color else Line, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center) {
            Text(text, color = if (on) Color(0xFF111827) else if (enabled) C.Text else C.Sub.copy(alpha = 0.5f), fontSize = 10.5.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun IconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color = C.Gold, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).clickable(enabled = enabled) { GameAudio.click(); onClick() }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(if (enabled) tint.copy(alpha = 0.14f) else Color.Transparent).border(1.dp, if (enabled) tint.copy(alpha = 0.6f) else Line, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = if (enabled) tint else C.Sub.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun FitBar(fit: Int, w: Dp = 46.dp) {
    val col = when { fit >= 80 -> Ok; fit >= 60 -> Warn; else -> C.Red }
    Column(horizontalAlignment = Alignment.End) {
        Box(Modifier.width(w).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x22FFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fit.coerceIn(0, 100) / 100f).background(col))
        }
        Text("FIT $fit", color = C.Sub, fontSize = 8.sp)
    }
}

@Composable
private fun FormMark(form: Int) {
    val (sym, col) = when { form >= 72 -> "▲" to Ok; form < 45 -> "▼" to C.Red; else -> "●" to Color(0xFF8899BB) }
    Text("$sym$form", color = col, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
}

private fun roleShort(r: String) = when (r) { "BAT" -> "BAT"; "WK" -> "WK"; "AR" -> "AR"; else -> "BOWL" }
private fun roleColor(r: String) = when (r) { "BAT" -> Color(0xFF5B9BFF); "WK" -> Color(0xFF22D3EE); "AR" -> Color(0xFFB388FF); else -> Color(0xFF43C66B) }

/** "This comp: 154r @38.5 · 3w" or career totals. */
private fun statLine(comp: Competition, p: GPlayer): String {
    val b = comp.bat[p.id]; val w = comp.bowlS[p.id]
    return if ((b?.m ?: 0) + (w?.m ?: 0) > 0) {
        val parts = ArrayList<String>()
        if (b != null && b.inn > 0) parts += "${b.runs}r" + (b.avg()?.let { " @%.1f".format(it) } ?: "")
        if (w != null && w.balls > 0) parts += "${w.wk}w" + (w.econ()?.let { " e%.1f".format(it) } ?: "")
        "THIS COMP " + parts.joinToString(" · ").ifBlank { "${b?.m ?: w?.m ?: 0} m" }
    } else "CAREER ${p.careerM}m · ${p.careerRuns}r · ${p.careerWk}w"
}

/* ===================================================================================================== Match Centre */

@Composable
fun PreMatchScreen(vm: GameVM, initialTab: Int = 0) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick + vm.simTick
    val comp = vm.pendingComp(); val fx = vm.pendingFixture()
    if (comp == null || fx == null) { ScreenScaffold(vm, "Match Centre") { Sub("No match scheduled.", Modifier.padding(16.dp)) }; return }
    val me = comp.userTeam ?: s.userClubId
    val myClub = s.clubs[me] ?: return
    val opp = s.clubs[if (fx.home == me) fx.away else fx.home] ?: return
    val regOk = Registration.registered(s, comp)
    // the sim (pitch / weather / opponent XI) is only built once the squad is registered
    val m = if (regOk) vm.prepareSim() else null
    var tab by remember { mutableIntStateOf(initialTab) }
    var tossState by remember { mutableStateOf(if (m?.started == true) "DONE" else "CALL") }
    // teams lock after the toss
    val started = m?.started == true || m?.tossWinner?.isNotEmpty() == true
    val edit: () -> Unit = { vm.sheetEdited() }

    ScreenScaffold(vm, "Match Centre") {
        MatchHeader(s, comp, fx, m)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 700.dp
            val tabs = listOf("Team Sheet", "Batting Order", "Bowling Plan", "Match Info", "Opponent") + if (wide) emptyList() else listOf("Start")
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    TabStrip(tabs, tab) { tab = it }
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
                        if (!regOk && tab in 0..2) RegistrationGate(vm, comp)
                        else when (tabs[tab.coerceIn(0, tabs.size - 1)]) {
                            "Team Sheet" -> TeamSheetTab(vm, s, comp, myClub, started, edit)
                            "Batting Order" -> BattingOrderTab(vm, s, comp, myClub, started, edit)
                            "Bowling Plan" -> BowlingPlanTab(vm, s, comp, myClub, started, edit)
                            "Match Info" -> MatchInfoTab(s, comp, fx, m, myClub, opp)
                            "Opponent" -> OpponentTab(vm, s, comp, opp, m)
                            else -> Column(Modifier.verticalScroll(rememberScrollState())) { StartPanel(vm, s, comp, myClub, m, tossState) { tossState = it } }
                        }
                    }
                }
                if (wide) Column(Modifier.width(262.dp).fillMaxHeight().background(Color(0xE60A1228)).drawBehind { drawLine(Color(0x553D5A99), Offset(0f, 0f), Offset(0f, size.height), 2f) }
                    .verticalScroll(rememberScrollState()).padding(10.dp)) {
                    StartPanel(vm, s, comp, myClub, m, tossState) { tossState = it }
                }
            }
        }
    }
}

@Composable
private fun TabStrip(tabs: List<String>, sel: Int, onSel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        tabs.forEachIndexed { i, name ->
            val on = i == sel
            Column(Modifier.fillMaxHeight().clickable { GameAudio.click(); onSel(i) }.padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                Text(name.uppercase(), color = if (on) C.Gold else C.Sub, fontWeight = if (on) FontWeight.Black else FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(36.dp).height(2.dp).background(if (on) C.Gold else Color.Transparent))
            }
        }
    }
}

@Composable
private fun MatchHeader(s: CareerSave, comp: Competition, fx: Fixture, m: MatchSim?) {
    val h = s.clubs[fx.home]!!; val a = s.clubs[fx.away]!!
    Row(Modifier.fillMaxWidth().height(60.dp).background(Brush.horizontalGradient(listOf(Color(h.primary).copy(alpha = 0.35f), Color(0xFF0A1530), Color(0xFF0A1530), Color(a.primary).copy(alpha = 0.35f))))
        .drawBehind { drawLine(Brush.horizontalGradient(listOf(Color.Transparent, C.Gold, Color.Transparent)), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 2f) }
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        TeamSide(s, h, Modifier.weight(1f), alignEnd = false)
        Column(Modifier.weight(1.25f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Stats.compLabel(comp).uppercase(), color = C.Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${fx.stage} · ${Schedule.formatLabel(comp, fx)}", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
            val date = runCatching { Schedule.fixtureDate(s, comp, fx) }.getOrNull()
            Text(listOfNotNull(date?.let { "%s %d %s".format(it.dayOfWeek.name.take(3), it.dayOfMonth, it.month.name.take(3)) }, m?.cfg?.venue ?: Schedule.venue(s, fx)).joinToString(" · "),
                color = C.Sub, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TeamSide(s, a, Modifier.weight(1f), alignEnd = true)
    }
}

@Composable
private fun TeamSide(s: CareerSave, c: Club, modifier: Modifier, alignEnd: Boolean) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start) {
        val info: @Composable () -> Unit = {
            Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
                Text(c.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("STR %.0f".format(Career.strength(s, c)), color = C.Sub, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                FormDots(formOf(s, c.id))
            }
        }
        if (alignEnd) { Box(Modifier.weight(1f, fill = false)) { info() }; Spacer(Modifier.width(8.dp)); ClubBadge(c, 42.dp) }
        else { ClubBadge(c, 42.dp); Spacer(Modifier.width(8.dp)); Box(Modifier.weight(1f, fill = false)) { info() } }
    }
}

/* ---------------------------------------------------------------------------------------------------- side panel */

@Composable
private fun Step(n: Int, title: String, ok: Boolean, detail: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(if (ok) Ok else Color(0x22FFFFFF)).border(1.dp, if (ok) Ok else Warn, CircleShape), contentAlignment = Alignment.Center) {
            if (ok) Icon(Icons.Filled.Check, null, tint = Color(0xFF0B1A10), modifier = Modifier.size(13.dp)) else Text("$n", color = Warn, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = if (ok) C.Sub else Warn, fontSize = 9.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        action?.invoke()
    }
}

@Composable
private fun StartPanel(vm: GameVM, s: CareerSave, comp: Competition, c: Club, m: MatchSim?, tossState: String, setToss: (String) -> Unit) {
    val rule = Registration.rule(s, comp)
    val regOk = Registration.registered(s, comp)
    val started = m?.started == true || m?.tossWinner?.isNotEmpty() == true
    val errors = if (regOk) Registration.sheetErrors(s, comp, c) else emptyList()
    val warns = if (regOk) Registration.sheetWarnings(s, comp, c) else emptyList()
    val confirmed = vm.sheetConfirmed() || started
    Text("PRE-MATCH", color = C.Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
    Spacer(Modifier.height(2.dp))
    Step(1, "Tournament squad", regOk,
        when { rule == null -> "No registration needed"; !regOk -> "Register ${rule.sizeText()} before the first match"
            else -> "${comp.reg.size} registered${if (Registration.locked(s, comp)) " · LOCKED" else " · editable until match 1"}" }) {
        if (rule != null) Pill(if (regOk) "View" else "Register", !regOk) { vm.nav(Screen.SquadRegistration(comp.id)) }
    }
    val xiOk = regOk && c.xi.size == 11 && errors.none { it.startsWith("Pick") || it.startsWith("Injured") || it.startsWith("Not reg") || it.startsWith("No longer") || it.startsWith("Max 4") }
    Step(2, "Playing XI", xiOk, if (!regOk) "Waiting for registration" else "${c.xi.size}/11 selected")
    val leadOk = regOk && errors.none { it.startsWith("Choose") }
    Step(3, "Captain · Vice · Keeper", leadOk, listOf(c.captain, c.vice, c.keeper).map { s.players[it]?.short() ?: "-" }.joinToString(" · "))
    val bowlOk = regOk && errors.none { it.startsWith("Bowling") }
    Step(4, "Bowling plan", bowlOk, "${c.bowlers.count { it in c.xi }} bowlers · ${c.bowlRoles.count { it.key in c.bowlers && it.key in c.xi && it.value != "AUTO" }} roles set")
    Step(5, "Team sheet", confirmed, if (confirmed) "Confirmed - changes re-open it" else "Confirm to unlock the toss")
    if (errors.isNotEmpty()) errors.take(3).forEach { Text("✕ $it", color = C.Red, fontSize = 9.5.sp, lineHeight = 12.sp) }
    if (warns.isNotEmpty()) warns.take(3).forEach { Text("! $it", color = Warn, fontSize = 9.5.sp, lineHeight = 12.sp) }
    Spacer(Modifier.height(6.dp))
    if (!started) {
        if (!confirmed) GoldButton("Confirm team sheet", Modifier.fillMaxWidth(), enabled = regOk && errors.isEmpty()) { vm.msg(vm.confirmTeamSheet() ?: "Team sheet confirmed - time for the toss") }
        else Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ok.copy(alpha = 0.14f)).border(1.dp, Ok.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = Ok, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
            Text("TEAM SHEET CONFIRMED", color = Ok, fontSize = 10.5.sp, fontWeight = FontWeight.Black)
        }
    }
    Spacer(Modifier.height(8.dp))
    // ---- toss
    Text("TOSS", color = C.Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
    when {
        started || tossState == "DONE" -> vm.sim?.takeIf { it.tossWinner.isNotEmpty() }?.let { sim -> Text("${sim.club(sim.tossWinner).short} won the toss and chose to ${sim.decision.lowercase()} first.", color = C.Text, fontSize = 11.sp) }
            ?: Text("Toss done.", color = C.Sub, fontSize = 11.sp)
        tossState == "CHOOSE" -> {
            Text("You won the toss!", color = Ok, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Row { GreenButton("Bat", Modifier.weight(1f)) { vm.chooseToss(true); setToss("DONE") }; Spacer(Modifier.width(6.dp)); GreenButton("Bowl", Modifier.weight(1f)) { vm.chooseToss(false); setToss("DONE") } }
        }
        else -> Row {
            OutlineBtn("Heads", Modifier.weight(1f), enabled = confirmed) { setToss(if (vm.callToss(true)) "CHOOSE" else if (vm.sim?.tossWinner?.isNotEmpty() == true) "DONE" else "CALL") }
            Spacer(Modifier.width(6.dp))
            OutlineBtn("Tails", Modifier.weight(1f), enabled = confirmed) { setToss(if (vm.callToss(false)) "CHOOSE" else if (vm.sim?.tossWinner?.isNotEmpty() == true) "DONE" else "CALL") }
        }
    }
    Spacer(Modifier.height(8.dp))
    GoldButton(if (started && tossState != "DONE") "Resume match" else "Watch live", Modifier.fillMaxWidth(), enabled = confirmed && (tossState == "DONE" || started)) { vm.watchLive() }
    OutlineBtn("Quick sim", Modifier.fillMaxWidth(), enabled = confirmed) { vm.quickSimMatch() }
}

@Composable
private fun RegistrationGate(vm: GameVM, comp: Competition) {
    val s = vm.save ?: return
    val r = Registration.rule(s, comp) ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Warn.copy(alpha = 0.15f)).border(1.dp, Warn, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Lock, null, tint = Warn) }
        Spacer(Modifier.height(6.dp))
        Text("${Registration.short(comp).uppercase()} SQUAD REGISTRATION REQUIRED", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
        Text("Register ${r.sizeText()} before your first match${if (r.overseasMax < 99) " · max ${r.overseasMax} overseas" else ""} · at least ${r.minKeepers} keeper and ${r.minBowlers} bowling options. " +
            "The squad locks once match 1 is played; afterwards only injured or departed players can be replaced (${r.replacements} replacements).",
            color = C.Sub, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 460.dp))
        Spacer(Modifier.height(10.dp))
        GoldButton("Register squad", Modifier.width(220.dp)) { vm.nav(Screen.SquadRegistration(comp.id)) }
    }
}

/* ---------------------------------------------------------------------------------------------------- team sheet */

@Composable
private fun SheetRow(s: CareerSave, comp: Competition, c: Club, p: GPlayer, idx: Int?, onOpen: () -> Unit, trailing: @Composable RowScope.() -> Unit) {
    val avail = Registration.availability(s, comp, c, p)
    val shape = RoundedCornerShape(10.dp)
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(shape).background(if (idx != null) NavyHi.copy(alpha = 0.85f) else Navy.copy(alpha = 0.85f)).border(1.dp, Line, shape)
        .padding(start = 6.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (idx != null) Text("${idx + 1}", color = C.Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(16.dp), textAlign = TextAlign.Center)
        Box(Modifier.clickable { onOpen() }.padding(vertical = 4.dp)) { PlayerFace(p.id, p.name, 30.dp, roleColor(p.role)) }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f).clickable { onOpen() }.padding(vertical = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(4.dp))
                val tags = buildList { if (c.captain == p.id) add("C"); if (c.vice == p.id) add("VC"); if (c.keeper == p.id) add("†"); if (TeamSelector.isOverseas(p, c)) add("OS"); if (c.impact == p.id) add("IMP") }
                tags.forEach { Text(it, color = if (it == "OS") C.Blue else C.Gold, fontSize = 8.5.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(end = 3.dp)) }
                if (avail.isNotEmpty()) TagChip(avail, if (avail == "TIRED") Warn else C.Red)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(roleShort(p.role), color = roleColor(p.role), fontSize = 8.5.sp, fontWeight = FontWeight.Black)
                Text("  ${p.overall()} OVR", color = C.Text, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                val fc = when { p.fitness >= 80 -> Ok; p.fitness >= 60 -> Warn; else -> C.Red }
                Text("  FIT ${p.fitness}", color = fc, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                val (sym, col) = when { p.form >= 72 -> "▲" to Ok; p.form < 45 -> "▼" to C.Red; else -> "●" to Color(0xFF8899BB) }
                Text("  FORM $sym${p.form}", color = col, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            }
            Text(statLine(comp, p), color = C.Sub, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun TeamSheetTab(vm: GameVM, s: CareerSave, comp: Competition, c: Club, started: Boolean, edit: () -> Unit) {
    val pool = Registration.xiPool(s, comp)
    val xi = c.xi.mapNotNull { s.players[it] }
    val bench = c.squad.mapNotNull { s.players[it] }.filter { it.id !in c.xi && !it.retired && (pool == null || it.id in pool) }.sortedByDescending { it.overall() }
    val canEdit = !started
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1.45f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(34.dp)) {
                Text("PLAYING XI  ${xi.size}/11", color = C.Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                Pill("Auto-pick", false, enabled = canEdit) { TeamSelector.autoPick(c, s.players, pool = pool); edit() }
                Pill("Squad ›", false) { vm.nav(Screen.Squad(c.id)) }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(xi, key = { _, p -> p.id }) { i, p ->
                    SheetRow(s, comp, c, p, i, { vm.nav(Screen.Profile(p.id)) }) {
                        Pill("C", c.captain == p.id, enabled = canEdit, minW = 30.dp) { if (c.vice == p.id) c.vice = c.captain; c.captain = p.id; edit() }
                        Pill("VC", c.vice == p.id, enabled = canEdit, minW = 32.dp) { if (c.captain == p.id) c.captain = c.vice; c.vice = p.id; edit() }
                        Pill("WK", c.keeper == p.id, color = Color(0xFF22D3EE), enabled = canEdit, minW = 34.dp) { c.keeper = p.id; edit() }
                        IconBtn(Icons.Filled.KeyboardArrowRight, "to bench", C.Red, enabled = canEdit) {
                            c.xi.remove(p.id); c.bowlers.remove(p.id)
                            if (c.captain == p.id) c.captain = ""; if (c.vice == p.id) c.vice = ""; if (c.keeper == p.id) c.keeper = ""
                            edit()
                        }
                    }
                }
                item { Spacer(Modifier.height(10.dp)) }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(34.dp)) {
                Text(if (pool != null) "BENCH · REGISTERED ${bench.size}" else "BENCH ${bench.size}", color = C.Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                if (comp.allowImpact) Text("IMPACT: ${s.players[c.impact]?.short() ?: "none"}", color = C.Sub, fontSize = 9.sp)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(bench, key = { it.id }) { p ->
                    SheetRow(s, comp, c, p, null, { vm.nav(Screen.Profile(p.id)) }) {
                        if (comp.allowImpact) Pill("IMP", c.impact == p.id, color = UI.Violet, enabled = canEdit && !p.injured, minW = 36.dp) { c.impact = if (c.impact == p.id) "" else p.id; edit() }
                        IconBtn(Icons.Filled.Add, "to XI", Ok, enabled = canEdit && !p.injured && c.xi.size < 11) {
                            c.xi.add(p.id); if (c.impact == p.id) c.impact = ""
                            if (p.bowl >= 50 && c.bowlers.size < 8 && p.id !in c.bowlers) c.bowlers.add(p.id)
                            edit()
                        }
                    }
                }
                if (bench.isEmpty()) item { Sub("No other registered players available.", Modifier.padding(8.dp)) }
                item { Spacer(Modifier.height(10.dp)) }
            }
        }
    }
}

/* ---------------------------------------------------------------------------------------------------- batting order */

@Composable
private fun BattingOrderTab(vm: GameVM, s: CareerSave, comp: Competition, c: Club, started: Boolean, edit: () -> Unit) {
    val canEdit = !started
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(34.dp)) {
            Text("BATTING ORDER", color = C.Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Text("Use the arrows to move a batter up or down. ", color = C.Sub, fontSize = 9.5.sp)
            Pill("Auto order", false, enabled = canEdit) { c.xi = TeamSelector.orderBatting(c.xi.mapNotNull { s.players[it] }).map { it.id }.toMutableList(); edit() }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(c.xi.toList(), key = { _, id -> id }) { i, pid ->
                val p = s.players[pid] ?: return@itemsIndexed
                val slot = when (i) { 0, 1 -> "OPENER"; 2, 3 -> "TOP ORDER"; 4, 5 -> "MIDDLE"; 6 -> "FINISHER"; else -> "TAIL" }
                val b = comp.bat[pid]
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp)).background(NavyHi.copy(alpha = 0.85f)).border(1.dp, Line, RoundedCornerShape(10.dp)).padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = C.Gold, fontFamily = Display, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                    PlayerFace(p.id, p.name, 28.dp, roleColor(p.role)); Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$slot · ${roleShort(p.role)} · TECH ${p.tech} · PWR ${p.power}", color = C.Sub, fontSize = 8.5.sp)
                    }
                    Text(if (b != null && b.inn > 0) "${b.runs} r · avg ${b.avg()?.let { "%.1f".format(it) } ?: "-"} · SR ${b.sr()?.let { "%.0f".format(it) } ?: "-"}" else "${p.careerRuns} career runs",
                        color = C.Text, fontSize = 9.5.sp, modifier = Modifier.width(150.dp), textAlign = TextAlign.End)
                    FormMark(p.form)
                    IconBtn(Icons.Filled.KeyboardArrowUp, "up", enabled = canEdit && i > 0) { val l = c.xi; val x = l[i - 1]; l[i - 1] = l[i]; l[i] = x; edit() }
                    IconBtn(Icons.Filled.KeyboardArrowDown, "down", enabled = canEdit && i < c.xi.size - 1) { val l = c.xi; val x = l[i + 1]; l[i + 1] = l[i]; l[i] = x; edit() }
                }
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

/* ---------------------------------------------------------------------------------------------------- bowling plan */

@Composable
private fun BowlingPlanTab(vm: GameVM, s: CareerSave, comp: Competition, c: Club, started: Boolean, edit: () -> Unit) {
    val canEdit = !started
    val test = comp.format == "TEST"
    val plan = c.bowlers.filter { it in c.xi }
    val rest = c.xi.filter { it !in c.bowlers }.mapNotNull { s.players[it] }.sortedByDescending { it.bowl }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(34.dp)) {
            Text("BOWLING PLAN  ${plan.size}", color = C.Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(if (test) "  Priority order + role: New ball · Stock (long spells) · Strike (fresh bursts) · Part-time" else "  Priority order + role: New ball (powerplay) · Middle · Death · Part-time",
                color = C.Sub, fontSize = 9.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Pill("Auto roles", false, enabled = canEdit) { autoRoles(s, c, test); edit() }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(plan, key = { _, id -> id }) { i, pid ->
                val p = s.players[pid] ?: return@itemsIndexed
                val w = comp.bowlS[pid]
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp)).background(NavyHi.copy(alpha = 0.85f)).border(1.dp, Line, RoundedCornerShape(10.dp)).padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = C.Gold, fontFamily = Display, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                    PlayerFace(p.id, p.name, 28.dp, roleColor(p.role)); Spacer(Modifier.width(6.dp))
                    Column(Modifier.width(150.dp)) {
                        Text(p.name, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.bowlType} · BOWL ${p.bowl} · " + (if (w != null && w.balls > 0) "${w.wk}w e${"%.1f".format(w.econ() ?: 0.0)}" else "${p.careerWk} career w"), color = C.Sub, fontSize = 8.5.sp, maxLines = 1)
                    }
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        for (r in Registration.BOWL_ROLES) Pill(Registration.roleChip(r, test), (c.bowlRoles[pid] ?: "AUTO") == r, color = if (r == "DEATH") C.Red else if (r == "NEW") C.Blue else C.Gold, enabled = canEdit) {
                            if (r == "AUTO") c.bowlRoles.remove(pid) else c.bowlRoles[pid] = r; edit()
                        }
                    }
                    FitBar(p.fitness, 34.dp)
                    IconBtn(Icons.Filled.KeyboardArrowUp, "up", enabled = canEdit && i > 0) { swapPlan(c, pid, -1); edit() }
                    IconBtn(Icons.Filled.KeyboardArrowDown, "down", enabled = canEdit && i < plan.size - 1) { swapPlan(c, pid, 1); edit() }
                    IconBtn(Icons.Filled.Close, "remove", C.Red, enabled = canEdit) { c.bowlers.remove(pid); c.bowlRoles.remove(pid); edit() }
                }
            }
            item {
                if (rest.isNotEmpty()) {
                    Text("ADD FROM XI", color = C.Sub, fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        rest.take(6).forEach { p -> Pill("+ ${p.short()} ${p.bowl}", false, enabled = canEdit && c.bowlers.size < 8) { c.bowlers.add(p.id); edit() } }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private fun swapPlan(c: Club, pid: String, d: Int) {
    val l = c.bowlers; val i = l.indexOf(pid); if (i < 0) return
    // move past bowlers that are not in the XI so the visible order changes
    var j = i + d
    while (j in l.indices && l[j] !in c.xi) j += d
    if (j !in l.indices) return
    val x = l[j]; l[j] = l[i]; l[i] = x
}

/** Sensible default roles: best two pace = new ball, best death bowler = death, spinners / others = middle, weakest = part-time. */
fun autoRoles(s: CareerSave, c: Club, test: Boolean) {
    val ps = c.bowlers.filter { it in c.xi }.mapNotNull { s.players[it] }
    c.bowlRoles.keys.retainAll(ps.map { it.id }.toSet())
    val pace = ps.filter { it.bowlType == "PACE" }.sortedByDescending { it.bowl }
    val newBall = pace.take(2).map { it.id }.toSet()
    val death = (pace.drop(if (test) 2 else 1).maxByOrNull { it.bowl } ?: pace.firstOrNull())?.id
    val weakest = ps.filter { it.bowl < 50 }.map { it.id }.toSet()
    for (p in ps) c.bowlRoles[p.id] = when {
        p.id in weakest -> "PART"
        p.id == death && p.id !in newBall -> "DEATH"
        p.id in newBall -> "NEW"
        else -> "MID"
    }
}

/* ---------------------------------------------------------------------------------------------------- info + opponent */

@Composable
private fun MatchInfoTab(s: CareerSave, comp: Competition, fx: Fixture, m: MatchSim?, me: Club, opp: Club) {
    Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            InfoTile("VENUE", m?.cfg?.venue ?: Schedule.venue(s, fx))
            InfoTile("PITCH", m?.pitchText() ?: "Report after registration")
            InfoTile("WEATHER", (m?.cfg?.weather ?: "-") + (m?.rainNote?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""))
            InfoTile("FORMAT", if (m?.isTest == true) (if (comp.key == "RANJI") "First-class (4 days)" else "Test (5 days)") else "${m?.cfg?.overs ?: if (comp.format == "ODI") 50 else 20} overs a side${if (comp.allowImpact) " · Impact Player rule" else ""}")
            InfoTile("START", runCatching { Schedule.startTime(s, comp, fx) }.getOrDefault("-") + " local (generated schedule)")
        }
        Column(Modifier.weight(1f)) {
            StadiumGraphic(s.clubs[fx.home]?.capacity ?: 20000, s.clubs[fx.home]?.primary ?: 0xFF1565C0)
            Spacer(Modifier.height(6.dp))
            for (club in listOf(me, opp)) {
                val xi = club.xi.mapNotNull { s.players[it] }
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    ClubBadge(club, 26.dp); Spacer(Modifier.width(6.dp))
                    Text(club.short, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.width(48.dp))
                    Text("STR %.0f · avg fitness %d · avg form %d".format(Career.strength(s, club), if (xi.isEmpty()) 0 else xi.sumOf { it.fitness } / xi.size, if (xi.isEmpty()) 0 else xi.sumOf { it.form } / xi.size),
                        color = C.Sub, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    FormDots(formOf(s, club.id))
                }
            }
        }
    }
}

@Composable
private fun InfoTile(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(Navy.copy(alpha = 0.9f)).border(1.dp, Line, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(label, color = C.Gold, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text(value, color = C.Text, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun OpponentTab(vm: GameVM, s: CareerSave, comp: Competition, opp: Club, m: MatchSim?) {
    val xi = (m?.xiOf(opp.id)?.takeIf { it.isNotEmpty() } ?: opp.xi).mapNotNull { s.players[it] }
    val threats = xi.sortedByDescending { it.overall() + it.form / 5 }.take(3)
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(34.dp)) {
            Text("${opp.name.uppercase()} · PROBABLE XI", color = C.Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Text("Key threats: " + threats.joinToString { it.short() }, color = C.Sub, fontSize = 9.5.sp)
            Pill("Full squad ›", false) { vm.nav(Screen.Squad(opp.id)) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(xi, key = { _, p -> p.id }) { i, p -> SheetRow(s, comp, opp, p, i, { vm.nav(Screen.Profile(p.id)) }) { if (p in threats) { TagChip("THREAT", C.Red); Spacer(Modifier.width(6.dp)) } } }
            if (xi.isEmpty()) item { Sub("The opponent's XI is announced once your squad is registered.", Modifier.padding(8.dp)) }
        }
    }
}

/* ===================================================================================================== Squad Registration */

@Composable
fun SquadRegistrationScreen(vm: GameVM, compId: String) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val comp = vm.compById(compId)
    val rule = comp?.let { Registration.rule(s, it) }
    if (comp == null || rule == null) { ScreenScaffold(vm, "Squad Registration") { Sub("No registration needed for this competition.", Modifier.padding(16.dp)) }; return }
    val c = s.clubs[comp.userTeam!!]!!
    val locked = Registration.locked(s, comp)
    var sel by remember(compId) { mutableStateOf(if (comp.regDone) comp.reg.toSet() else Registration.autoSelect(s, comp).toSet()) }
    var role by remember { mutableStateOf("ALL") }
    var replaceOut by remember { mutableStateOf<String?>(null) }
    val pool = Registration.pool(s, comp).let { l -> if (locked) l.sortedWith(compareByDescending<GPlayer> { it.id in comp.reg }.thenByDescending { it.overall() }) else l.sortedByDescending { it.overall() } }
        .filter { role == "ALL" || it.role == role }
    val chosen = (if (locked) comp.reg.toSet() else sel)
    val problems = if (locked) emptyList() else Registration.problems(s, comp, chosen)
    val replaceable = if (locked) Registration.replaceable(s, comp).toSet() else emptySet()

    ScreenScaffold(vm, "${Registration.short(comp)} · Squad Registration") {
        // ---- rules strip
        Row(Modifier.fillMaxWidth().height(40.dp).background(Brush.horizontalGradient(listOf(Color(c.primary).copy(alpha = 0.35f), Color(0xFF0A1530)))).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ClubBadge(c, 28.dp); Spacer(Modifier.width(8.dp))
            Text(comp.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            val status = when { locked -> "LOCKED" to C.Red; comp.regDone -> "REGISTERED" to Ok; else -> "OPEN" to Warn }
            TagChip(status.first, status.second); Spacer(Modifier.width(6.dp))
            TagChip(rule.sizeText(), C.Gold); Spacer(Modifier.width(4.dp))
            if (rule.overseasMax < 99) { TagChip("≤${rule.overseasMax} overseas", C.Blue); Spacer(Modifier.width(4.dp)) }
            TagChip("≥${rule.minKeepers} WK · ≥${rule.minBowlers} bowlers", C.Sub); Spacer(Modifier.width(4.dp))
            TagChip("Replacements ${comp.regRepl}/${rule.replacements}", if (locked) Warn else C.Sub)
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // ---- player list
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(36.dp)) {
                    for (r in listOf("ALL", "BAT", "WK", "AR", "BOWL")) Pill(r, role == r) { role = r }
                    Spacer(Modifier.weight(1f))
                    Text(if (locked) "Locked after match 1 - replace injured or departed players only" else "Tap a player to add / remove · ${pool.size} shown",
                        color = C.Sub, fontSize = 9.5.sp, maxLines = 1)
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(pool, key = { it.id }) { p ->
                        val on = p.id in chosen
                        val shape = RoundedCornerShape(10.dp)
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(shape).background(if (on) NavyHi.copy(alpha = 0.95f) else Navy.copy(alpha = 0.6f))
                            .border(1.dp, if (on) C.Gold.copy(alpha = 0.55f) else Line, shape)
                            .clickable(enabled = !locked) { GameAudio.click(); sel = if (on) sel - p.id else sel + p.id }
                            .padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!locked) Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(if (on) C.Gold else Color.Transparent).border(1.5.dp, if (on) C.Gold else C.Sub, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                                    if (on) Icon(Icons.Filled.Check, null, tint = Color(0xFF111827), modifier = Modifier.size(15.dp))
                                }
                            }
                            PlayerFace(p.id, p.name, 30.dp, roleColor(p.role)); Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.name, color = if (on || !locked) Color.White else C.Sub, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    if (TeamSelector.isOverseas(p, c)) Text(" OS", color = C.Blue, fontSize = 8.5.sp, fontWeight = FontWeight.Black)
                                    if (p.fictional) Text(" FICTIONAL", color = C.Sub, fontSize = 8.sp)
                                }
                                Text("${roleShort(p.role)} · ${p.overall()} OVR · age ${p.age(s.year)} · ${p.bowlType.lowercase()} · CAREER ${p.careerM}m ${p.careerRuns}r ${p.careerWk}w", color = C.Sub, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (p.injured) { TagChip("INJURED ${p.injuryDays}d", C.Red); Spacer(Modifier.width(4.dp)) }
                            if (locked && !on) { TagChip("NOT REGISTERED", C.Sub); Spacer(Modifier.width(4.dp)) }
                            FormMark(p.form); FitBar(p.fitness, 36.dp)
                            if (locked && p.id in replaceable) Pill("Replace", true, color = Warn, enabled = Registration.replacementsLeft(s, comp) > 0) { replaceOut = p.id }
                        }
                    }
                    // registered players who have left the club (replaceable, not in the pool any more)
                    if (locked) items(comp.reg.filter { it !in c.squad }, key = { "gone_$it" }) { pid ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp)).background(Navy).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(s.players[pid]?.name ?: pid, color = C.Sub, fontSize = 11.5.sp, modifier = Modifier.weight(1f)); TagChip("LEFT CLUB", C.Red)
                            Pill("Replace", true, color = Warn, enabled = Registration.replacementsLeft(s, comp) > 0) { replaceOut = pid }
                        }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                }
            }
            // ---- summary panel
            Column(Modifier.width(250.dp).fillMaxHeight().background(Color(0xE60A1228)).drawBehind { drawLine(Color(0x553D5A99), Offset(0f, 0f), Offset(0f, size.height), 2f) }
                .verticalScroll(rememberScrollState()).padding(10.dp)) {
                val ps = chosen.mapNotNull { s.players[it] }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${ps.size}", color = if (ps.size in rule.min..rule.max) Ok else Warn, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                    Text(" / ${rule.max}", color = C.Sub, fontFamily = Display, fontSize = 18.sp, modifier = Modifier.padding(bottom = 4.dp))
                    Spacer(Modifier.weight(1f))
                    Text("MIN ${rule.min}", color = C.Sub, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                }
                Row(Modifier.fillMaxWidth()) {
                    for (r in listOf("BAT", "WK", "AR", "BOWL")) Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${ps.count { it.role == r }}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Text(r, color = roleColor(r), fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
                Text("Pace ${ps.count { it.bowl >= 50 && it.bowlType == "PACE" }} · Spin ${ps.count { it.bowl >= 50 && it.bowlType == "SPIN" }}" +
                    (if (rule.overseasMax < 99) " · Overseas ${ps.count { TeamSelector.isOverseas(it, c) }}/${rule.overseasMax}" else "") +
                    " · Injured ${ps.count { it.injured }}", color = C.Sub, fontSize = 10.sp, modifier = Modifier.padding(vertical = 4.dp))
                problems.forEach { Text("✕ $it", color = C.Red, fontSize = 10.sp, lineHeight = 13.sp) }
                if (!locked && problems.isEmpty()) Text("✓ Valid squad", color = Ok, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (!locked) {
                    GoldButton(if (comp.regDone) "Update registration" else "Register squad", Modifier.fillMaxWidth(), enabled = problems.isEmpty()) {
                        val err = Registration.register(s, comp, chosen.toList())
                        if (err != null) vm.msg(err) else {
                            TeamSelector.validate(c, s.players, Registration.xiPool(s, comp)); vm.sheetEdited()
                            vm.msg("${Registration.short(comp)} squad registered (${chosen.size}).")
                            if (vm.stack.size > 1 && vm.stack.getOrNull(vm.stack.size - 2) == Screen.PreMatch) vm.back()
                        }
                    }
                    Row {
                        OutlineBtn("Auto-select", Modifier.weight(1f)) { sel = Registration.autoSelect(s, comp).toSet() }
                        Spacer(Modifier.width(6.dp))
                        OutlineBtn("Clear", Modifier.weight(1f)) { sel = emptySet() }
                    }
                    val poolSize = Registration.pool(s, comp).size
                    if (poolSize < rule.min && (s.mode == Modes.INTL || s.mode == Modes.DOMESTIC)) GreenButton("Top up squad to ${rule.min}", Modifier.fillMaxWidth()) {
                        Registration.topUpPool(s, comp, rule.min); sel = Registration.autoSelect(s, comp).toSet(); vm.persist(); vm.tick++
                        vm.msg("Squad topped up to ${Registration.pool(s, comp).size} players")
                    }
                    if (s.mode == Modes.INTL || s.mode == Modes.DOMESTIC) OutlineBtn(if (s.mode == Modes.INTL) "Call up more players" else "Sign more players", Modifier.fillMaxWidth()) { vm.nav(Screen.Selection) }
                    Text("You can change the registration until your first ${Registration.short(comp)} match is played. Then it locks.", color = C.Sub, fontSize = 9.5.sp, lineHeight = 12.sp, modifier = Modifier.padding(top = 4.dp))
                } else {
                    Text("REPLACEMENTS ${comp.regRepl}/${rule.replacements}", color = C.Gold, fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    if (comp.regLog.isEmpty()) Text("None yet. A registered player who is injured, retires or leaves the club can be replaced by a fit club player.", color = C.Sub, fontSize = 9.5.sp, lineHeight = 12.sp)
                    comp.regLog.forEach { Text("• $it", color = C.Text, fontSize = 9.5.sp, lineHeight = 12.sp) }
                }
                if (comp.regAuto) Text("This squad was registered automatically for a simulated match.", color = Warn, fontSize = 9.5.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    replaceOut?.let { outPid ->
        val cands = Registration.replacementPool(s, comp)
        AlertDialog(onDismissRequest = { replaceOut = null }, containerColor = C.Surface,
            title = { Text("Replace ${s.players[outPid]?.name ?: ""}", fontSize = 16.sp) },
            text = {
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(cands, key = { it.id }) { p ->
                        Row(Modifier.fillMaxWidth().clickable {
                            GameAudio.click()
                            val err = Registration.replace(s, comp, outPid, p.id)
                            vm.msg(err ?: "${p.name} registered as replacement"); vm.sheetEdited(); replaceOut = null
                        }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            PlayerFace(p.id, p.name, 28.dp, roleColor(p.role)); Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("${roleShort(p.role)} · ${p.overall()} OVR · fit ${p.fitness}${if (TeamSelector.isOverseas(p, c)) " · OS" else ""}", color = C.Sub, fontSize = 10.sp)
                            }
                            Text("SELECT", color = C.Gold, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    if (cands.isEmpty()) item { Sub("No fit, unregistered club players. Sign or call up a player first.") }
                }
            },
            confirmButton = { TextButton(onClick = { replaceOut = null }) { Text("Cancel") } })
    }
}
