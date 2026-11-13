package com.cricketcoach.manager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.game.Modes
import com.cricketcoach.manager.ui.*

/** Category of an inbox message, guessed from its text (news strings are plain text). */
private fun category(t: String): Triple<String, String, Color> {
    val l = t.lowercase()
    return when {
        "🏆" in t || "champion" in l -> Triple("Trophy", "trophy", UI.Amber)
        "injur" in l -> Triple("Medical", "squad", C.Red)
        "signed" in l || "transfer" in l || "auction" in l || "sold" in l || "released" in l || "offer" in l -> Triple("Transfers", "gavel", UI.Violet)
        " won " in l || " beat " in l || " lost " in l || " vs " in l || "tied" in l || "draw" in l -> Triple("Match", "bat", UI.Neon)
        "sponsor" in l || "salary" in l || "revenue" in l || "₹" in t || "cr" in l && "board" in l -> Triple("Finance", "coins", UI.Cyan)
        "board" in l -> Triple("Board", "stadium", UI.Amber)
        "academy" in l || "training" in l || "youth" in l -> Triple("Training", "chart", Color(0xFF60A5FA))
        else -> Triple("News", "inbox", Color(0xFF94A3B8))
    }
}

private fun metaLabel(meta: String): String = meta.replace("S", "Season ").replace(" D", " · Day ").ifBlank { "Club bulletin" }

@Composable
fun InboxScreen(vm: GameVM) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val unread = remember { vm.unreadNews() }          // highlight what was new when the inbox was opened
    LaunchedEffect(Unit) { vm.markNewsRead(); vm.persist() }
    var open by remember { mutableIntStateOf(0) }
    ScreenScaffold(vm, "Inbox") {
        Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
            // ---- message list
            Panel(Modifier.weight(0.46f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph("inbox", DS.Gold, 17.dp); Spacer(Modifier.width(8.dp))
                    Text("Messages", color = DS.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (unread > 0) Box(Modifier.clip(RoundedCornerShape(9.dp)).background(DS.Gold).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text("$unread new", color = DS.GoldInk, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    } else Text("${s.news.size}", color = DS.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (s.offers.isNotEmpty() || s.sponsorOffers.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                        if (s.offers.isNotEmpty()) ActionChip("${s.offers.size} transfer bid${if (s.offers.size > 1) "s" else ""}", UI.Violet) { vm.navRoot(Screen.Transfers) }
                        if (s.sponsorOffers.isNotEmpty()) ActionChip("${s.sponsorOffers.size} sponsor offer${if (s.sponsorOffers.size > 1) "s" else ""}", UI.Cyan) { vm.navRoot(Screen.MyClub) }
                    }
                }
                HorizontalDivider(color = DS.Line, modifier = Modifier.padding(horizontal = 12.dp))
                if (s.news.isEmpty()) EmptyState("inbox", "No messages yet", "Play a few matches and the board, media and your staff will start writing.")
                else LazyColumn(Modifier.weight(1f).padding(top = 4.dp)) {
                    itemsIndexed(s.news) { i, n ->
                        val (meta, body) = splitNews(n); val (cat, glyph, col) = category(body)
                        val selected = i == open; val isNew = i < unread
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(RoundedCornerShape(9.dp))
                            .background(if (selected) DS.Navy4 else Color.Transparent)
                            .border(if (selected) 1.dp else 0.dp, if (selected) DS.Gold.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(9.dp))
                            .clickable { open = i }.padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(col.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { Glyph(glyph, col, 16.dp) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(cat.uppercase(), color = col, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                                    Spacer(Modifier.weight(1f)); Text(meta, color = DS.Muted, fontSize = 9.sp, maxLines = 1)
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(body, color = if (isNew) DS.White else DS.Sub, fontSize = 12.sp, fontWeight = if (isNew) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (isNew) { Spacer(Modifier.width(7.dp)); Box(Modifier.size(7.dp).clip(CircleShape).background(DS.Gold)) }
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            // ---- reading pane
            Panel(Modifier.weight(0.54f).fillMaxHeight()) {
                val n = s.news.getOrNull(open)
                if (n == null) { EmptyState("inbox", "Select a message", "Pick an item on the left to read it here.", Modifier.weight(1f)); return@Panel }
                val (meta, body) = splitNews(n); val (cat, glyph, col) = category(body)
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(col.copy(alpha = 0.16f)).border(1.dp, col.copy(alpha = 0.5f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Glyph(glyph, col, 20.dp) }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        DsTag(cat, col)
                        Spacer(Modifier.height(4.dp))
                        Text(metaLabel(meta), color = DS.Sub, fontSize = 11.sp)
                    }
                    IconButton(onClick = { if (open > 0) open-- }, enabled = open > 0, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.KeyboardArrowUp, "Newer", tint = if (open > 0) DS.Sub else DS.Muted) }
                    IconButton(onClick = { if (open < s.news.size - 1) open++ }, enabled = open < s.news.size - 1, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.KeyboardArrowDown, "Older", tint = if (open < s.news.size - 1) DS.Sub else DS.Muted) }
                }
                HorizontalDivider(color = DS.Line, modifier = Modifier.padding(horizontal = 14.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(body, color = DS.White, fontSize = 14.sp, lineHeight = 21.sp)
                }
                val action: Pair<String, () -> Unit>? = when (cat) {
                    "Match", "Trophy" -> "Fixtures & results" to { vm.navRoot(Screen.Fixtures) }
                    "Transfers" -> "Open transfers" to { vm.navRoot(if (Modes.hasTransfers(s)) Screen.Transfers else Screen.Selection) }
                    "Medical", "Training" -> "Open squad" to { vm.navRoot(Screen.Squad()) }
                    "Finance" -> "Open finances" to { vm.navRoot(Screen.Finances) }
                    "Board" -> "My club" to { vm.navRoot(Screen.MyClub) }
                    else -> null
                }
                if (action != null) {
                    HorizontalDivider(color = DS.Line, modifier = Modifier.padding(horizontal = 14.dp))
                    Row(Modifier.fillMaxWidth().padding(12.dp)) { DsButton(action.first, kind = DsKind.SECONDARY, height = DS.BtnHSmall, onClick = action.second) }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(text: String, color: Color, onClick: () -> Unit) {
    Text(text.uppercase(), color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp,
        modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f))
            .clickable { onClick() }.padding(horizontal = 9.dp, vertical = 5.dp))
}
