package com.cricketcoach.manager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/* ============================================================================================
 *  Phase A — Cricket Calendar / Match Schedule Centre
 *  Month grid + date agenda with match cards (Play / Simulate / Details) and off-field events.
 * ============================================================================================ */

private val Navy = Color(0xFF07112A)
private val NavyHi = Color(0xFF0E1E45)
private val GoldLine = Color(0x66E9B949)

fun calColor(cat: String): Color = when (cat) {
    Schedule.INTL -> Color(0xFF38BDF8)
    Schedule.IPL -> Color(0xFFA78BFA)
    Schedule.DOMESTIC -> Color(0xFF4ADE80)
    Schedule.TOURN -> Color(0xFFF59E0B)
    Schedule.AUCTION -> Color(0xFFE9B949)
    Schedule.TRANSFER -> Color(0xFFF472B6)
    Schedule.TRAINING -> Color(0xFF64748B)
    else -> Color(0xFFEF5350)
}

private val DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)

@Composable
fun CalendarScreen(vm: GameVM, initialDate: LocalDate? = null) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    @Suppress("UNUSED_VARIABLE") val st = vm.simTick
    val today = Schedule.today(s)
    var month by remember { mutableStateOf(YearMonth.from(initialDate ?: today)) }
    var selected by remember { mutableStateOf(initialDate ?: today) }
    var filters by remember { mutableStateOf(Schedule.CATEGORIES.toSet()) }
    var detail by remember { mutableStateOf<Schedule.Entry?>(null) }
    var confirm by remember { mutableStateOf<Triple<Schedule.Entry, Boolean, Int>?>(null) }
    // all entries; recomputed when the save advances
    val all = remember(s, s.day, s.phase, vm.tick, vm.simTick, vm.busy, vm.pending, s.tournament?.fixtures?.count { it.played }) { Schedule.all(s) }
    val visible = all.filter { e -> e.cats.any { it in filters } }

    // after a calendar sim: jump to that match and open its result
    LaunchedEffect(vm.calendarFocus) {
        val f = vm.calendarFocus ?: return@LaunchedEffect
        val e = all.firstOrNull { it.fx?.id == f.fixtureId }
        if (e != null) { selected = e.date; month = YearMonth.from(e.date); if (e.fx?.played == true) detail = e }
        vm.calendarFocus = null
    }

    ScreenScaffold(vm, "Cricket Calendar", actions = {
        CalAction("Advance day", Color(0xFF1E3A8A), enabled = !vm.busy) { vm.calendarAdvanceDay() }
        Spacer(Modifier.width(6.dp))
        CalAction(if (vm.pending != null) "Go to match" else "Sim to my next match", null, enabled = !vm.busy) { vm.simToNextUserMatch() }
        Spacer(Modifier.width(4.dp))
    }) {
        Row(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
            // ---------------- left: month grid ----------------
            Column(Modifier.weight(0.47f).fillMaxHeight().clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(NavyHi, Navy))).border(1.dp, GoldLine, RoundedCornerShape(14.dp)).padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundIcon(Icons.Filled.KeyboardArrowLeft, "Previous month") { month = month.minusMonths(1) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase() + " " + month.year, fontFamily = Display, color = C.GoldLight,
                            fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 1.5.sp, maxLines = 1)
                        val n = visible.count { it.isMatch && YearMonth.from(it.date) == month }
                        Text(if (n == 0) "No matches this month" else "$n matches this month", color = C.Sub, fontSize = 10.sp)
                    }
                    RoundIcon(Icons.Filled.KeyboardArrowRight, "Next month") { month = month.plusMonths(1) }
                    Spacer(Modifier.width(4.dp))
                    SmallPill("TODAY", C.Gold, filled = month == YearMonth.from(today) && selected == today) { month = YearMonth.from(today); selected = today }
                }
                Spacer(Modifier.height(4.dp))
                MonthGrid(month, selected, today, visible, Modifier.weight(1f)) { selected = it }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = C.Gold.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(Schedule.DISCLAIMER, color = C.Sub, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            // ---------------- right: filters + agenda ----------------
            Column(Modifier.weight(0.53f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    SmallPill("ALL", C.Gold, filled = filters.size == Schedule.CATEGORIES.size) { filters = Schedule.CATEGORIES.toSet() }
                    for (c in Schedule.CATEGORIES) {
                        Spacer(Modifier.width(5.dp))
                        FilterPill(Schedule.catLabel(c), calColor(c), c in filters) {
                            filters = if (filters.size == Schedule.CATEGORIES.size) setOf(c) else if (c in filters) (filters - c).ifEmpty { Schedule.CATEGORIES.toSet() } else filters + c
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                val day = Schedule.on(visible, selected)
                val matches = day.filter { it.isMatch }.sortedWith(compareByDescending<Schedule.Entry> { it.mine }.thenBy { Schedule.startTime(s, it.comp!!, it.fx!!) })
                val events = day.filter { !it.isMatch }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selected.format(DATE_FMT).uppercase(), fontFamily = Display, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                    if (selected == today) SmallPill("TODAY", C.Gold, filled = true) {}
                    if (s.phase == "LEAGUE" || s.phase == "PLAYOFFS") { Spacer(Modifier.width(4.dp)); Text("Game day ${Schedule.dayOf(s, selected).coerceAtLeast(0)}", color = C.Sub, fontSize = 10.sp) }
                }
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(events) { e -> EventRow(vm, s, e) }
                    items(matches, key = { it.fx!!.id }) { e ->
                        MatchCard(vm, s, e,
                            onPlay = { val n = vm.userMatchesBefore(e.comp!!.id, e.fx!!.id); if (n > 0) confirm = Triple(e, true, n) else vm.calendarGo(e.comp.id, e.fx.id, true) },
                            onSim = { val n = vm.userMatchesBefore(e.comp!!.id, e.fx!!.id); if (n > 0) confirm = Triple(e, false, n) else vm.calendarGo(e.comp.id, e.fx.id, false) },
                            onDetails = { detail = e })
                    }
                    if (matches.isEmpty() && events.isEmpty()) item {
                        Text("Nothing scheduled for this date.", color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                    }
                    // the next few matches after the selected date, so the agenda is never empty
                    val upcoming = visible.filter { it.isMatch && it.date.isAfter(selected) && it.fx?.played == false }.sortedWith(compareByDescending<Schedule.Entry> { it.mine }.thenBy { it.date }).let { l ->
                        (l.filter { it.mine }.take(3) + l.filter { !it.mine }.take(3)).sortedBy { it.date }
                    }
                    if (upcoming.isNotEmpty()) {
                        item { Text("COMING UP", color = C.Gold, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) }
                        items(upcoming, key = { "u" + it.fx!!.id }) { e -> UpcomingRow(s, e) { selected = e.date; month = YearMonth.from(e.date) } }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                }
            }
        }
    }

    val de = detail
    val df = de?.fx
    if (de != null && df != null) {
        if (df.played && df.res != null) FixtureDetail(s, df) { detail = null }
        else MatchPreviewDialog(vm, s, de, onClose = { detail = null },
            onPlay = { detail = null; vm.calendarGo(de.comp!!.id, df.id, true) }, onSim = { detail = null; vm.calendarGo(de.comp!!.id, df.id, false) })
    }
    confirm?.let { (e, play, n) ->
        AlertDialog(onDismissRequest = { confirm = null }, containerColor = C.Surface,
            title = { Text(if (play) "Jump to this match?" else "Simulate to this date?", color = C.Gold, fontWeight = FontWeight.Bold) },
            text = { Text("$n of your earlier match${if (n == 1) "" else "es"} will be simulated automatically before ${e.title} on ${e.date.format(DATE_FMT)}.", color = C.Text) },
            confirmButton = { TextButton({ confirm = null; vm.calendarGo(e.comp!!.id, e.fx!!.id, play) }) { Text("CONTINUE", color = C.Gold, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ confirm = null }) { Text("CANCEL", color = C.Sub) } })
    }
}

/* ---------------- month grid ---------------- */

@Composable
private fun MonthGrid(month: YearMonth, selected: LocalDate, today: LocalDate, entries: List<Schedule.Entry>, modifier: Modifier, onPick: (LocalDate) -> Unit) {
    val inMonth = remember(month, entries) { Schedule.inMonth(entries, month) }
    val first = month.atDay(1)
    val lead = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val cells = lead + month.lengthOfMonth()
    val rows = (cells + 6) / 7
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            for (d in listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"))
                Text(d, color = C.Sub, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(2.dp))
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - lead + 1
                    if (idx < 1 || idx > month.lengthOfMonth()) { Spacer(Modifier.weight(1f)); continue }
                    val date = month.atDay(idx)
                    val list = Schedule.on(inMonth, date)
                    DayCell(date, list, date == selected, date == today, Modifier.weight(1f).fillMaxHeight()) { GameAudio.click(); onPick(date) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, list: List<Schedule.Entry>, sel: Boolean, isToday: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(7.dp)
    val mine = list.any { it.isMatch && it.mine }
    val matches = list.count { it.isMatch }
    val bg = when { sel -> Brush.verticalGradient(listOf(Color(0xFF3A2E0B), Color(0xFF1E1606))); mine -> Brush.verticalGradient(listOf(Color(0xFF13306B), Color(0xFF0B1D45))); else -> Brush.verticalGradient(listOf(Color(0x22FFFFFF), Color(0x0AFFFFFF))) }
    Box(modifier.padding(1.5.dp).clip(shape).background(bg)
        .border(if (sel || isToday) 1.5.dp else 0.5.dp, when { sel -> C.Gold; isToday -> C.GoldLight.copy(alpha = 0.8f); else -> Color(0x1AFFFFFF) }, shape)
        .clickable(onClick = onClick).padding(horizontal = 3.dp, vertical = 2.dp)) {
        Text("${date.dayOfMonth}", color = if (sel) C.GoldLight else if (date.dayOfWeek.value >= 6) C.Text else C.Text.copy(alpha = 0.85f),
            fontSize = 11.sp, fontWeight = if (isToday || sel) FontWeight.Black else FontWeight.SemiBold, modifier = Modifier.align(Alignment.TopStart))
        if (matches > 0) Text(if (mine) "★" else "$matches", color = if (mine) C.Gold else C.Sub, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd))
        // category dots
        val cats = list.flatMap { if (it.isMatch) listOf(it.cats.first()) else listOf(it.cat) }.distinct().take(4)
        Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.Center) {
            for (c in cats) Box(Modifier.padding(horizontal = 1.dp).size(width = if (c == Schedule.TRAINING) 4.dp else 6.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(calColor(c)))
        }
    }
}

/* ---------------- agenda rows ---------------- */

@Composable
private fun MatchCard(vm: GameVM, s: CareerSave, e: Schedule.Entry, onPlay: () -> Unit, onSim: () -> Unit, onDetails: () -> Unit) {
    val c = e.comp!!; val f = e.fx!!
    val h = s.clubs[f.home]; val a = s.clubs[f.away]
    val status = Schedule.status(s, c, f, vm.pending?.fixtureId)
    val accent = calColor(e.cats.first())
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(shape)
        .background(Brush.horizontalGradient(listOf(Color(0xFF102452), Color(0xFF0A1636))))
        .border(1.dp, if (e.mine) C.Gold.copy(alpha = 0.75f) else Color(0x223D5A99), shape)
        .clickable { GameAudio.click(); onDetails() }) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Column(Modifier.weight(1f).padding(horizontal = 9.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(androidx.compose.ui.text.buildAnnotatedString {
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = C.Gold, fontWeight = FontWeight.Black)); append(c.name.uppercase()); pop()
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = C.Sub)); append("  ·  ${f.stage}"); pop()
                }, fontSize = 9.5.sp, letterSpacing = 0.6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(4.dp))
                MiniTag(Schedule.formatLabel(c, f), accent)
                Spacer(Modifier.width(4.dp))
                StatusTag(status)
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamFlag(h, 30.dp); Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(h?.short ?: "?", fontFamily = Display, color = if (f.res?.winner == f.home) C.GoldLight else C.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                    Text(f.res?.hScore?.ifBlank { null } ?: (h?.name ?: ""), color = C.Sub, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("VS", color = C.Gold.copy(alpha = 0.7f), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(a?.short ?: "?", fontFamily = Display, color = if (f.res?.winner == f.away) C.GoldLight else C.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                    Text(f.res?.aScore?.ifBlank { null } ?: (a?.name ?: ""), color = C.Sub, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                }
                Spacer(Modifier.width(6.dp)); TeamFlag(a, 30.dp)
            }
            Spacer(Modifier.height(4.dp))
            val span = if (e.endDate != e.date) "${e.date.format(DateTimeFormatter.ofPattern("d", Locale.ENGLISH))}–${e.endDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} (${Schedule.duration(c, f)} days)"
                else e.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
            Text("$span · ${Schedule.startTime(s, c, f)} local · ${Schedule.venue(s, f)}",
                color = C.Sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            f.res?.let { Text(it.text, color = C.Positive, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!f.played && e.mine) { CardBtn(if (status == "MATCH DAY") "Play now" else "Play", gold = true, enabled = !vm.busy, icon = true) { onPlay() }; Spacer(Modifier.width(6.dp)) }
                if (!f.played) { CardBtn("Simulate", enabled = !vm.busy) { onSim() }; Spacer(Modifier.width(6.dp)) }
                CardBtn(if (f.played) "Scorecard" else "View details", ghost = true) { onDetails() }
            }
        }
    }
}

@Composable
private fun EventRow(vm: GameVM, s: CareerSave, e: Schedule.Entry) {
    val col = calColor(e.cat)
    val shape = RoundedCornerShape(10.dp)
    val action: (() -> Unit)? = when (e.cat) {
        Schedule.AUCTION -> if (s.auction != null && s.auction?.done != true) ({ vm.nav(Screen.AuctionScr) }) else if (s.phase == "PRESEASON" && !s.auctionDoneThisSeason) ({ vm.startMiniAuction() }) else null
        Schedule.TRANSFER -> ({ vm.nav(Screen.Transfers) })
        Schedule.TRAINING -> ({ vm.nav(Screen.MyClub) })
        else -> if (e.comp != null && s.tournament?.id != e.comp.id) ({ vm.nav(Screen.Table) }) else if (e.comp != null) ({ vm.nav(Screen.TournamentsScr) }) else null
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(shape).background(col.copy(alpha = 0.10f)).border(1.dp, col.copy(alpha = 0.35f), shape)
        .then(if (action != null) Modifier.clickable { GameAudio.click(); action() } else Modifier).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(col)); Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val range = if (e.endDate != e.date) "${e.date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} – ${e.endDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} · " else ""
            Text(range + e.sub, color = C.Sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MiniTag(Schedule.catLabel(e.cat).uppercase(), col)
        if (action != null) Icon(Icons.Filled.KeyboardArrowRight, null, tint = C.Sub, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun UpcomingRow(s: CareerSave, e: Schedule.Entry, onClick: () -> Unit) {
    val f = e.fx!!; val h = s.clubs[f.home]; val a = s.clubs[f.away]
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x14FFFFFF)).clickable { GameAudio.click(); onClick() }
        .padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(e.date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)).uppercase(), color = if (e.mine) C.Gold else C.Sub, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(46.dp))
        TeamFlag(h, 20.dp); Spacer(Modifier.width(4.dp))
        Text("${h?.short} v ${a?.short}", color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
        TeamFlag(a, 20.dp); Spacer(Modifier.width(6.dp))
        Box(Modifier.size(6.dp).clip(CircleShape).background(calColor(e.cats.first())))
    }
}

/* ---------------- match preview (mini match centre for any fixture) ---------------- */

@Composable
private fun MatchPreviewDialog(vm: GameVM, s: CareerSave, e: Schedule.Entry, onClose: () -> Unit, onPlay: () -> Unit, onSim: () -> Unit) {
    val c = e.comp!!; val f = e.fx!!
    val h = s.clubs[f.home]; val a = s.clubs[f.away]
    AlertDialog(onDismissRequest = onClose, containerColor = Navy,
        title = {
            Column {
                Text("MATCH CENTRE", color = C.Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("${c.name} · ${f.stage}", color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PreviewSide(s, c, h, Modifier.weight(1f))
                    Text("VS", fontFamily = Display, color = C.Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                    PreviewSide(s, c, a, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text("${Schedule.formatLabel(c, f)} · ${e.date.format(DATE_FMT)} · ${Schedule.startTime(s, c, f)} local", color = C.Sub, fontSize = 11.sp)
                Text(Schedule.venue(s, f), color = C.Sub, fontSize = 11.sp)
                val h2h = c.fixtures.filter { it.played && setOf(it.home, it.away) == setOf(f.home, f.away) }
                if (h2h.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text("Earlier this season: " + h2h.joinToString(" · ") { it.res?.text ?: "" }, color = C.Text, fontSize = 11.sp) }
                Spacer(Modifier.height(6.dp))
                Text(Schedule.DISCLAIMER, color = C.Sub.copy(alpha = 0.7f), fontSize = 9.sp)
            }
        },
        confirmButton = {
            Row {
                if (e.mine) { CardBtn("Play", gold = true, enabled = !vm.busy, icon = true) { onPlay() }; Spacer(Modifier.width(6.dp)) }
                CardBtn("Simulate", enabled = !vm.busy) { onSim() }
            }
        },
        dismissButton = { CardBtn("Close", ghost = true) { onClose() } })
}

@Composable
private fun PreviewSide(s: CareerSave, c: Competition, club: Club?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamFlag(club, 44.dp)
        Text(club?.name ?: "?", color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        // form in this competition (last 5)
        val form = c.fixtures.filter { it.played && (it.home == club?.id || it.away == club?.id) }.sortedBy { it.day }.takeLast(5)
        Row {
            if (form.isEmpty()) Text("No games yet", color = C.Sub, fontSize = 9.sp)
            for (fx in form) {
                val r = fx.res; val ch = when { r == null -> "-"; r.noResult || r.draw -> "D"; r.winner == club?.id -> "W"; else -> "L" }
                Box(Modifier.padding(1.dp).size(14.dp).clip(RoundedCornerShape(3.dp)).background(when (ch) { "W" -> C.Positive; "L" -> C.Red; else -> Color(0xFF64748B) }), contentAlignment = Alignment.Center) {
                    Text(ch, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        val stars = (club?.let { cl -> (cl.xi.ifEmpty { cl.squad }).mapNotNull { s.players[it] } } ?: emptyList()).sortedByDescending { it.overall() }.take(2)
        for (p in stars) Text("${p.name} · ${p.overall()}", color = C.Sub, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/* ---------------- small premium controls ---------------- */

@Composable
fun CalAction(text: String, color: Color?, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.height(32.dp).clip(shape).background(if (!enabled) Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF334155))) else if (color != null) Brush.horizontalGradient(listOf(color, color)) else C.GoldBrush)
        .border(1.dp, if (color != null) Color(0x55FFFFFF) else Color.Transparent, shape)
        .clickable(enabled = enabled) { GameAudio.click(); onClick() }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.PlayArrow, null, tint = if (color != null) Color.White else Color(0xFF1A1200), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(text.uppercase(), color = if (color != null) Color.White else Color(0xFF1A1200), fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, maxLines = 1)
    }
}

@Composable
private fun CardBtn(text: String, gold: Boolean = false, ghost: Boolean = false, enabled: Boolean = true, icon: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(7.dp)
    val bg = when { !enabled -> Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF334155))); gold -> C.GoldBrush; ghost -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)); else -> C.BlueBrush }
    val fg = if (gold) Color(0xFF1A1200) else Color.White
    Row(Modifier.heightIn(min = 32.dp).clip(shape).background(bg).border(1.dp, if (ghost) C.Gold.copy(alpha = 0.5f) else Color.Transparent, shape)
        .clickable(enabled = enabled) { GameAudio.click(); onClick() }.padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon) { Icon(Icons.Filled.PlayArrow, null, tint = fg, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(2.dp)) }
        Text(text.uppercase(), color = if (ghost) C.GoldLight else fg, fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, maxLines = 1)
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0x22E9B949)).border(1.dp, GoldLine, CircleShape).clickable { GameAudio.click(); onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = C.GoldLight, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SmallPill(text: String, color: Color, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(Modifier.heightIn(min = 28.dp).clip(shape).background(if (filled) color else color.copy(alpha = 0.10f)).border(1.dp, color.copy(alpha = 0.7f), shape)
        .clickable { GameAudio.click(); onClick() }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (filled) Color(0xFF1A1200) else color, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1)
    }
}

@Composable
private fun FilterPill(text: String, color: Color, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(Modifier.heightIn(min = 28.dp).clip(shape).background(if (on) color.copy(alpha = 0.22f) else Color(0x0FFFFFFF)).border(1.dp, if (on) color else Color(0x33FFFFFF), shape)
        .clickable { GameAudio.click(); onClick() }.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (on) color else Color(0xFF475569))); Spacer(Modifier.width(5.dp))
        Text(text, color = if (on) C.Text else C.Sub, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun MiniTag(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.16f)).border(0.5.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
        Text(text, color = color, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, maxLines = 1)
    }
}

@Composable
private fun StatusTag(status: String) {
    val col = when (status) { "RESULT" -> C.Sub; "MATCH DAY", "TODAY" -> C.Red; "NO RESULT" -> Color(0xFF94A3B8); else -> Color(0xFF38BDF8) }
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(if (status == "MATCH DAY") C.Red else col.copy(alpha = 0.14f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
        Text(if (status == "MATCH DAY") "● MATCH DAY" else status, color = if (status == "MATCH DAY") Color.White else col, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, maxLines = 1)
    }
}
