package com.cricketcoach.manager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.data.FmtStats
import com.cricketcoach.manager.data.OffStats
import com.cricketcoach.manager.data.RealPlayer
import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*

private fun n(d: Double?): String = d?.let { if (it == Math.floor(it)) it.toLong().toString() else "%.2f".format(it) } ?: "-"

/** In-game rating rank among all real players of the same role (an in-game ranking, not ICC). */
private fun ratingRank(pid: String, role: String, ovr: Int, s: CareerSave?): Pair<Int, Int> {
    return if (s != null) {
        val same = s.players.values.filter { it.role == role && !it.retired && !it.academy }
        same.count { it.overall() > ovr } + 1 to same.size
    } else {
        val same = Repo.ds.players.filter { it.role == role }
        same.count { (Repo.attrs[it.id]?.overall ?: 0) > ovr } + 1 to same.size
    }
}

@Composable
fun ProfileScreen(vm: GameVM, pid: String) {
    val s = vm.save
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val rp: RealPlayer? = Repo.byId[pid]
    val gp: GPlayer? = s?.players?.get(pid)
    val a = Repo.attrs[pid]
    var tab by remember { mutableIntStateOf(0) }
    var bidDialog by remember { mutableStateOf(false) }
    var swapDialog by remember { mutableStateOf(false) }
    val name = gp?.name ?: rp?.name ?: pid
    val ovr = gp?.overall() ?: a?.overall ?: 50
    val role = gp?.role ?: rp?.role ?: "BAT"
    ScreenScaffold(vm, name) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            // ----- player card -----
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFF1B5E20), Color(0xFF0D3B26), Color(0xFF3E2F00)))).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$ovr", color = C.Gold, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                            Text(role, color = C.Text, fontWeight = FontWeight.Bold)
                            Box(Modifier.padding(top = 6.dp)) { PlayerFace(pid, name, 72.dp) }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            rp?.full?.let { if (it != name) Sub("Full name: $it") }
                            rp?.img?.let { Sub("Photo: Wikimedia Commons (free licence) • $it", size = 9) }
                            Sub("${gp?.country ?: rp?.country} • ${Consts.ROLE_NAMES[role]}")
                            val age = when { gp != null && s != null -> "${gp.age(s.year)}${if (gp.ageEst) " (est.)" else ""}"; rp?.dob != null -> "born ${rp.dob}"; else -> "n/a" }
                            Sub("Age: $age${if (rp?.dob != null && s != null) " • DOB ${rp.dob}" else ""}")
                            if (rp != null) { Sub("Bat: ${rp.bat.ifEmpty { "n/a" }}"); Sub("Bowl: ${rp.bowl.ifEmpty { "n/a" }}") }
                            else if (gp != null) Sub("Bowling type: ${gp.bowlType}")
                            val (rk, tot) = ratingRank(pid, role, ovr, s)
                            Sub("In-game ${Consts.ROLE_NAMES[role]} rank: #$rk of $tot")
                        }
                    }
                }
            }
            // ----- teams -----
            CcmCard {
                Text("Teams", color = C.Gold, fontWeight = FontWeight.Bold)
                if (gp != null && s != null) {
                    KV("Career-mode club", s.clubs[gp.clubId]?.name ?: if (gp.academy) "Academy" else "Free agent")
                    if (gp.clubId.isNotEmpty()) KV("Contract", "${Money.fmt(gp.salary)}/season • ${gp.contractYears} yr")
                    KV("Market value", Money.fmt(Money.value(gp.overall(), gp.age(s.year), s.economy)))
                }
                val nat = Consts.nationByCountry(gp?.country ?: rp?.country ?: "")
                KV("National team", nat?.name ?: "-")
                if (rp != null) {
                    val intlTeams = rp.stats.filterKeys { it != "IPL" }.values.mapNotNull { it.team }.distinct()
                    if (intlTeams.isNotEmpty()) KV("International side(s) in data", intlTeams.joinToString())
                    KV("IPL franchise (real)", rp.ipl?.let { "$it${rp.iplLast?.let { d -> " • last match $d" } ?: ""}" } ?: "-")
                    if (rp.cInf) Sub("Uncapped domestic player: country inferred as India from IPL registration.")
                }
                if (gp?.fictional == true) Sub("FICTIONAL player generated by the game (youth intake / scouting).")
            }
            // ----- attributes -----
            CcmCard {
                Text("Attributes", color = C.Gold, fontWeight = FontWeight.Bold)
                val tech = gp?.tech ?: a?.tech ?: 0; val pow = gp?.power ?: a?.power ?: 0; val bowl = gp?.bowl ?: a?.bowl ?: 0
                StatBar("Batting technique", tech); StatBar("Power hitting", pow); StatBar("Bowling", bowl)
                StatBar("Fielding", gp?.field ?: a?.field ?: 0); if (role == "WK") StatBar("Wicket-keeping", gp?.keep ?: a?.keep ?: 0)
                if (gp != null) {
                    StatBar("Fitness", gp.fitness, 100); StatBar("Morale", gp.morale, 100); StatBar("Form", gp.form, 100)
                    val own = s != null && gp.clubId == s.userClubId || gp.academy
                    KV("Potential", if (gp.scouted || own) "${gp.potential}" else "Unknown — scout him")
                    if (gp.injured) KV("Injury", "${gp.injury} (${gp.injuryDays} days)", C.Red)
                }
                Sub("Ratings are derived from the real career statistics below (game abstraction).")
            }
            // ----- actions -----
            if (s != null && gp != null) ActionsCard(vm, s, gp, { bidDialog = true }, { swapDialog = true })
            // ----- real statistics -----
            if (rp != null) {
                SectionTitle("Real international & IPL statistics")
                val tabs = listOf("TEST", "ODI", "T20I", "IPL")
                Tabs(tabs, tab) { tab = it }
                val f = tabs[tab]
                val off = rp.off?.get(f)
                if (off != null && f != "IPL") OfficialCard(off, rp.offSrc, rp.offDate)
                val st = rp.stats[f]
                if (st != null && st.m > 0) DetailedCard(f, st, off != null)
                if (off == null && (st == null || st.m == 0)) CcmCard { Sub(if (f == "IPL") "No IPL matches in the Cricsheet IPL dataset." else "No $f appearances recorded in the verified sources.") }
                // totals
                CcmCard {
                    Text("International career totals", color = C.Gold, fontWeight = FontWeight.Bold)
                    var m = 0.0; var r = 0.0; var w = 0.0; var src = "official"
                    for (fm in listOf("TEST", "ODI", "T20I")) {
                        val o = rp.off?.get(fm); val c = rp.stats[fm]
                        if (o?.m != null) { m += o.m; r += o.runs ?: 0.0; w += o.wk ?: 0.0 }
                        else if (c != null) { m += c.m; r += c.runs; w += c.wk; if (c.m > 0) src = "mixed (official + Cricsheet)" }
                    }
                    KV("Matches", n(m)); KV("Runs", n(r)); KV("Wickets", n(w)); Sub("Source: $src")
                }
                if (rp.ms.isNotEmpty()) CcmCard { Text("Milestones", color = C.Gold, fontWeight = FontWeight.Bold); rp.ms.forEach { Text("• $it", color = C.Text, fontSize = 12.sp) } }
                if (rp.form.isNotEmpty() || rp.bform.isNotEmpty()) CcmCard {
                    Text("Recent real-world form (Cricsheet)", color = C.Gold, fontWeight = FontWeight.Bold)
                    rp.form.forEach { Text("${it.d} ${it.f} v ${it.o}: ${it.r}${if (it.no) "*" else ""} (${it.b})", color = C.Text, fontSize = 12.sp) }
                    if (rp.bform.isNotEmpty()) Spacer(Modifier.height(4.dp))
                    rp.bform.forEach { Text("${it.d} ${it.f} v ${it.o}: ${it.w}/${it.r}", color = C.Sub, fontSize = 12.sp) }
                }
            }
            // ----- in-game career -----
            if (gp != null && s != null) {
                CcmCard {
                    Text("Career-mode statistics", color = C.Gold, fontWeight = FontWeight.Bold)
                    for (fm in listOf("T20", "ODI", "TEST")) {
                        val bk = s.records["ALL|$fm"] ?: continue
                        val b = bk.bat[pid]; val w = bk.bowl[pid]
                        if (b == null && w == null) continue
                        Text(fm, color = C.Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (b != null) Sub("Bat: ${b.inn} inn, ${b.runs} runs, HS ${b.hs}${if (b.hsNo) "*" else ""}, avg ${f2(b.avg())}, SR ${f1(b.sr())}, 50s ${b.f50}, 100s ${b.f100}, 4s ${b.fours}, 6s ${b.sixes}")
                        if (w != null) Sub("Bowl: ${w.wk} wkts, avg ${f2(w.avg())}, econ ${f2(w.econ())}, best ${if (w.bbW > 0) "${w.bbW}/${w.bbR}" else "-"}, 5w ${w.w5} • catches ${w.ct}")
                    }
                    KV("Total career-mode runs / wickets", "${gp.careerRuns} / ${gp.careerWk}")
                    if (gp.history.isNotEmpty()) { SectionTitle("History"); gp.history.takeLast(15).reversed().forEach { Text("• $it", color = C.Sub, fontSize = 12.sp) } }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
    if (bidDialog && s != null && gp != null) BidDialog(vm, s, gp) { bidDialog = false }
    if (swapDialog && s != null && gp != null) SwapDialog(vm, s, gp) { swapDialog = false }
}

@Composable
private fun OfficialCard(o: OffStats, src: String?, date: String?) {
    CcmCard(highlight = true) {
        Text("Official career totals", color = C.Gold, fontWeight = FontWeight.Bold)
        Row {
            Column(Modifier.weight(1f)) {
                Text("Batting", color = C.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                KV("Matches", n(o.m)); KV("Runs", n(o.runs)); KV("Average", n(o.avg)); KV("High score", o.hs ?: "-"); KV("100s / 50s", "${n(o.h100)} / ${n(o.h50)}")
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Bowling & fielding", color = C.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                KV("Wickets", n(o.wk)); KV("Average", n(o.bavg)); KV("BBI", o.bb ?: "-"); KV("5w / 10w", "${n(o.w5)} / ${n(o.w10)}"); KV("Catches", n(o.ct) + (o.st?.let { " / ${n(it)} st" } ?: ""))
            }
        }
        Sub("Source: ${src ?: "Wikipedia infobox (ESPNcricinfo)"} • as of ${date ?: "n/a"}")
    }
}

@Composable
private fun DetailedCard(f: String, s: FmtStats, hasOfficial: Boolean) {
    CcmCard {
        Text(if (hasOfficial) "Detailed / partial (Cricsheet ball-by-ball)" else "Statistics (Cricsheet ball-by-ball)", color = C.Gold, fontWeight = FontWeight.Bold)
        Row {
            Column(Modifier.weight(1f)) {
                Text("Batting", color = C.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                KV("M / Inn", "${s.m} / ${s.inn}"); KV("Runs", "${s.runs}"); KV("HS", s.hsText); KV("Avg", f2(s.avg)); KV("SR", f1(s.sr))
                KV("50s/100s/200s", "${s.f50}/${s.f100}/${s.f200}"); KV("4s / 6s", "${s.fours} / ${s.sixes}")
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Bowling", color = C.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                KV("Wkts", "${s.wk}"); KV("Avg", f2(s.bowlAvg)); KV("Econ", f2(s.econ)); KV("SR", f1(s.bowlSr)); KV("BBI", s.bbText)
                KV("5w / 10w", "${s.w5} / ${s.w10}"); KV("Ct / St", "${s.ct} / ${s.st}")
            }
        }
        Sub("${s.first ?: "?"} → ${s.last ?: "?"}${s.team?.let { " • $it" } ?: ""} • Cricsheet coverage: ${Repo.ds.meta.coverage[f]?.let { "${it.first} → ${it.last}" } ?: "-"}")
        if (hasOfficial) Sub("Cricsheet does not cover every match (esp. pre-2002 and Afghanistan games) — official totals above are authoritative.")
    }
}

@Composable
private fun ActionsCard(vm: GameVM, s: CareerSave, p: GPlayer, onBid: () -> Unit, onSwap: () -> Unit) {
    val mine = p.clubId == s.userClubId
    CcmCard {
        Text("Actions", color = C.Gold, fontWeight = FontWeight.Bold)
        when {
            p.retired -> Sub("Retired.")
            p.academy && s.userClub().academyList.contains(p.id) -> Row {
                GreenButton("Promote") { vm.act { Career.promoteYouth(it, p.id) } }; Spacer(Modifier.width(8.dp)); OutlineBtn("Release") { vm.act { Career.releaseYouth(it, p.id) } }
            }
            mine -> {
                Row {
                    GreenButton("Extra training", Modifier.weight(1f)) { vm.act { Career.extraTraining(it, p.id) } }
                    Spacer(Modifier.width(8.dp))
                    OutlineBtn(if (p.forSale) "Remove from list" else "List for transfer", Modifier.weight(1f)) { p.forSale = !p.forSale; vm.persist(); vm.msg(if (p.forSale) "${p.name} listed — AI clubs may make offers" else "Removed from transfer list") }
                }
                Spacer(Modifier.height(6.dp))
                var confirm by remember { mutableStateOf(false) }
                OutlineBtn("Release (pays 50% of salary)", Modifier.fillMaxWidth()) { confirm = true }
                if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = C.Surface, title = { Text("Release ${p.name}?") },
                    text = { Text("You pay ${Money.fmt(p.salary * 0.5)} compensation and he becomes a free agent.") },
                    confirmButton = { TextButton(onClick = { confirm = false; vm.act { Career.release(it, p.id) }; vm.back() }) { Text("Release", color = C.Red) } },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
            }
            p.clubId.isEmpty() -> Row {
                GreenButton("Sign free agent (${Money.fmt(maxOf(0.2 * s.economy, Money.value(p.overall(), p.age(s.year), s.economy)))}/yr)", Modifier.weight(1f)) { vm.act { Career.signFreeAgent(it, p.id) } }
            }
            else -> Row {
                GreenButton("Make transfer bid", Modifier.weight(1f)) { onBid() }
                Spacer(Modifier.width(8.dp))
                OutlineBtn("Propose swap", Modifier.weight(1f)) { onSwap() }
            }
        }
        if (!p.scouted && !mine && !p.retired) { Spacer(Modifier.height(6.dp)); OutlineBtn("Scout potential (${Money.fmt(0.1 * s.economy)})", Modifier.fillMaxWidth()) { vm.act { Career.scoutPlayer(it, p.id) } } }
    }
}

@Composable
private fun BidDialog(vm: GameVM, s: CareerSave, p: GPlayer, onClose: () -> Unit) {
    val ask = Career.askingPrice(s, p).coerceAtLeast(0.2)
    var fee by remember { mutableFloatStateOf(ask.toFloat()) }
    AlertDialog(onDismissRequest = onClose, containerColor = C.Surface, title = { Text("Bid for ${p.name}") },
        text = {
            Column {
                Sub("Club: ${s.clubs[p.clubId]?.name} • valuation ≈ ${Money.fmt(ask)} • his salary ${Money.fmt(p.salary)}")
                Sub("Your balance ${Money.fmt(s.userClub().balance)} • cap room ${Money.fmt(Career.purse(s, s.userClub()))}")
                Spacer(Modifier.height(8.dp))
                Text("Offer: ${Money.fmt(fee.toDouble())}", color = C.Gold, fontWeight = FontWeight.Bold)
                Slider(fee, { fee = (Math.round(it * 20) / 20f) }, valueRange = (ask * 0.4).toFloat()..(ask * 2).toFloat())
            }
        },
        confirmButton = { TextButton(onClick = { vm.act { Career.bidForPlayer(it, p.id, fee.toDouble()) }; onClose() }) { Text("Submit bid", color = C.Gold) } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
private fun SwapDialog(vm: GameVM, s: CareerSave, p: GPlayer, onClose: () -> Unit) {
    val mine = s.userClub().squad.mapNotNull { s.players[it] }.sortedByDescending { it.overall() }
    AlertDialog(onDismissRequest = onClose, containerColor = C.Surface, title = { Text("Swap for ${p.name} (${p.overall()})") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Sub("Choose one of your players to offer in exchange. AI clubs accept if the value is fair and it fits their needs.")
                for (m in mine) TextButton(onClick = { vm.act { Career.proposeSwap(it, m.id, p.id) }; onClose() }) {
                    Text("${m.name} • ${m.role} • ${m.overall()}", color = C.Text, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}
