package com.cricketcoach.manager.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.game.Auction
import com.cricketcoach.manager.ui.*
import kotlinx.coroutines.delay

@Composable
fun AuctionScreen(vm: GameVM, preview: Int = 0) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val a = s.auction
    val u = s.userClub()
    DisposableEffect(Unit) {
        com.cricketcoach.manager.audio.GameAudio.setScene(com.cricketcoach.manager.audio.GameAudio.Scene.AUCTION)
        onDispose { com.cricketcoach.manager.audio.GameAudio.setScene(com.cricketcoach.manager.audio.GameAudio.Scene.MENU) }
    }
    if (a == null) {
        ScreenScaffold(vm, "Auction") {
            Column(Modifier.padding(16.dp)) {
                Sub(if (s.auctionDoneThisSeason) "This season's auction is complete." else "Auctions take place in pre-season. A mega auction (with retentions) is held every 3 seasons; a mini auction is optional in other seasons.")
                Spacer(Modifier.height(8.dp))
                if (s.phase == "PRESEASON" && !s.auctionDoneThisSeason) GoldButton("Start mini auction") { vm.startMiniAuction() }
            }
        }
        return
    }
    // live bidding is a full-screen broadcast with its own compact header
    if (a.phase == "LIVE") { AuctionStage(vm, s, a, preview); return }
    AuctionBoard(vm, s, a)
}

/* ======================= Transfers ======================= */

@Composable
fun TransfersScreen(vm: GameVM) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    var tab by remember { mutableIntStateOf(0) }
    var role by remember { mutableStateOf("All") }
    val u = s.userClub()
    ScreenScaffold(vm, "Transfers") {
        Row(Modifier.fillMaxWidth().background(C.Surface).padding(10.dp)) {
            Column(Modifier.weight(1f)) { Sub("Balance"); Text(Money.fmt(u.balance), color = C.Gold, fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f)) { Sub("Cap room"); Text(Money.fmt(Career.purse(s, u)), color = C.Text, fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f)) { Sub("Squad"); Text("${u.squad.size}/25", color = C.Text, fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f)) { Sub("Overseas"); Text("${Career.overseasCount(s, u)}/8", color = C.Text, fontWeight = FontWeight.Bold) }
        }
        Tabs(listOf("Free agents", "Transfer list", "All players", "Offers (${s.offers.size})", "Scouting"), tab) { tab = it }
        if (tab < 3) FlowRowSimple(listOf("All", "BAT", "BOWL", "AR", "WK"), role, Consts.ROLE_NAMES) { role = it }
        when (tab) {
            0, 1, 2 -> {
                val list = s.players.values.filter { p ->
                    !p.retired && !p.academy && p.clubId != u.id && (role == "All" || p.role == role) && when (tab) {
                        0 -> p.clubId.isEmpty(); 1 -> p.forSale && p.clubId.isNotEmpty(); else -> p.clubId.isNotEmpty() && s.clubs[p.clubId]?.kind == "CLUB"
                    }
                }.sortedByDescending { it.overall() }.take(150)
                LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                    items(list, key = { it.id }) { p ->
                        PlayerRow(p, s.year, onClick = { vm.nav(Screen.Profile(p.id)) },
                            extra = if (p.clubId.isEmpty()) "Wants ${Money.fmt(maxOf(0.2 * s.economy, Money.value(p.overall(), p.age(s.year), s.economy)))}/yr" else "${s.clubs[p.clubId]?.short} • value ${Money.fmt(Career.askingPrice(s, p))}",
                            trailing = {
                                Column(horizontalAlignment = Alignment.End) {
                                    RatingPill(p.overall())
                                    if (p.clubId.isEmpty()) TextButton(onClick = { vm.act { Career.signFreeAgent(it, p.id) } }, contentPadding = PaddingValues(4.dp)) { Text("Sign", color = C.Green) }
                                }
                            })
                    }
                    item { if (list.isEmpty()) Sub("No players match."); Spacer(Modifier.height(24.dp)) }
                }
            }
            3 -> LazyColumn(Modifier.padding(12.dp)) {
                item { Sub("AI clubs make offers for players you list for transfer (open a profile → List for transfer). Offers expire at season end.") }
                items(s.offers.toList(), key = { it.id }) { o ->
                    val p = s.players[o.pid]
                    CcmCard {
                        Text("${s.clubs[o.fromClub]?.name} offer ${Money.fmt(o.fee)} for ${p?.name}", color = C.Text, fontWeight = FontWeight.Bold)
                        Row {
                            GreenButton("Accept") { vm.act { Career.acceptOffer(it, o.id) } }
                            Spacer(Modifier.width(8.dp))
                            OutlineBtn("Reject") { s.offers.remove(o); vm.persist() }
                        }
                    }
                }
                item { if (s.offers.isEmpty()) Sub("No offers right now.") }
            }
            else -> Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                Text("Scouting missions", color = C.Gold, fontWeight = FontWeight.Bold)
                Sub("Send scouts (level ${u.scoutLvl}) to discover 3 young FICTIONAL prospects in a country for ${Money.fmt(1.0 * s.economy)}. They become free agents you can sign. Higher scouting level finds better talent.")
                for (row in Consts.CLUBS.keys.toList().chunked(2)) Row {
                    for (c in row) OutlineBtn(c, Modifier.weight(1f).padding(2.dp)) { vm.act { Career.scoutingMission(it, c) } }
                }
                SectionTitle("Scouted prospects (free agents)")
                s.players.values.filter { it.fictional && it.scouted && it.clubId.isEmpty() && !it.retired }.sortedByDescending { it.potential }.take(30).forEach { p ->
                    PlayerRow(p, s.year, onClick = { vm.nav(Screen.Profile(p.id)) }, extra = "Potential ${p.potential}")
                }
            }
        }
    }
}

/* ======================= My club & finances ======================= */

@Composable
fun MyClubScreen(vm: GameVM) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val c = s.userClub()
    var tab by remember { mutableIntStateOf(0) }
    ScreenScaffold(vm, c.name) {
        Tabs(listOf("Overview", "Facilities", "Staff", "Sponsors & tickets", "Trophies"), tab) { tab = it }
        Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
            when (tab) {
                0 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClubBadge(c, 72.dp); Spacer(Modifier.width(12.dp)); JerseyPreview(c.primary, c.secondary, c.jersey, 72.dp); Spacer(Modifier.width(12.dp))
                        Column { Text(c.name, color = C.Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp); Sub("${c.country} • Division ${c.division}"); Sub("Reputation ${c.reputation}/100") }
                    }
                    SectionTitle(c.stadium.ifEmpty { "${c.name} Ground" })
                    StadiumGraphic(c.capacity, c.primary)
                    KV("Capacity", "%,d".format(c.capacity)); KV("Last attendance", "%,d".format(c.lastAttendance)); KV("Ticket price", "₹${c.ticketPrice}")
                    KV("Balance", Money.fmt(c.balance), if (c.balance >= 0) C.Green else C.Red); KV("Payroll", Money.fmt(Career.payroll(s, c)) + " / cap " + Money.fmt(Money.capFor(c.division, s.economy)))
                    KV("Sponsor", c.sponsor.ifEmpty { "None — choose one" })
                    SectionTitle("Customise")
                    Sub("Logo"); Row { for (i in Consts.LOGO_NAMES.indices) TextButton(onClick = { c.logo = i; vm.persist() }, contentPadding = PaddingValues(2.dp)) { Badge("", c.primary, c.secondary, i, 30.dp) } }
                    Sub("Jersey"); Row { for (i in Consts.JERSEY_NAMES.indices) TextButton(onClick = { c.jersey = i; vm.persist() }, contentPadding = PaddingValues(2.dp)) { JerseyPreview(c.primary, c.secondary, i, 40.dp) } }
                    Sub("Colours"); Row { Consts.CLUB_COLORS.forEach { (pc, sc) -> TextButton(onClick = { c.primary = pc; c.secondary = sc; vm.persist() }, contentPadding = PaddingValues(1.dp), modifier = Modifier.size(28.dp)) { Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Color(pc))) } } }
                }
                1 -> {
                    Sub("Upgrades are paid from your balance. Max level 5; stadium expands by 5,000 seats (max 80,000).")
                    for ((key, label, lvl, desc) in listOf(
                        Quad("training", "Training ground", c.trainingLvl, "+10% training gains per level"),
                        Quad("academy", "Youth academy", c.academyLvl, "Better and more youth prospects each off-season"),
                        Quad("medical", "Medical centre", c.medicalLvl, "Faster injury recovery, fewer injuries"),
                        Quad("scouting", "Scouting network", c.scoutLvl, "Better prospects from scouting missions"),
                        Quad("stadium", "Stadium expansion", c.capacity / 5000, "More ticket & matchday revenue")
                    )) UpgradeCard(vm, s, key, label, if (key == "stadium") "%,d seats".format(c.capacity) else "Level $lvl/5", desc)
                }
                2 -> {
                    Sub("Coaching staff (level 1–5). Better coaches improve player development in their area. Wages are paid every matchday.")
                    UpgradeCard(vm, s, "coachBat", "Batting coach", "Level ${c.coachBat}/5", "Boosts batting development")
                    UpgradeCard(vm, s, "coachBowl", "Bowling coach", "Level ${c.coachBowl}/5", "Boosts bowling development")
                    UpgradeCard(vm, s, "coachFit", "Fitness coach", "Level ${c.coachFit}/5", "Fitness recovery & fitness training")
                    KV("Staff wages per season", Money.fmt((c.coachBat + c.coachBowl + c.coachFit + c.scoutLvl) * 0.35 * s.economy))
                    KV("Facilities upkeep per season", Money.fmt(((c.trainingLvl + c.academyLvl + c.medicalLvl) * 0.25 + c.capacity / 10000.0 * 0.3) * s.economy))
                }
                3 -> {
                    SectionTitle("Sponsorship")
                    if (c.sponsor.isNotEmpty()) Text("Current: ${c.sponsor} — ${Money.fmt(c.sponsorValue)}/season", color = C.Green)
                    else if (s.phase != "PRESEASON" && s.phase != "OFFSEASON") Sub("No sponsor this season.")
                    if (c.sponsor.isEmpty()) s.sponsorOffers.forEach { o ->
                        CcmCard(onClick = { c.sponsor = o.name; c.sponsorValue = o.value; vm.persist(); vm.msg("Signed with ${o.name}") }) {
                            Text(o.name, color = C.Text, fontWeight = FontWeight.Bold); Sub("${Money.fmt(o.value)}/season • ${o.bonus}"); Sub("Tap to sign")
                        }
                    }
                    SectionTitle("Ticket pricing")
                    var price by remember { mutableFloatStateOf(c.ticketPrice.toFloat()) }
                    Text("₹${price.toInt()} per ticket", color = C.Gold, fontWeight = FontWeight.Bold)
                    Slider(price, { price = (it / 50).toInt() * 50f }, onValueChangeFinished = { c.ticketPrice = price.toInt(); vm.persist() }, valueRange = 100f..3000f)
                    Sub("Higher prices earn more per fan but reduce attendance. Demand depends on reputation, division and recent results.")
                }
                else -> {
                    if (c.trophies.isEmpty()) Sub("No trophies yet — go and win one!")
                    c.trophies.forEach { Text("🏆 $it", color = C.Gold, fontSize = 15.sp, modifier = Modifier.padding(vertical = 3.dp)) }
                    SectionTitle("Club history")
                    s.history.forEach { Sub("• $it") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

data class Quad(val a: String, val b: String, val c: Int, val d: String)

@Composable
private fun UpgradeCard(vm: GameVM, s: CareerSave, key: String, label: String, level: String, desc: String) {
    val c = s.userClub()
    val cost = Career.upgradeCost(c, key, s.economy)
    CcmCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(label, color = C.Text, fontWeight = FontWeight.Bold); Sub("$level • $desc") }
            GoldButton("Upgrade\n${Money.fmt(cost)}", enabled = c.balance >= cost) { vm.act { Career.upgrade(it, key) } }
        }
    }
}

@Composable
fun FinancesScreen(vm: GameVM) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val c = s.userClub()
    var last by remember { mutableStateOf(false) }
    val inc = if (last) c.lastIncome else c.income
    val exp = if (last) c.lastExpense else c.expense
    ScreenScaffold(vm, "Club Finances") {
        Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
            CcmCard(highlight = true) {
                KV("Bank balance", Money.fmt(c.balance), if (c.balance >= 0) C.Green else C.Red)
                KV("Salary cap (Division ${c.division})", Money.fmt(Money.capFor(c.division, s.economy)))
                KV("Payroll", Money.fmt(Career.payroll(s, c)))
                KV("Cap room", Money.fmt(Career.purse(s, c)))
                KV("Economy setting", "${"%.1f".format(s.economy)}×")
            }
            FlowRowSimple(listOf("This season", "Last season"), if (last) "Last season" else "This season") { last = it == "Last season" }
            SectionTitle("Income")
            inc.entries.sortedByDescending { it.value }.forEach { KV(it.key, Money.fmt(it.value), C.Green) }
            KV("Total income", Money.fmt(inc.values.sum()), C.Gold)
            SectionTitle("Expenses")
            exp.entries.sortedByDescending { it.value }.forEach { KV(it.key, Money.fmt(it.value), C.Red) }
            KV("Total expenses", Money.fmt(exp.values.sum()), C.Gold)
            val net = inc.values.sum() - exp.values.sum()
            KV("Net", Money.fmt(net), if (net >= 0) C.Green else C.Red)
            SectionTitle("Salaries")
            c.squad.mapNotNull { s.players[it] }.sortedByDescending { it.salary }.forEach { p -> KV("${p.name} (${p.contractYears}y)", Money.fmt(p.salary)) }
            if (c.balance < 0) Text("⚠ Your club is in debt. The board expects you to cut costs or sell players; reputation and squad morale fall every matchday while in the red.", color = C.Red, fontSize = 12.sp)
            Spacer(Modifier.height(24.dp))
        }
    }
}
