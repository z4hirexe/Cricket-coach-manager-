package com.cricketcoach.manager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.cricketcoach.manager.game.Money
import com.cricketcoach.manager.ui.*

/** Category of an inbox message, guessed from its text (news strings are plain text). */
private fun category(t: String): Triple<String, String, Color> {
    val l = t.lowercase()
    return when {
        "🏆" in t || "champion" in l -> Triple("TROPHY", "trophy", UI.Amber)
        "injur" in l -> Triple("MEDICAL", "squad", C.Red)
        "signed" in l || "transfer" in l || "auction" in l || "sold" in l || "released" in l || "offer" in l -> Triple("TRANSFERS", "gavel", UI.Violet)
        " won " in l || " beat " in l || " lost " in l || " vs " in l || "tied" in l || "draw" in l -> Triple("MATCH", "bat", UI.Neon)
        "sponsor" in l || "salary" in l || "revenue" in l || "₹" in t || "cr" in l && "board" in l -> Triple("FINANCE", "coins", UI.Cyan)
        "board" in l -> Triple("BOARD", "stadium", UI.Amber)
        "academy" in l || "training" in l || "youth" in l -> Triple("TRAINING", "chart", Color(0xFF60A5FA))
        else -> Triple("NEWS", "inbox", Color(0xFF94A3B8))
    }
}

@Composable
fun InboxScreen(vm: GameVM) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val unread = remember { vm.unreadNews() }          // highlight what was new when the inbox was opened
    LaunchedEffect(Unit) { vm.markNewsRead(); vm.persist() }
    var open by remember { mutableIntStateOf(0) }
    ScreenScaffold(vm, "Inbox") {
        Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
            // message list
            Panel(Modifier.weight(0.5f).fillMaxHeight()) {
                PanelHeader("Messages · ${s.news.size}", C.Gold, "inbox")
                // action items first: transfer bids and sponsor offers waiting for a decision
                if (s.offers.isNotEmpty() || s.sponsorOffers.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)) {
                        if (s.offers.isNotEmpty()) ActionChip("${s.offers.size} transfer bid${if (s.offers.size > 1) "s" else ""}", UI.Violet) { vm.navRoot(Screen.Transfers) }
                        if (s.sponsorOffers.isNotEmpty()) ActionChip("${s.sponsorOffers.size} sponsor offer${if (s.sponsorOffers.size > 1) "s" else ""}", UI.Cyan) { vm.navRoot(Screen.MyClub) }
                    }
                }
                if (s.news.isEmpty()) Sub("No messages yet - play some matches.", Modifier.padding(14.dp))
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(s.news) { i, n ->
                        val (meta, body) = splitNews(n); val (cat, glyph, col) = category(body)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (i == open) col.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { open = i }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(col.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Glyph(glyph, col, 16.dp) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(cat, color = col, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                    if (i < unread) { Spacer(Modifier.width(6.dp)); Text("NEW", color = Color(0xFF052E16), fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(UI.Neon).padding(horizontal = 4.dp)) }
                                    Spacer(Modifier.weight(1f)); Text(meta, color = C.Sub, fontSize = 9.sp)
                                }
                                Text(body, color = if (i < unread) C.Text else Color(0xFFB6C2D6), fontSize = 12.sp, fontWeight = if (i < unread) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            // reading pane
            Panel(Modifier.weight(0.5f).fillMaxHeight(), border = UI.Line) {
                val n = s.news.getOrNull(open)
                if (n == null) { Sub("Select a message.", Modifier.padding(14.dp)); return@Panel }
                val (meta, body) = splitNews(n); val (cat, glyph, col) = category(body)
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(col.copy(alpha = 0.2f)).border(1.dp, col, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Glyph(glyph, col, 22.dp) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(cat, color = col, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                        Text(meta.replace("S", "Season ").replace(" D", " · Day "), color = C.Sub, fontSize = 10.sp)
                    }
                }
                HorizontalDivider(color = UI.Line, modifier = Modifier.padding(horizontal = 14.dp))
                Text(body, color = C.Text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(14.dp))
                Spacer(Modifier.weight(1f))
                Row(Modifier.padding(14.dp)) {
                    when (cat) {
                        "MATCH", "TROPHY" -> OutlineBtn("Fixtures & results") { vm.navRoot(Screen.Fixtures) }
                        "TRANSFERS" -> OutlineBtn("Open transfers") { vm.navRoot(if (com.cricketcoach.manager.game.Modes.hasTransfers(s)) Screen.Transfers else Screen.Selection) }
                        "MEDICAL" -> OutlineBtn("Open squad") { vm.navRoot(Screen.Squad()) }
                        "FINANCE" -> OutlineBtn("Open finances (${Money.fmt(s.userClub().balance)})") { vm.navRoot(Screen.Finances) }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(text: String, color: Color, onClick: () -> Unit) {
    Text(text.uppercase(), color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp,
        modifier = Modifier.padding(end = 6.dp, bottom = 4.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, color, RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f))
            .clickable { onClick() }.padding(horizontal = 8.dp, vertical = 4.dp))
}
