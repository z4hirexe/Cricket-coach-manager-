package com.cricketcoach.manager.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.R
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*

private fun ordinal(n: Int) = "$n" + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }

/** v2.2 Home: broadcast-style "upcoming match" hero, competition centre, fixtures, club status and news. */
@Composable
fun Dashboard(vm: GameVM) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val u = s.userClub()
    val myFixtures = remember(vm.tick, s.day, s.phase) {
        s.league.flatMap { c -> c.fixtures.filter { !it.played && (it.home == u.id || it.away == u.id) }.map { c to it } }.sortedBy { it.second.day }
    }
    val pendingPair = vm.pendingComp()?.let { c -> vm.pendingFixture()?.let { c to it } }
    val next = pendingPair ?: myFixtures.firstOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Hero(vm, s, next, pendingPair != null)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            CompetitionCentre(vm, s, next?.first ?: s.league.firstOrNull { c -> c.teams.contains(u.id) && c.stage != "DONE" } ?: s.league.firstOrNull(), Modifier.weight(0.42f).fillMaxHeight())
            Spacer(Modifier.width(10.dp))
            UpcomingFixtures(vm, s, myFixtures.filter { it.second.id != next?.second?.id }.take(4), Modifier.weight(0.58f).fillMaxHeight())
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            ClubStatus(vm, s, Modifier.weight(1f).fillMaxHeight())
            Spacer(Modifier.width(10.dp))
            LatestNews(vm, s, Modifier.weight(1.25f).fillMaxHeight())
        }
        if (s.phase == "PRESEASON" || s.phase == "LEAGUE" || s.phase == "PLAYOFFS") {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                if (s.phase == "PRESEASON" && s.auction != null && !s.auction!!.done) { GreenButton("Go to Auction", Modifier.weight(1f)) { vm.nav(Screen.AuctionScr) }; Spacer(Modifier.width(8.dp)) }
                if (s.phase == "PRESEASON" && s.auction == null && !s.auctionDoneThisSeason && Modes.hasAuction(s)) { OutlineBtn("Mini Auction (optional)", Modifier.weight(1f)) { vm.startMiniAuction() }; Spacer(Modifier.width(8.dp)) }
                OutlineBtn("Sim to season end", Modifier.weight(1f), enabled = vm.pending == null && !vm.busy) { vm.simToSeasonEnd() }
                Spacer(Modifier.width(8.dp))
                OutlineBtn("Save game", Modifier.weight(1f)) { vm.manualSave() }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Hero(vm: GameVM, s: CareerSave, next: Pair<Competition, Fixture>?, matchDay: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth().height(222.dp).clip(shape).border(1.5.dp, UI.Cyan.copy(alpha = 0.55f), shape)
        .clickable { GameAudio.click(); if (next != null && !matchDay && s.phase != "PRESEASON") vm.nav(Screen.Fixtures) else vm.continueAction() }) {
        Image(painterResource(R.drawable.stadium_bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99050A18), Color(0x55050A18), Color(0xE6050A18)))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0x88000000), Color.Transparent, Color(0x88000000)))))
        val tag = when { matchDay -> "MATCH DAY"; s.phase == "PRESEASON" -> "PRE-SEASON"; s.phase == "OFFSEASON" -> "SEASON COMPLETE"; next != null -> "UPCOMING MATCH"; else -> "NO FIXTURES LEFT" }
        Text(tag, color = Color(0xFF1A1200), fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(5.dp)).background(if (matchDay) UI.Neon else UI.Amber).padding(horizontal = 8.dp, vertical = 4.dp))
        if (next == null || s.phase == "PRESEASON" || s.phase == "OFFSEASON") {
            // no fixture yet: club identity + call to action
            val u = s.userClub()
            Row(Modifier.align(Alignment.Center).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamFlag(u, 96.dp)
                Spacer(Modifier.width(18.dp))
                Column {
                    Text(u.name.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(when (s.phase) {
                        "PRESEASON" -> if (s.auction != null && !s.auction!!.done) "The ${s.auction!!.type.lowercase()} auction is waiting." else "Pick your squad and XI, then start the season."
                        "OFFSEASON" -> "Review the season and move on to the next one."
                        else -> "No more fixtures for you - simulate the remaining matches."
                    }, color = C.Sub, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("SEASON ${s.season} · ${s.year}", color = UI.Amber, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp)
                }
            }
            return@Box
        }
        val (comp, fx) = next
        val h = s.clubs[fx.home]; val a = s.clubs[fx.away]
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(comp.name.substringBefore(" (").uppercase(), color = UI.Amber, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 110.dp))
            Text("${fx.stage.uppercase()} · ${comp.format}", color = Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    TeamFlag(h, 78.dp); Spacer(Modifier.height(4.dp))
                    Text(h?.name?.uppercase() ?: "", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h?.short ?: "", color = Color.White, fontWeight = FontWeight.Black, fontSize = 36.sp)
                    Text("  vs  ", color = UI.Cyan, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                    Text(a?.short ?: "", color = Color.White, fontWeight = FontWeight.Black, fontSize = 36.sp)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    TeamFlag(a, 78.dp); Spacer(Modifier.height(4.dp))
                    Text(a?.name?.uppercase() ?: "", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                // series status / league position
                val boxLabel: String; val boxValue: String
                if (comp.series) {
                    val uw = comp.fixtures.count { it.played && it.res?.winner == fx.home }; val ow = comp.fixtures.count { it.played && it.res?.winner == fx.away }
                    boxLabel = "SERIES STATUS"; boxValue = "$uw - $ow"
                } else {
                    val tbl = runCatching { CompEngine.table(comp, if (comp.groups.size > 1) comp.groups.indexOfFirst { s.userClubId in it }.takeIf { it >= 0 } else null) }.getOrNull()
                    val pos = tbl?.indexOfFirst { it.team == s.userClubId } ?: -1
                    boxLabel = "POSITION"; boxValue = if (pos >= 0 && (tbl?.getOrNull(pos)?.p ?: 0) > 0) "${ordinal(pos + 1)} · ${tbl!![pos].pts} PTS" else "-"
                }
                Column(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xCC0B111C)).border(1.dp, UI.Line, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(boxLabel, color = C.Sub, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(boxValue, color = UI.Amber, fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph("calendar", Color.White, 13.dp); Text(" DAY ${fx.day}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("   ·   ", color = C.Sub, fontSize = 11.sp)
                    Text("📍 " + (h?.stadium?.ifBlank { null } ?: "TBC").uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 230.dp))
                }
                Spacer(Modifier.weight(1f))
                val w = Career.forecast(s, comp, fx)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(if (w == "Sunny") "sun" else "cloud", if (w == "Sunny") UI.Amber else Color(0xFFCBD5E1), 22.dp)
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(when (w) { "Sunny" -> "CLEAR SKY"; "Humid" -> "HUMID"; "Overcast" -> "OVERCAST"; else -> "CLOUDY" }, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Text("FORECAST", color = C.Sub, fontSize = 8.sp, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompetitionCentre(vm: GameVM, s: CareerSave, comp: Competition?, modifier: Modifier) {
    Panel(modifier, border = UI.Amber.copy(alpha = 0.45f)) {
        PanelHeader(if (comp?.series == true) "Tour Centre" else "Competition Centre", UI.Amber, "trophy") { vm.navRoot(Screen.Table) }
        if (comp == null) { Sub("No competition yet.", Modifier.padding(14.dp)); return@Panel }
        val u = s.userClubId
        Column(Modifier.padding(horizontal = 14.dp)) {
            if (comp.series) {
                val oppId = comp.teams.firstOrNull { it != u } ?: ""
                val uw = comp.fixtures.count { it.played && it.res?.winner == u }; val ow = comp.fixtures.count { it.played && it.res?.winner == oppId }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { TeamFlag(s.clubs[u], 40.dp); Text(s.clubs[u]?.short ?: "", color = C.Text, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$uw - $ow", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
                        Text("SERIES SCORE", color = C.Sub, fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { TeamFlag(s.clubs[oppId], 40.dp); Text(s.clubs[oppId]?.short ?: "", color = C.Text, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
            } else {
                val tbl = remember(vm.tick, comp.id) { runCatching { CompEngine.table(comp, if (comp.groups.size > 1) comp.groups.indexOfFirst { u in it }.takeIf { it >= 0 } else null) }.getOrDefault(emptyList()) }
                Text(comp.name, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                val idx = tbl.indexOfFirst { it.team == u }
                val window = if (tbl.size <= 4) tbl.indices.toList() else { val st = (idx - 1).coerceIn(0, tbl.size - 4); (st until st + 4).toList() }
                for (i in window) {
                    val r = tbl[i]; val me = r.team == u
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (me) UI.NeonDeep.copy(alpha = 0.45f) else Color.Transparent).padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = if (me) UI.Neon else C.Sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                        TeamFlag(s.clubs[r.team], 22.dp); Spacer(Modifier.width(6.dp))
                        Text(s.clubs[r.team]?.short ?: r.team, color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("P${r.p}  W${r.w}", color = C.Sub, fontSize = 10.sp); Spacer(Modifier.width(8.dp))
                        Text("${r.pts}", color = if (me) UI.Neon else C.Text, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            HorizontalDivider(color = UI.Line, modifier = Modifier.padding(vertical = 8.dp))
            val bat = comp.bat.entries.maxByOrNull { it.value.runs }
            val bowl = comp.bowlS.entries.maxByOrNull { it.value.wk * 1000 - it.value.runs }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Leader(Modifier.weight(1f), "bat", UI.Neon, bat?.let { s.players[it.key]?.short() } ?: "-", bat?.let { "${it.value.runs} Runs" } ?: "No runs yet") { bat?.let { vm.nav(Screen.Profile(it.key)) } }
                Box(Modifier.width(1.dp).height(30.dp).background(UI.Line))
                Leader(Modifier.weight(1f), "ball", UI.Cyan, bowl?.let { s.players[it.key]?.short() } ?: "-", bowl?.let { "${it.value.wk} Wkts" } ?: "No wickets yet") { bowl?.let { vm.nav(Screen.Profile(it.key)) } }
            }
        }
    }
}

@Composable
private fun Leader(modifier: Modifier, kind: String, color: Color, name: String, value: String, onClick: () -> Unit) {
    Row(modifier.clickable { onClick() }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            if (kind == "bat") Glyph("bat", color, 16.dp) else Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(color))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(name, color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun UpcomingFixtures(vm: GameVM, s: CareerSave, list: List<Pair<Competition, Fixture>>, modifier: Modifier) {
    Panel(modifier, border = UI.Cyan.copy(alpha = 0.4f)) {
        PanelHeader(if (s.mode == Modes.INTL) "Upcoming Tour Fixtures" else "Upcoming Fixtures", UI.Cyan, "calendar") { vm.navRoot(Screen.Fixtures) }
        if (list.isEmpty()) { Sub("No further fixtures scheduled yet.", Modifier.padding(14.dp)); return@Panel }
        list.forEachIndexed { i, (c, f) ->
            if (i > 0) HorizontalDivider(color = UI.Line, modifier = Modifier.padding(horizontal = 14.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamFlag(s.clubs[f.home], 34.dp)
                Text(" vs ", color = C.Sub, fontSize = 10.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold)
                TeamFlag(s.clubs[f.away], 34.dp)
                Spacer(Modifier.width(10.dp))
                Text(c.format, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF60A5FA), RoundedCornerShape(4.dp)).background(Color(0xFF1E3A8A).copy(alpha = 0.5f)).padding(horizontal = 6.dp, vertical = 2.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("${s.clubs[f.home]?.short} v ${s.clubs[f.away]?.short}", color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(c.name.substringBefore(" (").uppercase(), color = UI.Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("DAY ${f.day}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(f.stage, color = C.Sub, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ClubStatus(vm: GameVM, s: CareerSave, modifier: Modifier) {
    val u = s.userClub()
    Panel(modifier, border = UI.Neon.copy(alpha = 0.35f)) {
        PanelHeader(if (s.mode == Modes.CLUB) "Club Status" else "Team Status", UI.Neon, "stadium") { vm.navRoot(Screen.MyClub) }
        @Composable fun stat(label: String, value: String, color: Color = C.Text, frac: Float? = null) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                Row { Text(label.uppercase(), color = C.Sub, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f)); Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black) }
                if (frac != null) {
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(UI.Line)) {
                        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color))))
                    }
                }
            }
        }
        stat("Balance", Money.fmt(u.balance), if (u.balance >= 0) UI.Neon else C.Red)
        if (Modes.hasTransfers(s)) stat("Cap room", Money.fmt(Career.purse(s, u)))
        stat("Board confidence", "${s.boardRating}%", if (s.boardRating < 25) C.Red else UI.Amber, s.boardRating / 100f)
        stat("Reputation", "${u.reputation}/100", UI.Cyan, u.reputation / 100f)
        stat("Squad", "${u.squad.size}/${Modes.maxSquad(s)}", if (u.squad.size < 15) C.Red else C.Text)
        stat("Followers", "%,d".format(s.followers), UI.Pink)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LatestNews(vm: GameVM, s: CareerSave, modifier: Modifier) {
    val unread = vm.unreadNews()
    Panel(modifier, border = UI.Line) {
        PanelHeader(if (unread > 0) "Inbox · $unread new" else "Inbox", C.Gold, "inbox") { vm.navRoot(Screen.Inbox) }
        if (s.lastResult.isNotEmpty()) {
            Text("LAST RESULT", color = C.Sub, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 14.dp))
            Text(s.lastResult, color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
            HorizontalDivider(color = UI.Line, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        }
        if (s.news.isEmpty()) Sub("No messages yet.", Modifier.padding(horizontal = 14.dp))
        s.news.take(5).forEachIndexed { i, n ->
            val (meta, body) = splitNews(n)
            Row(Modifier.fillMaxWidth().clickable { vm.navRoot(Screen.Inbox) }.padding(horizontal = 14.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(if (i < unread) UI.Neon else UI.Line))
                Spacer(Modifier.width(8.dp))
                Text(body, color = if (i < unread) C.Text else C.Sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(meta, color = C.Sub, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** "S1 D12 • text" -> ("S1 D12", "text") */
fun splitNews(n: String): Pair<String, String> {
    val i = n.indexOf(" • ")
    return if (i in 1..12) n.substring(0, i) to n.substring(i + 3) else "" to n
}
