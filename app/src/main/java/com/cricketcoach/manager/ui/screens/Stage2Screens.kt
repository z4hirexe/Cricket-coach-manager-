package com.cricketcoach.manager.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*
import java.time.LocalDate

/* ===============================================================================================
 * Stage 2: Squad Hub (selected / training / health), series selection popup, standalone tournaments.
 * =============================================================================================== */

private fun teamLabel(id: String, s: CareerSave?): String =
    (Consts.NATIONS + Consts.IPL).firstOrNull { it.id == id }?.name ?: s?.clubs?.get(id)?.name ?: id

/** Main-menu Tournament entry: run a cup without touching the three career slots. */
@Composable
fun TournamentHubScreen(vm: GameVM) {
    val presets = remember { Tournaments.TYPES.filter { it.id in listOf("T20_WC", "ODI_WC", "CT", "ASIA", "WTC", "IPL") } }
    var type by remember { mutableStateOf<String?>(null) }
    var team by remember { mutableStateOf<String?>(null) }
    ScreenScaffold(vm, "Tournament") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Sub("Standalone cups use real national squads and IPL rosters. They do not use a career slot and are not saved after you leave.")
            if (type == null) {
                for (tt in presets) CcmCard(onClick = { GameAudio.click(); type = tt.id; team = null }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = C.Gold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tt.name, color = C.Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Sub(tt.desc)
                        }
                    }
                }
            } else {
                val tt = presets.first { it.id == type }
                Text(tt.name, color = C.Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Sub(tt.desc)
                TextButton({ type = null; team = null }) { Text("← choose another cup", color = C.Sub) }
                SectionTitle("Team you will manage")
                val pool = Tournaments.teamsFor(type!!)
                for (row in pool.chunked(3)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (id in row) FilterChip(selected = team == id, onClick = { GameAudio.click(); team = id },
                        label = { Text(teamLabel(id, vm.save), fontSize = 12.sp, maxLines = 1) }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                GoldButton("START ${tt.name.uppercase()}", Modifier.fillMaxWidth(), enabled = team != null && !vm.busy) {
                    vm.startStandaloneTournament(type!!, team!!)
                }
            }
        }
    }
}

/** Squad Hub: selected match squad, training group and player health/fitness for the active team. */
@Composable
fun SquadHubScreen(vm: GameVM) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val c = s.userClub()
    val comp = remember(s.day, s.phase, c.id, vm.tick) {
        s.league.filter { it.userTeam == c.id && it.stage != "DONE" }
            .minByOrNull { lc -> lc.fixtures.filter { !it.played }.minOfOrNull { it.day } ?: 9999 }
            ?: s.league.firstOrNull { it.userTeam == c.id }
    }
    val pool = remember(comp?.id, c.squad.size, vm.tick) { comp?.let { Registration.pool(s, it) } ?: c.squad.mapNotNull { s.players[it] } }
    val selectedIds = remember(comp?.id, comp?.regDone, comp?.reg?.size, vm.tick) {
        when {
            comp != null && comp.regDone -> comp.reg.toSet()
            comp != null -> Registration.autoSelect(s, comp).toSet()
            else -> c.squad.take(16).toSet()
        }
    }
    val selected = pool.filter { it.id in selectedIds }.sortedByDescending { it.overall() }
    val training = pool.filter { it.id !in selectedIds }.sortedByDescending { it.overall() }
    var tab by remember { mutableIntStateOf(0) }
    ScreenScaffold(vm, "Squad Hub · ${c.short}") {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ClubBadge(c, 42.dp); Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, color = C.Gold, fontWeight = FontWeight.Bold, maxLines = 1)
                    Sub(comp?.let { "${Registration.short(it)} · ${it.format}" } ?: "No active competition")
                    if (comp?.selectionDate?.isNotEmpty() == true) Sub("Selection: ${runCatching { Schedule.fmt(LocalDate.parse(comp.selectionDate), vm.settings.dateStyle) }.getOrDefault(comp.selectionDate)}")
                }
                if (comp != null) {
                    if (!Registration.locked(s, comp)) OutlineBtn(if (comp.regDone) "Change selection" else "Select squad") { vm.nav(Screen.SeriesSelection(comp.id)) }
                    else OutlineBtn("Replacements") { vm.nav(Screen.SquadRegistration(comp.id)) }
                }
            }
            Tabs(listOf("Selected (${selected.size})", "Training (${training.size})", "Health"), tab) { tab = it }
            val list = when (tab) { 0 -> selected; 1 -> training; else -> (selected + training).sortedWith(compareByDescending<GPlayer> { it.injured }.thenBy { it.fitness }) }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                items(list, key = { it.id }) { p ->
                    PlayerRow(p, s.year, onClick = { vm.nav(Screen.Profile(p.id)) },
                        extra = "Fitness ${p.fitness} · Form ${p.form}${if (p.injured) " · ${p.injury} (${p.injuryDays}d)" else ""}${if (p.id in c.xi) " · XI" else ""}",
                        highlight = tab == 0 && p.id in c.xi,
                        trailing = {
                            Column(horizontalAlignment = Alignment.End) {
                                RatingPill(p.overall())
                                Spacer(Modifier.height(3.dp))
                                Box(Modifier.width(54.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF1E2B47))) {
                                    Box(Modifier.fillMaxWidth(p.fitness / 100f).fillMaxHeight().background(if (p.fitness >= 80) C.Green else if (p.fitness >= 55) C.Gold else C.Red))
                                }
                            }
                        })
                }
                if (list.isEmpty()) item { Sub("No players in this group.", Modifier.padding(12.dp)) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

/** Two-pane picker: the full player pool stays visible on the left, chosen squad on the right. */
@Composable
fun SeriesSelectionScreen(vm: GameVM, compId: String) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val tick = vm.tick
    val comp = vm.compById(compId)
    val rule = comp?.let { Registration.rule(s, it) }
    if (comp == null || rule == null) { ScreenScaffold(vm, "Series Selection") { Sub("No selection needed.", Modifier.padding(16.dp)) }; return }
    val club = s.clubs[comp.userTeam!!] ?: return
    val locked = Registration.locked(s, comp)
    var selected by remember(compId) { mutableStateOf(if (comp.regDone) comp.reg.toSet() else Registration.autoSelect(s, comp).toSet()) }
    var query by remember(compId) { mutableStateOf("") }
    val pool = remember(compId, vm.tick) { Registration.pool(s, comp).sortedByDescending { it.overall() } }
    val list = pool.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    val chosen = pool.filter { it.id in selected }
    val problems = if (locked) emptyList() else Registration.problems(s, comp, selected)
    val first = comp.fixtures.filter { it.home == club.id || it.away == club.id }.minByOrNull { it.day }
    fun toggle(id: String) { if (!locked) { GameAudio.click(); selected = if (id in selected) selected - id else if (selected.size < rule.max) selected + id else selected } }
    ScreenScaffold(vm, "Series Selection · ${Registration.short(comp)}") {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
            val wide = maxWidth >= 560.dp
            @Composable fun PoolPanel(modifier: Modifier) {
                Column(modifier) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("PLAYER POOL · ${pool.size}", color = C.Gold, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            Text("Tap a player to add or remove", color = C.Sub, fontSize = 11.sp)
                        }
                        if (!locked) TextButton(onClick = { selected = Registration.autoSelect(s, comp).toSet() }) { Text("AUTO", color = C.Gold, fontWeight = FontWeight.Bold) }
                    }
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().heightIn(min = 48.dp), singleLine = true,
                        placeholder = { Text("Search player name", fontSize = 13.sp) }, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        shape = RoundedCornerShape(10.dp))
                    Spacer(Modifier.height(5.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(list, key = { it.id }) { p ->
                            val on = p.id in selected
                            val bg by animateColorAsState(if (on) DS.Navy4 else C.Card, label = "player selected")
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg)
                                .border(1.dp, if (on) C.Gold else DS.Line, RoundedCornerShape(10.dp))
                                .clickable(enabled = !locked) { toggle(p.id) }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                PlayerFace(p.id, p.name, 38.dp); Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2)
                                    Text("${Consts.ROLE_NAMES[p.role] ?: p.role}  ·  Fitness ${p.fitness}%${if (p.injured) " · Injured" else ""}", color = if (p.injured) C.Red else C.Sub, fontSize = 11.sp, maxLines = 1)
                                }
                                RatingPill(p.overall()); Spacer(Modifier.width(7.dp))
                                Box(Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(if (on) C.Gold else DS.Navy3).border(1.dp, if (on) C.Gold else C.Sub, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                                    Text(if (on) "✓" else "+", color = if (on) Color.Black else C.Text, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        if (list.isEmpty()) item { Sub("No players found.", Modifier.padding(12.dp)) }
                    }
                }
            }
            @Composable fun SelectionPanel(modifier: Modifier) {
                Column(modifier.clip(RoundedCornerShape(12.dp)).background(DS.Navy3).border(1.dp, DS.Line, RoundedCornerShape(12.dp)).padding(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("YOUR SQUAD", color = C.Gold, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            Text("${selected.size} / ${rule.max} selected · minimum ${rule.min}", color = C.Text, fontSize = 11.sp)
                        }
                        if (!locked) TextButton(onClick = { selected = emptySet() }) { Text("CLEAR", color = C.Sub, fontSize = 11.sp) }
                    }
                    Text("Selection: ${comp.selectionDate}  ·  Match: ${first?.let { Schedule.fixtureDate(s, comp, it) } ?: "—"}", color = C.Sub, fontSize = 10.sp, maxLines = 1)
                    Spacer(Modifier.height(5.dp))
                    if (wide) LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        items(chosen, key = { it.id }) { p ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(C.Card).clickable(enabled = !locked) { toggle(p.id) }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name, Modifier.weight(1f), color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                Text(if (locked) "${p.overall()}" else "−", color = C.Gold, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            }
                        }
                    } else Spacer(Modifier.height(4.dp))
                    problems.firstOrNull()?.let { Text(it, color = C.Red, fontSize = 11.sp, maxLines = 2) }
                    Spacer(Modifier.height(4.dp))
                    if (locked) GoldButton("REPLACEMENTS", Modifier.fillMaxWidth()) { vm.replaceTop(Screen.SquadRegistration(comp.id)) }
                    else GoldButton("CONFIRM SQUAD (${selected.size})", Modifier.fillMaxWidth(), enabled = problems.isEmpty() && selected.size in rule.min..rule.max) {
                        val err = Registration.register(s, comp, selected.toList())
                        if (err != null) vm.msg(err) else {
                            TeamSelector.validate(club, s.players, Registration.xiPool(s, comp))
                            vm.sheetEdited(); vm.persist(); vm.msg("${Registration.short(comp)} squad registered (${selected.size})."); vm.back()
                        }
                    }
                }
            }
            if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PoolPanel(Modifier.weight(1.2f).fillMaxHeight())
                SelectionPanel(Modifier.weight(0.8f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize()) {
                SelectionPanel(Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                PoolPanel(Modifier.weight(1f))
            }
        }
    }
}
