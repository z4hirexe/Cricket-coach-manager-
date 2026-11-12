package com.cricketcoach.manager.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.R
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.game.Modes
import com.cricketcoach.manager.ui.screens.continueLabel

/** Accent palette of the v2.2 "manager office" UI (dark charcoal + neon accents, gold brand). */
object UI {
    val Panel = Color(0xFF0B111C)
    val Side = Color(0xFF080D16)
    val Line = Color(0xFF1C2638)
    val Neon = Color(0xFF4ADE80)
    val NeonDeep = Color(0xFF166534)
    val Cyan = Color(0xFF22D3EE)
    val Amber = Color(0xFFFBBF24)
    val Pink = Color(0xFFEC4899)
    val Violet = Color(0xFF7C5CFF)
    val SimBrush get() = Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Color(0xFF7C5CFF)))
}

/** True when a screen is drawn inside the shell (sidebar + top bar). ScreenScaffold then skips its own top bar. */
val LocalInShell = compositionLocalOf { false }

/** Screens that live inside the career shell. Live match, pre-match and the auction stay full-screen. */
fun Screen.inShell(): Boolean = when (this) {
    Screen.Dashboard, Screen.Inbox, Screen.MyClub, is Screen.Squad, Screen.Database, is Screen.Profile, Screen.Transfers, Screen.Fixtures,
    Screen.Table, Screen.TournamentsScr, Screen.Statistics, Screen.Finances, Screen.SettingsScr, Screen.Selection, Screen.Social, Screen.SeasonSummary, Screen.Calendar -> true
    else -> false
}

private class NavItem(val key: String, val label: String, val glyph: String?, val icon: androidx.compose.ui.graphics.vector.ImageVector?, val badge: Int = 0, val star: Boolean = false, val go: () -> Unit)

private fun selectedKey(s: Screen): String? = when (s) {
    Screen.Dashboard -> "home"; Screen.Inbox -> "inbox"; is Screen.Squad -> "squad"; Screen.Selection -> "selection"; Screen.Transfers -> "transfers"
    Screen.MyClub -> "club"; Screen.Fixtures -> "fixtures"; Screen.Table -> "table"; Screen.TournamentsScr -> "tournaments"; Screen.Statistics -> "stats"; Screen.Calendar -> "calendar"
    Screen.Social -> "social"; Screen.Finances -> "finances"; Screen.Database -> "database"; Screen.SettingsScr -> "settings"; else -> null
}

@Composable
fun GameShell(vm: GameVM, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxSize().background(C.Bg)) {
        Sidebar(vm)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            ShellTopBar(vm)
            Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))) {
                Image(painterResource(R.drawable.stadium_bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.07f)
                CompositionLocalProvider(LocalInShell provides true) { content() }
            }
        }
    }
}

@Composable
private fun Sidebar(vm: GameVM) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val u = s.userClub()
    val club = s.mode == Modes.CLUB
    val sel = selectedKey(vm.screen)
    val I = Icons.Filled
    val main = listOfNotNull(
        NavItem("home", "Home", null, I.Home) { vm.navRoot(Screen.Dashboard) },
        NavItem("inbox", "Inbox", "inbox", null, badge = vm.unreadNews()) { vm.navRoot(Screen.Inbox) },
        NavItem("squad", "Squad & XI", "squad", null) { vm.navRoot(Screen.Squad()) },
        if (Modes.hasTransfers(s)) NavItem("transfers", "Transfers", null, I.ShoppingCart) { vm.navRoot(Screen.Transfers) }
        else NavItem("selection", "Selection", "xi", null) { vm.navRoot(Screen.Selection) },
        if (Modes.hasAuction(s)) NavItem("auction", "Auction", "gavel", null, star = s.auction != null && !s.auction!!.done) {
            if (s.auction == null) vm.startMiniAuction() else { vm.navRoot(Screen.Dashboard); vm.nav(Screen.AuctionScr) }
        } else null,
        NavItem("club", if (club) "My Club" else "Facilities", "stadium", null) { vm.navRoot(Screen.MyClub) },
    )
    val comps = listOf(
        NavItem("calendar", "Calendar", "calendar", null) { vm.navRoot(Screen.Calendar) },
        NavItem("fixtures", "Fixtures", "table", null) { vm.navRoot(Screen.Fixtures) },
        NavItem("table", if (club) "League Table" else "Points Tables", "table", null) { vm.navRoot(Screen.Table) },
        NavItem("tournaments", "Tournaments", "trophy", null) { vm.navRoot(Screen.TournamentsScr) },
        NavItem("stats", "Player Stats", "chart", null) { vm.navRoot(Screen.Statistics) },
    )
    val media = listOf(NavItem("social", "CricGram", "social", null, badge = 0) { vm.navRoot(Screen.Social) })
    val office = listOf(
        NavItem("finances", "Finances", "coins", null) { vm.navRoot(Screen.Finances) },
        NavItem("database", "Player Database", "search", null) { vm.navRoot(Screen.Database) },
        NavItem("settings", "Settings", null, I.Settings) { vm.navRoot(Screen.SettingsScr) },
        NavItem("menu", "Main Menu", "exit", null) { vm.exitToMenu() },
    )
    Column(Modifier.width(196.dp).fillMaxHeight().background(UI.Side)
        .drawBehind { drawLine(UI.Line, Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height), 2f) }
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))) {
        // team header
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamFlag(u, 52.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(u.short, color = C.Text, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1)
                Text(Modes.label(s.mode).uppercase(), color = C.Sub, fontSize = 9.sp, letterSpacing = 0.8.sp, maxLines = 1, softWrap = false)
            }
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(UI.NeonDeep.copy(alpha = 0.55f)).border(1.dp, UI.Neon, RoundedCornerShape(8.dp))
                .clickable { GameAudio.click(); vm.navRoot(Screen.SettingsScr) }, contentAlignment = Alignment.Center) {
                Icon(I.Settings, "Settings", tint = UI.Neon, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = UI.Line, modifier = Modifier.padding(horizontal = 12.dp))
        LazyColumn(Modifier.weight(1f).padding(top = 6.dp)) {
            items(main.size) { i -> NavRow(main[i], main[i].key == sel) }
            item { NavHeader("Competitions") }
            items(comps.size) { i -> NavRow(comps[i], comps[i].key == sel) }
            item { NavHeader("Media") }
            items(media.size) { i -> NavRow(media[i], media[i].key == sel) }
            item { NavHeader("Office") }
            items(office.size) { i -> NavRow(office[i], office[i].key == sel) }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun NavHeader(t: String) =
    Text(t.uppercase(), color = UI.Neon, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))

@Composable
private fun NavRow(n: NavItem, selected: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    val tint = if (selected) Color.White else Color(0xFF9AA6BA)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).height(38.dp).clip(shape)
        .then(if (selected) Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF1F7A3A), Color(0xFF14532D)))).border(1.dp, UI.Neon, shape) else Modifier)
        .clickable { GameAudio.click(); n.go() }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (n.icon != null) Icon(n.icon, null, tint = if (selected) UI.Neon else tint, modifier = Modifier.size(20.dp))
            else Glyph(n.glyph ?: "", if (selected) UI.Neon else tint, 19.dp)
        }
        Spacer(Modifier.width(12.dp))
        Text(n.label.uppercase(), color = tint, fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (n.badge > 0) Box(Modifier.size(20.dp).clip(CircleShape).background(UI.Neon), contentAlignment = Alignment.Center) {
            Text(if (n.badge > 9) "9+" else "${n.badge}", color = Color(0xFF052E16), fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        if (n.star) Icon(Icons.Filled.Star, null, tint = UI.Amber, modifier = Modifier.size(16.dp))
    }
}

/** Season calendar label shown in the top bar. */
fun calendarLabel(s: com.cricketcoach.manager.game.CareerSave): String = when (s.phase) {
    "PRESEASON" -> "PRE-SEASON ${s.year}"
    "OFFSEASON" -> "OFF-SEASON ${s.year}"
    else -> com.cricketcoach.manager.game.Schedule.today(s).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy", java.util.Locale.ENGLISH)).uppercase()
}

@Composable
private fun ShellTopBar(vm: GameVM) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val u = s.userClub()
    Row(Modifier.fillMaxWidth().height(54.dp).background(UI.Panel)
        .drawBehind { drawLine(UI.Line, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 2f) }
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        // manager
        Box(Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(u.primary), Color(0xFF111827)))).border(1.5.dp, C.Gold.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center) {
            Text(s.manager.split(' ').mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(s.manager.uppercase(), color = C.Text, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${u.name.uppercase()} · LVL ${vm.level} · ${vm.coins} COINS", color = C.Sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // music toggle with a little equaliser
        val on = vm.settings.music && !vm.settings.mute
        Box(Modifier.height(36.dp).width(64.dp).clip(RoundedCornerShape(8.dp)).background(if (on) Color(0xFF123322) else Color(0xFF111827)).border(1.dp, if (on) UI.Neon else UI.Line, RoundedCornerShape(8.dp))
            .clickable { vm.toggleMusic() }, contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("♪", color = if (on) UI.Neon else C.Sub, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(5.dp))
                Equalizer(on)
            }
        }
        Spacer(Modifier.width(10.dp))
        // calendar
        Row(Modifier.height(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF111827)).border(1.dp, if (vm.screen == Screen.Calendar) C.Gold else UI.Line, RoundedCornerShape(8.dp))
            .clickable { GameAudio.click(); vm.navRoot(Screen.Calendar) }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Glyph("calendar", UI.Neon, 16.dp); Spacer(Modifier.width(6.dp))
            Text(calendarLabel(s), color = C.Text, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        // simulate / continue
        val inSeason = vm.pending == null && (s.phase == "LEAGUE" || s.phase == "PLAYOFFS")
        val onCal = vm.screen == Screen.Calendar
        // SIMULATE opens the cricket calendar (schedule centre); on the calendar it advances one day.
        // Always: SIMULATE opens the calendar first; on the calendar the same button runs the next step.
        val text = if (onCal) calendarStepLabel(vm) else "Simulate"
        Row(Modifier.height(38.dp).clip(RoundedCornerShape(9.dp)).background(if (vm.busy) Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF334155))) else UI.SimBrush)
            .clickable(enabled = !vm.busy) { GameAudio.click(); if (!onCal) vm.navRoot(Screen.Calendar) else vm.continueAction() }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!onCal) Glyph("calendar", Color.White, 16.dp) else Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Text(text.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp, maxLines = 1)
        }
        if (inSeason && !onCal) {
            // quick one-day advance (the classic SIMULATE behaviour) stays one tap away
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF1E293B)).border(1.dp, Color(0xFF3B82F6), RoundedCornerShape(9.dp))
                .clickable(enabled = !vm.busy) { GameAudio.click(); vm.continueAction() }, contentAlignment = Alignment.Center) {
                Text("▶▶", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
            }
        }
    }
}

@Composable
private fun Equalizer(on: Boolean) {
    val tr = rememberInfiniteTransition(label = "eq")
    val ph by tr.animateFloat(0f, 6.283f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "eqp")
    val cols = listOf(UI.Cyan, Color(0xFF60A5FA), UI.Violet, UI.Pink, UI.Amber)
    Canvas(Modifier.size(26.dp, 16.dp)) {
        val bw = size.width / 9f
        for (i in 0 until 5) {
            val v = if (on) 0.35f + 0.65f * (0.5f + 0.5f * kotlin.math.sin(ph + i * 1.3f)) else 0.25f
            val h = size.height * v
            drawRoundRect(if (on) cols[i] else Color(0xFF475569), Offset(i * bw * 1.9f, size.height - h), androidx.compose.ui.geometry.Size(bw, h), androidx.compose.ui.geometry.CornerRadius(bw / 2))
        }
    }
}

/** Slim title row used by ScreenScaffold inside the shell. */
@Composable
fun ShellTitleRow(vm: GameVM, title: String, actions: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (vm.stack.size > 2) IconButton(onClick = { GameAudio.click(); vm.back() }, modifier = Modifier.size(34.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Text) }
        else Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), color = C.Gold, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp))
        actions()
    }
}

/** Card frame used across the new dashboard / inbox / social screens. */
@Composable
fun Panel(modifier: Modifier = Modifier, border: Color = UI.Line, glow: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.clip(shape).background(Brush.verticalGradient(listOf(Color(0xF0101826), Color(0xF00B111C)))).border(if (glow) 1.5.dp else 1.dp, border, shape), content = content)
}

@Composable
fun PanelHeader(title: String, color: Color, glyph: String? = null, onMore: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (glyph != null) { Glyph(glyph, color, 16.dp); Spacer(Modifier.width(8.dp)) }
        Text(title.uppercase(), color = color, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f), maxLines = 1)
        if (onMore != null) IconButton(onClick = { GameAudio.click(); onMore() }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.KeyboardArrowRight, "Open", tint = C.Sub)
        }
    }
}
