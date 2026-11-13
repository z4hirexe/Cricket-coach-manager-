package com.cricketcoach.manager.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.cricketcoach.manager.audio.GameAudio
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
fun MyClubScreen(vm: GameVM, initialTab: Int = 0) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val c = s.userClub()
    var tab by remember { mutableIntStateOf(initialTab) }
    ScreenScaffold(vm, if (s.mode == Modes.CLUB) "My Club" else c.name) {
        Tabs(listOf("Overview", "Facilities", "Staff", "Sponsors & tickets", "Trophies"), tab) { tab = it }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp)) {
            when (tab) {
                0 -> ClubOverview(vm, s, c)
                1 -> ClubFacilities(vm, s, c)
                2 -> ClubStaff(vm, s, c)
                3 -> ClubSponsors(vm, s, c)
                else -> ClubTrophies(s, c)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ClubOverview(vm: GameVM, s: CareerSave, c: Club) {
    // identity hero
    DsCard(pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClubBadge(c, 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                DsTitle(c.name, size = 22.sp)
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DsTag(c.country, DS.Sub); DsTag("Division ${c.division}", DS.Accent); DsTag("Rep ${c.reputation}/100", DS.Gold)
                }
            }
            JerseyPreview(c.primary, c.secondary, c.jersey, 62.dp)
        }
    }
    Spacer(Modifier.height(10.dp))
    // key numbers
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Balance", Money.fmt(c.balance), Modifier.weight(1f), accent = if (c.balance >= 0) DS.Good else DS.Bad)
        StatTile("Payroll", Money.fmt(Career.payroll(s, c)), Modifier.weight(1f), sub = "cap ${Money.fmt(Money.capFor(c.division, s.economy))}", accent = DS.White)
        StatTile("Squad", "${c.squad.size}/25", Modifier.weight(1f), sub = "${Career.overseasCount(s, c)} overseas", accent = DS.White)
    }
    Spacer(Modifier.height(10.dp))
    // home ground
    DsCard {
        SectionLabel("Home ground", trailing = { Text(c.stadium.ifEmpty { "${c.name} Ground" }, color = DS.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        StadiumGraphic(c.capacity, c.primary)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Capacity", "%,d".format(c.capacity), Modifier.weight(1f), accent = DS.White)
            StatTile("Last gate", "%,d".format(c.lastAttendance), Modifier.weight(1f), accent = DS.White)
            StatTile("Ticket", "₹${c.ticketPrice}", Modifier.weight(1f), accent = DS.White)
        }
    }
    Spacer(Modifier.height(10.dp))
    // customise
    DsCard {
        SectionLabel("Customise", trailing = { Text(Consts.LOGO_NAMES.getOrElse(c.logo) { "Shield" }, color = DS.Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) })
        Text("Crest", color = DS.Sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in Consts.LOGO_NAMES.indices) PickerTile(c.logo == i, { c.logo = i; vm.persist() }) { Badge("", c.primary, c.secondary, i, 34.dp) }
        }
        Spacer(Modifier.height(10.dp))
        Text("Jersey", color = DS.Sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in Consts.JERSEY_NAMES.indices) PickerTile(c.jersey == i, { c.jersey = i; vm.persist() }) { JerseyPreview(c.primary, c.secondary, i, 38.dp) }
        }
        Spacer(Modifier.height(10.dp))
        Text("Colours", color = DS.Sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Consts.CLUB_COLORS.forEach { (pc, sc) ->
                val on = c.primary == pc
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(pc)).border(if (on) 2.dp else 1.dp, if (on) DS.Gold else DS.Line, CircleShape)
                    .clickable { c.primary = pc; c.secondary = sc; vm.persist() }, contentAlignment = Alignment.Center) {
                    if (on) Icon(Icons.Filled.Check, null, tint = if (Color(pc).luminanceApprox() > 0.6f) Color.Black else Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
private fun PickerTile(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(9.dp)).background(if (selected) DS.Navy4 else DS.Navy2)
        .border(if (selected) 1.5.dp else 1.dp, if (selected) DS.Gold else DS.Line, RoundedCornerShape(9.dp))
        .clickable { GameAudio.click(); onClick() }.padding(6.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ClubFacilities(vm: GameVM, s: CareerSave, c: Club) {
    Text("Invest in the club to develop players faster and grow matchday revenue. Upgrades are paid from your balance.", color = DS.Sub, fontSize = 12.sp, lineHeight = 16.sp)
    Spacer(Modifier.height(8.dp))
    UpgradeCard(vm, s, "training", "Training ground", "chart", c.trainingLvl, "+10% training gains per level")
    UpgradeCard(vm, s, "academy", "Youth academy", "squad", c.academyLvl, "Better and more youth prospects each off-season")
    UpgradeCard(vm, s, "medical", "Medical centre", "squad", c.medicalLvl, "Faster injury recovery, fewer injuries")
    UpgradeCard(vm, s, "scouting", "Scouting network", "search", c.scoutLvl, "Better prospects from scouting missions")
    UpgradeCard(vm, s, "stadium", "Stadium expansion", "stadium", c.capacity / 5000, "More ticket & matchday revenue", stadium = true)
}

@Composable
private fun ClubStaff(vm: GameVM, s: CareerSave, c: Club) {
    Text("Coaching staff (level 1–5). Better coaches improve player development in their area. Wages are paid every matchday.", color = DS.Sub, fontSize = 12.sp, lineHeight = 16.sp)
    Spacer(Modifier.height(8.dp))
    UpgradeCard(vm, s, "coachBat", "Batting coach", "bat", c.coachBat, "Boosts batting development")
    UpgradeCard(vm, s, "coachBowl", "Bowling coach", "bat", c.coachBowl, "Boosts bowling development")
    UpgradeCard(vm, s, "coachFit", "Fitness coach", "chart", c.coachFit, "Fitness recovery & fitness training")
    Spacer(Modifier.height(4.dp))
    DsCard {
        SectionLabel("Running costs")
        KV("Staff wages / season", Money.fmt((c.coachBat + c.coachBowl + c.coachFit + c.scoutLvl) * 0.35 * s.economy))
        KV("Facilities upkeep / season", Money.fmt(((c.trainingLvl + c.academyLvl + c.medicalLvl) * 0.25 + c.capacity / 10000.0 * 0.3) * s.economy))
    }
}

@Composable
private fun ClubSponsors(vm: GameVM, s: CareerSave, c: Club) {
    DsCard {
        SectionLabel("Sponsorship")
        if (c.sponsor.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph("coins", DS.Good, 20.dp); Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) { Text(c.sponsor, color = DS.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("${Money.fmt(c.sponsorValue)} / season", color = DS.Good, fontSize = 12.sp) }
                DsTag("Active", DS.Good)
            }
        } else if (s.sponsorOffers.isEmpty()) {
            Text(if (s.phase != "PRESEASON" && s.phase != "OFFSEASON") "No sponsor this season. Offers arrive in the pre-season." else "No sponsor offers right now.", color = DS.Sub, fontSize = 12.sp)
        } else {
            Text("Choose a season sponsor:", color = DS.Sub, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            s.sponsorOffers.forEach { o ->
                DsCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), onClick = { c.sponsor = o.name; c.sponsorValue = o.value; vm.persist(); vm.msg("Signed with ${o.name}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(o.name, color = DS.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("${Money.fmt(o.value)} / season · ${o.bonus}", color = DS.Sub, fontSize = 11.sp) }
                        DsButton("Sign", height = DS.BtnHSmall) { c.sponsor = o.name; c.sponsorValue = o.value; vm.persist(); vm.msg("Signed with ${o.name}") }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    DsCard {
        SectionLabel("Ticket pricing", trailing = { Text("₹${c.ticketPrice}", color = DS.Gold, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp) })
        var price by remember { mutableFloatStateOf(c.ticketPrice.toFloat()) }
        Slider(price, { price = (it / 50).toInt() * 50f }, onValueChangeFinished = { c.ticketPrice = price.toInt(); vm.persist() }, valueRange = 100f..3000f,
            colors = SliderDefaults.colors(thumbColor = DS.Gold, activeTrackColor = DS.Gold, inactiveTrackColor = DS.Line))
        Text("Higher prices earn more per fan but reduce attendance. Demand depends on reputation, division and recent results.", color = DS.Sub, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun ClubTrophies(s: CareerSave, c: Club) {
    DsCard {
        SectionLabel("Trophy cabinet", trailing = { Text("${c.trophies.size}", color = DS.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold) })
        if (c.trophies.isEmpty()) EmptyState("trophy", "No trophies yet", "Win a league or cup and it will be displayed here.")
        else c.trophies.forEach {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph("trophy", DS.Gold, 18.dp); Spacer(Modifier.width(10.dp)); Text(it, color = DS.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    DsCard {
        SectionLabel("Club history")
        if (s.history.isEmpty()) Text("Your story starts here.", color = DS.Sub, fontSize = 12.sp)
        else s.history.forEach { Text("·  $it", color = DS.Sub, fontSize = 12.sp, lineHeight = 18.sp) }
    }
}

@Composable
private fun UpgradeCard(vm: GameVM, s: CareerSave, key: String, label: String, glyph: String, lvl: Int, desc: String, stadium: Boolean = false) {
    val c = s.userClub()
    val cost = Career.upgradeCost(c, key, s.economy)
    val maxed = if (stadium) c.capacity >= 80000 else lvl >= 5
    DsCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(DS.Navy4), contentAlignment = Alignment.Center) { Glyph(glyph, DS.Gold, 20.dp) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = DS.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (stadium) "%,d seats".format(c.capacity) else "Level $lvl/5", color = DS.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                if (stadium) ProgressTrack(c.capacity / 80000f) else LevelDots(lvl)
                Spacer(Modifier.height(4.dp))
                Text(desc, color = DS.Sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(11.dp))
            if (maxed) DsTag("Maxed", DS.Good)
            else Column(horizontalAlignment = Alignment.End) {
                Text(Money.fmt(cost), color = DS.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                DsButton("Upgrade", height = DS.BtnHSmall, enabled = c.balance >= cost) { vm.act { Career.upgrade(it, key) } }
            }
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
