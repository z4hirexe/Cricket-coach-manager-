package com.cricketcoach.manager.ui.screens

import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.animation.core.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.cricketcoach.manager.render3d.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*
import com.cricketcoach.manager.audio.GameAudio
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class MenuEntry(val title: String, val sub: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tag: String? = null, val needsCareer: Boolean = false, val go: () -> Unit)

@Composable
fun MainMenu(vm: GameVM) {
    val last = vm.saves.firstOrNull { it.slot == vm.settings.lastSlot } ?: vm.saves.maxByOrNull { it.updated }
    val hasCareer = vm.save != null
    val I = androidx.compose.material.icons.Icons.Filled
    fun needCareer(go: () -> Unit): () -> Unit = { if (vm.save != null) go() else vm.msg(if (last != null) "Load your career first - tap CONTINUE CAREER." else "Start a New Career first.") }
    val entries = listOf(
        MenuEntry("NEW CAREER", "International • Domestic • IPL", I.Add) { startNewCareerFlow(vm, Screen.CareerType) },
        MenuEntry("NEW TEAM", "Name, logo, colours, stadium", I.Create) { startNewCareerFlow(vm, Screen.NewTeam) },
        MenuEntry("SQUAD", "Players, roles & XI", I.Person, needsCareer = true, go = needCareer { vm.nav(Screen.Squad()) }),
        MenuEntry("PLAYER DATABASE", "${Repo.ds.players.size} real players", I.Search) { vm.nav(Screen.Database) },
        MenuEntry("TOURNAMENTS", "Leagues, cups & world events", I.Star, needsCareer = true, go = needCareer { vm.nav(Screen.TournamentsScr) }),
        MenuEntry("AUCTION", "IPL-style bidding", I.ShoppingCart, needsCareer = true, go = needCareer { if (Modes.hasAuction(vm.save!!)) vm.nav(Screen.AuctionScr) else vm.msg("${Modes.label(vm.save!!.mode)} careers have no auction - use Squad Selection.") }),
        MenuEntry("TRANSFERS", "Buy, sell & contracts", I.Refresh, needsCareer = true, go = needCareer { vm.nav(if (Modes.hasTransfers(vm.save!!)) Screen.Transfers else Screen.Selection) }),
        MenuEntry("SOCIAL MEDIA", "Feed, likes & followers", I.ThumbUp) { vm.nav(Screen.Social) },
        MenuEntry("STATISTICS", "Records & leaderboards", I.List, needsCareer = true, go = needCareer { vm.nav(Screen.Statistics) }),
        MenuEntry("SETTINGS", "Graphics • sound • data", I.Settings) { vm.nav(Screen.SettingsScr) },
    )
    val zoom = rememberInfiniteTransition(label = "kb")
    val k by zoom.animateFloat(1f, 1.07f, infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse), label = "kbz")
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true; vm.refreshSaves() }
    BoxWithConstraints(Modifier.fillMaxSize().background(C.Bg)) {
        val compact = maxHeight < 340.dp
        // fit logo + continue + 5 tile rows into the screen height without scrolling
        val logoSize = (maxHeight * 0.22f).coerceIn(64.dp, 130.dp)
        val tileH = ((maxHeight - logoSize - 50.dp - 16.dp - 4.dp - 6.dp - 30.dp) / 5).coerceIn(36.dp, 56.dp)
        // key art: fictional star + floodlit stadium (slow cinematic push-in)
        Image(painterResource(com.cricketcoach.manager.R.drawable.home_keyart), null, Modifier.fillMaxSize().graphicsLayer { scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.15f, 0.4f) },
            contentScale = ContentScale.Crop, alignment = Alignment.CenterStart)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.30f to Color(0x00050A18), 0.52f to Color(0xB3050A18), 0.75f to Color(0xF0050A18))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.7f to Color(0x00000000), 1f to Color(0xB3000000))))
        // account chip top-left
        Row(Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start)).padding(start = 14.dp, top = 10.dp)
            .clip(RoundedCornerShape(24.dp)).background(Color(0xCC0A1530)).border(1.dp, C.Gold.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .clickable { vm.nav(if (vm.signedIn) Screen.Account else Screen.Login) }.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Avatar(vm.avatar, vm.profile?.username ?: "G", 30.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(if (vm.signedIn) (vm.profile?.displayName?.ifBlank { null } ?: vm.profile?.username ?: vm.email) else "Guest • Log in", color = C.GoldLight, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                Text("Lv ${vm.level} • ${vm.coins} coins", color = C.Sub, fontSize = 10.sp)
            }
        }
        // hero caption bottom-left
        Column(Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start)).padding(start = 20.dp, bottom = 12.dp)) {
            Text("MANAGE  •  COMPETE  •  MAKE HISTORY", color = C.Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp)
            Text("Real teams • ${Repo.ds.players.size} verified players • data ${Repo.ds.meta.built}", color = Color(0x99FFFFFF), fontSize = 9.sp)
        }
        androidx.compose.animation.AnimatedVisibility(shown, Modifier.align(Alignment.CenterEnd),
            enter = androidx.compose.animation.fadeIn(tween(600)) + androidx.compose.animation.slideInHorizontally(tween(600)) { it / 5 }) {
            Column(Modifier.fillMaxHeight().width(this@BoxWithConstraints.maxWidth * 0.5f).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                .verticalScroll(rememberScrollState()).padding(end = 18.dp, top = 8.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(com.cricketcoach.manager.R.drawable.logo_cmp), "Cricket Manager Pro", Modifier.size(logoSize))
                Spacer(Modifier.height(4.dp))
                val contSub = when { vm.save != null -> vm.save!!.userClub().name; last != null -> "${last.club} • Season ${last.season}"; else -> "No saved career yet" }
                PrimaryMenuButton("CONTINUE CAREER", contSub, enabled = hasCareer || last != null) { if (vm.save != null) vm.goHome() else last?.let { vm.loadCareer(it.slot) } }
                Spacer(Modifier.height(6.dp))
                entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEachIndexed { i, e ->
                            MenuTile(e, dim = e.needsCareer && !hasCareer, modifier = Modifier.weight(1f).padding(start = if (i == 1) 6.dp else 0.dp), compact = compact, h = tileH)
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun PrimaryMenuButton(title: String, sub: String, enabled: Boolean, onClick: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "p")
    val shine = rememberInfiniteTransition(label = "shine").animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(2800, delayMillis = 1200), RepeatMode.Restart), label = "s")
    Box(Modifier.fillMaxWidth().height(50.dp).graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.5f }
        .clip(RoundedCornerShape(10.dp)).background(C.GoldBrush)
        .drawWithContent {
            drawContent()
            val x = size.width * shine.value
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color(0x66FFFFFF), Color.Transparent), start = androidx.compose.ui.geometry.Offset(x - 80f, 0f), end = androidx.compose.ui.geometry.Offset(x + 80f, size.height)))
        }
        .clickable(src, indication = rememberRipple(color = Color.White), enabled = enabled) { GameAudio.click(); onClick() }
        .padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(androidx.compose.material.icons.Icons.Filled.PlayArrow, null, tint = Color(0xFF1A1200), modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color(0xFF1A1200), fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 2.sp)
                Text(sub, color = Color(0xCC1A1200), fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MenuTile(e: MenuEntry, dim: Boolean, modifier: Modifier, compact: Boolean, h: androidx.compose.ui.unit.Dp) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "t")
    Row(modifier.height(h).graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(8.dp))
        .background(Brush.horizontalGradient(listOf(Color(0xE6122453), Color(0xCC0A1530))))
        .border(1.dp, Brush.horizontalGradient(listOf(C.Gold.copy(alpha = if (dim) 0.25f else 0.75f), Color(0x22E9B949))), RoundedCornerShape(8.dp))
        .clickable(src, indication = rememberRipple(color = C.Gold)) { GameAudio.click(); e.go() }
        .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(C.GoldBrush), contentAlignment = Alignment.Center) {
            Icon(e.icon, null, tint = Color(0xFF1A1200), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = Color.White.copy(alpha = if (dim) 0.55f else 1f), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.sp, maxLines = 1)
            if (!compact) Text(e.sub, color = Color(0x99C9D6F2), fontSize = 9.sp, maxLines = 1)
        }
        e.tag?.let { Text(it, color = C.Gold, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.border(1.dp, C.Gold.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) }
    }
}

@Composable
fun CareerSlots(vm: GameVM) {
    var confirmDel by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { vm.refreshSaves() }
    ScreenScaffold(vm, "Career Mode") {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Sub(if (vm.signedIn) "Three permanent career slots, saved privately to your account and backed up to the cloud. Progress autosaves after every match and action."
                else "Guest mode: one temporary slot stored only on this phone. Log in or create a free account for 3 permanent cloud-saved slots.")
            if (!vm.signedIn) Row { GoldButton("Log in") { vm.nav(Screen.Login) }; Spacer(Modifier.width(8.dp)); OutlineBtn("Create account") { vm.nav(Screen.SignUp) } }
            for (slot in 1..3) {
                val m = vm.saves.firstOrNull { it.slot == slot }
                CcmCard(highlight = vm.save?.slot == slot) {
                    Text("Slot $slot", color = C.Gold, fontWeight = FontWeight.Bold)
                    if (m == null && !vm.slotAllowed(slot)) {
                        Sub("Locked - requires an account")
                        Spacer(Modifier.height(6.dp))
                        OutlineBtn("Create account to unlock") { vm.nav(Screen.SignUp) }
                    } else if (m == null) {
                        Sub("Empty")
                        Spacer(Modifier.height(6.dp))
                        Row {
                            GreenButton("New Career") { if (vm.slotAllowed(slot)) { pendingSlot = slot; vm.nav(Screen.CareerType) } else vm.nav(Screen.SignUp) }
                            if (vm.signedIn) { Spacer(Modifier.width(8.dp)); OutlineBtn("Restore from cloud") { vm.cloudRestore(slot) } }
                        }
                    } else {
                        Text("${m.club} — ${Modes.label(m.mode)} • Season ${m.season}${if (m.mode == Modes.CLUB) ", Division ${m.division}" else ""}", color = C.Text)
                        Sub("Manager ${m.manager} • saved ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(m.updated))}")
                        Spacer(Modifier.height(6.dp))
                        Row {
                            GoldButton("Load") { vm.loadCareer(slot) }
                            Spacer(Modifier.width(8.dp))
                            OutlineBtn("Delete") { confirmDel = slot }
                        }
                    }
                }
            }
        }
    }
    confirmDel?.let { slot ->
        AlertDialog(onDismissRequest = { confirmDel = null }, containerColor = C.Surface,
            title = { Text("Delete slot $slot?") }, text = { Text(if (vm.signedIn) "This permanently removes the career from this device and from your cloud account." else "This permanently removes the career on this device.") },
            confirmButton = { TextButton(onClick = { vm.deleteSlot(slot); confirmDel = null }) { Text("Delete", color = C.Red) } },
            dismissButton = { TextButton(onClick = { confirmDel = null }) { Text("Cancel") } })
    }
}

var pendingSlot = 1

@Composable
fun NewCareerScreen(vm: GameVM) {
    var manager by remember { mutableStateOf("") }
    var club by remember { mutableStateOf("") }
    var short by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("India") }
    var colorIdx by remember { mutableIntStateOf(0) }
    var logo by remember { mutableIntStateOf(0) }
    var jersey by remember { mutableIntStateOf(0) }
    var setup by remember { mutableStateOf("SQUAD") }
    val colors = Consts.CLUB_COLORS
    val (pc, sc) = colors[colorIdx]
    ScreenScaffold(vm, "Create Your Club") {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(short.ifBlank { "NEW" }, pc, sc, logo, 72.dp)
                Spacer(Modifier.width(16.dp))
                JerseyPreview(pc, sc, jersey, 80.dp)
                Spacer(Modifier.width(12.dp))
                Column { Text(club.ifBlank { "Your Club" }, color = C.Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp); Sub("Slot $pendingSlot • starts in Division 4") }
            }
            OutlinedTextField(manager, { manager = it.take(24) }, label = { Text("Manager name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(club, { club = it.take(28) }, label = { Text("Club name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(short, { short = it.uppercase().filter { c -> c.isLetter() }.take(4) }, label = { Text("Short name (2–4 letters)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SectionTitle("Home country")
            FlowRowSimple(Consts.CLUBS.keys.toList(), country) { country = it }
            SectionTitle("Club colours")
            Row(Modifier.fillMaxWidth()) {
                colors.forEachIndexed { i, (a, b) ->
                    Box(Modifier.padding(2.dp).size(26.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(a), Color(b))))
                        .clickable { colorIdx = i }, contentAlignment = Alignment.Center) { if (i == colorIdx) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            SectionTitle("Logo")
            Row(Modifier.fillMaxWidth()) {
                for (i in Consts.LOGO_NAMES.indices) Box(Modifier.padding(2.dp).clip(RoundedCornerShape(8.dp)).background(if (i == logo) C.CardHi else Color.Transparent).clickable { logo = i }.padding(3.dp)) { Badge("", pc, sc, i, 34.dp) }
            }
            Sub(Consts.LOGO_NAMES[logo])
            SectionTitle("Jersey")
            Row {
                for (i in Consts.JERSEY_NAMES.indices) Box(Modifier.padding(2.dp).clip(RoundedCornerShape(8.dp)).background(if (i == jersey) C.CardHi else Color.Transparent).clickable { jersey = i }.padding(3.dp)) { JerseyPreview(pc, sc, i, 44.dp) }
            }
            Sub(Consts.JERSEY_NAMES[jersey])
            SectionTitle("Squad setup")
            FlowRowSimple(listOf("SQUAD", "MEGA"), setup, labels = mapOf("SQUAD" to "Ready-made squad", "MEGA" to "Build via Mega Auction")) { setup = it }
            Sub(if (setup == "MEGA") "All squads are dissolved into a mega auction: retain up to 4, then bid live against 31 AI clubs." else "You receive a modest Division 4 squad of real domestic/fringe players and can improve it via transfers.")
            Spacer(Modifier.height(16.dp))
            GoldButton("Start Career", Modifier.fillMaxWidth(), enabled = !vm.busy) {
                vm.newCareer(pendingSlot, manager, club, short.ifBlank { club.filter { it.isLetter() }.take(3).uppercase() }, country, pc, sc, logo, jersey, setup)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun FlowRowSimple(items: List<String>, selected: String, labels: Map<String, String> = emptyMap(), onPick: (String) -> Unit) {
    val rows = items.chunked(3)
    Column {
        for (r in rows) Row(Modifier.fillMaxWidth()) {
            for (it in r) FilterChip(selected = it == selected, onClick = { onPick(it) }, label = { Text(labels[it] ?: it, fontSize = 12.sp) },
                modifier = Modifier.padding(end = 6.dp),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.Gold, selectedLabelColor = Color.Black, labelColor = C.Text))
        }
    }
}

@Composable
fun Dashboard(vm: GameVM) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val u = s.userClub()
    val next = s.league.flatMap { c -> c.fixtures.filter { !it.played && (it.home == u.id || it.away == u.id) }.map { c to it } }.minByOrNull { it.second.day }
    val curComp = s.league.filter { it.stage != "DONE" && it.fixtures.any { f -> !f.played } }.minByOrNull { c -> c.fixtures.filter { !it.played }.minOf { it.day } }
    val club = s.mode == Modes.CLUB
    ScreenScaffold(vm, u.name, actions = { TextButton(onClick = { vm.manualSave() }) { Text("SAVE", color = C.Gold) } }) {
        LazyColumn(Modifier.padding(horizontal = 14.dp)) {
            item {
                CcmCard(highlight = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClubBadge(u, 56.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.name, color = C.Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Sub("Coach ${s.manager} • Season ${s.season} (${s.year}) • ${if (club) "Division ${u.division}" else Modes.label(s.mode) + " career"}")
                            Sub("Phase: ${if (!club && curComp != null && s.phase != "PRESEASON" && s.phase != "OFFSEASON") curComp.name else phaseName(s)}${if (s.phase == "LEAGUE" || s.phase == "PLAYOFFS") " • Day ${s.day}" else ""}")
                            Sub("Followers ${"%,d".format(s.followers)} • Coins ${vm.coins} • Level ${vm.level}")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Column(Modifier.weight(1f)) { Sub("Balance"); Text(Money.fmt(u.balance), color = if (u.balance >= 0) C.Green else C.Red, fontWeight = FontWeight.Bold) }
                        if (Modes.hasTransfers(s)) Column(Modifier.weight(1f)) { Sub("Cap room"); Text(Money.fmt(Career.purse(s, u)), color = C.Text, fontWeight = FontWeight.Bold) }
                        else Column(Modifier.weight(1f)) { Sub("Board confidence"); Text("${s.boardRating}%", color = if (s.boardRating < 25) C.Red else C.Text, fontWeight = FontWeight.Bold) }
                        Column(Modifier.weight(1f)) { Sub("Reputation"); Text("${u.reputation}/100", color = C.Text, fontWeight = FontWeight.Bold) }
                        Column(Modifier.weight(1f)) { Sub("Squad"); Text("${u.squad.size}/${Modes.maxSquad(s)}", color = if (u.squad.size < 15) C.Red else C.Text, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            item {
                CcmCard {
                    if (vm.pending != null) {
                        val f = vm.pendingFixture()
                        Text("MATCH DAY", color = C.Gold, fontWeight = FontWeight.Bold)
                        if (f != null) Text("${s.clubs[f.home]?.name} vs ${s.clubs[f.away]?.name}", color = C.Text)
                    } else when (s.phase) {
                        "PRESEASON" -> {
                            Text("PRE-SEASON", color = C.Gold, fontWeight = FontWeight.Bold)
                            Sub(if (s.auction != null && !s.auction!!.done) "The ${s.auction!!.type.lowercase()} auction is waiting." else if (club) "Sign players, choose a sponsor (My Club) and set your XI, then start the season." else "Pick your squad (Selection) and playing XI, then start the season. Coming up: ${s.league.joinToString { it.name.substringBefore(" (") }.take(160)}")
                        }
                        "OFFSEASON" -> { Text("SEASON COMPLETE", color = C.Gold, fontWeight = FontWeight.Bold); Sub("Review the season and move to the next one.") }
                        else -> {
                            Text("NEXT FIXTURE", color = C.Gold, fontWeight = FontWeight.Bold)
                            if (next != null) {
                                val (nc, next) = next
                                Sub(nc.name)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ClubBadge(s.clubs[next.home], 32.dp); Spacer(Modifier.width(6.dp))
                                    Text("${s.clubs[next.home]?.short} vs ${s.clubs[next.away]?.short}", color = C.Text, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(6.dp)); ClubBadge(s.clubs[next.away], 32.dp)
                                    Spacer(Modifier.width(8.dp)); Sub("${next.stage} • Day ${next.day}")
                                }
                            } else Sub("No more fixtures for you — continue to simulate the remaining matches.")
                        }
                    }
                    if (s.lastResult.isNotEmpty()) Sub("Last result: ${s.lastResult}")
                    Spacer(Modifier.height(8.dp))
                    Row {
                        GoldButton(continueLabel(vm), Modifier.weight(1f)) { vm.continueAction() }
                        if (s.phase == "LEAGUE" || s.phase == "PLAYOFFS" || s.phase == "PRESEASON") {
                            Spacer(Modifier.width(8.dp))
                            OutlineBtn("Sim to season end", Modifier.weight(1f), enabled = vm.pending == null) { vm.simToSeasonEnd() }
                        }
                    }
                    if (s.phase == "PRESEASON" && s.auction != null && !s.auction!!.done) { Spacer(Modifier.height(6.dp)); GreenButton("Go to Auction", Modifier.fillMaxWidth()) { vm.nav(Screen.AuctionScr) } }
                    if (s.phase == "PRESEASON" && s.auction == null && !s.auctionDoneThisSeason && Modes.hasAuction(s)) { Spacer(Modifier.height(6.dp)); OutlineBtn("Enter Mini Auction (optional)", Modifier.fillMaxWidth()) { vm.startMiniAuction() } }
                }
            }
            item {
                val tiles = listOfNotNull(
                    "Squad & XI" to Screen.Squad(), (if (club) "My Club" else "Team & Facilities") to Screen.MyClub,
                    if (Modes.hasTransfers(s)) "Transfers" to Screen.Transfers else "Squad Selection" to Screen.Selection,
                    if (Modes.hasAuction(s)) "Auction" to Screen.AuctionScr else null,
                    "Fixtures" to Screen.Fixtures, (if (club) "League Table" else "Points Tables") to Screen.Table, "Tournaments" to Screen.TournamentsScr, "Statistics" to Screen.Statistics,
                    "Social Media" to Screen.Social, "Finances" to Screen.Finances, "Player Database" to Screen.Database, "Settings" to Screen.SettingsScr, "Main Menu" to Screen.Main
                )
                Column {
                    for (row in tiles.chunked(3)) Row(Modifier.fillMaxWidth()) {
                        for ((label, scr) in row) Card(onClick = {
                            when {
                                scr is Screen.Main -> vm.exitToMenu()
                                scr is Screen.AuctionScr && s.auction == null -> vm.startMiniAuction()
                                else -> vm.nav(scr)
                            }
                        }, modifier = Modifier.weight(1f).padding(3.dp).height(64.dp), colors = CardDefaults.cardColors(containerColor = C.Card), shape = RoundedCornerShape(12.dp)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label, color = C.Text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, fontSize = 13.sp) }
                        }
                    }
                }
            }
            item { SectionTitle("News") }
            items(s.news.take(25)) { n -> Text("• $n", color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

fun phaseName(s: CareerSave) = when (s.phase) { "PRESEASON" -> "Pre-season"; "LEAGUE" -> "League stage"; "PLAYOFFS" -> "Playoffs"; "OFFSEASON" -> "Off-season"; else -> s.phase }

fun continueLabel(vm: GameVM): String {
    val s = vm.save ?: return "Continue"
    if (vm.pending != null) return "Go to Match"
    return when (s.phase) { "PRESEASON" -> "Start Season"; "OFFSEASON" -> "Season Review"; else -> "Continue ▶" }
}

@Composable
fun SettingsScreen(vm: GameVM) {
    val st = vm.settings
    var speed by remember { mutableFloatStateOf(st.simSpeedMs.toFloat()) }
    var economy by remember { mutableFloatStateOf(st.economy) }
    var diff by remember { mutableIntStateOf(st.difficulty) }
    var autosave by remember { mutableStateOf(st.autosave) }
    ScreenScaffold(vm, "Settings") {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            SectionTitle("Gameplay")
            Sub("Live match speed: ${speed.toInt()} ms per ball")
            Slider(speed, { speed = it; st.simSpeedMs = it.toInt() }, valueRange = 60f..1200f)
            Sub("Economy multiplier for NEW careers: ${"%.1f".format(economy)}× (salary caps, prices, revenues)")
            Slider(economy, { economy = it; st.economy = it }, valueRange = 0.5f..2f, steps = 14)
            Sub("Difficulty for NEW careers")
            FlowRowSimple(listOf("0", "1", "2"), diff.toString(), mapOf("0" to "Easy", "1" to "Normal", "2" to "Hard")) { diff = it.toInt(); st.difficulty = diff }
            vm.save?.let { s ->
                Spacer(Modifier.height(6.dp))
                Sub("Current career: economy ${"%.1f".format(s.economy)}×, difficulty ${listOf("Easy", "Normal", "Hard")[s.difficulty.coerceIn(0, 2)]}")
                Row {
                    OutlineBtn("Apply economy to this career") { s.economy = economy.toDouble(); vm.persist(); vm.msg("Economy updated") }
                }
            }
            SectionTitle("Sound mixer")
            var mute by remember { mutableStateOf(st.mute) }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(mute, { mute = it; st.mute = it; GameAudio.refresh(); vm.saveSettingsToCloud() }, colors = SwitchDefaults.colors(checkedTrackColor = C.Red)); Spacer(Modifier.width(8.dp)); Text(if (mute) "All sound muted" else "Mute all sound", color = C.Text) }
            @Composable fun mix(label: String, get: () -> Float, set: (Float) -> Unit, test: () -> Unit) {
                var v by remember { mutableFloatStateOf(get()) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = C.Text, fontSize = 13.sp, modifier = Modifier.width(130.dp))
                    Slider(v, { v = it; set(it); GameAudio.refresh() }, valueRange = 0f..1f, enabled = !mute, onValueChangeFinished = { test(); vm.saveSettingsToCloud() }, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = C.Gold, activeTrackColor = C.Gold))
                    Text("${(v * 100).toInt()}%", color = C.Gold, fontSize = 12.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                }
            }
            mix("Master", { st.volume }, { st.volume = it }) { GameAudio.play("bat") }
            mix("Commentary", { st.volCommentary }, { st.volCommentary = it }) { GameAudio.speak(if (st.comLang == "HI") "क्या शानदार शॉट है!" else "What a shot that is!", if (st.comLang == "HI") "HI" else "EN") }
            mix("Crowd", { st.volCrowd }, { st.volCrowd = it }) { GameAudio.play("cheer") }
            mix("Bat & Ball", { st.volBat }, { st.volBat = it }) { GameAudio.play("batbig") }
            mix("Music", { st.volMusic }, { st.volMusic = it }) { }

            SectionTitle("Commentary")
            var lang by remember { mutableStateOf(st.comLang) }
            var style by remember { mutableIntStateOf(st.comStyle) }
            var voice by remember { mutableStateOf(st.voice) }
            Sub("Language")
            FlowRowSimple(listOf("EN", "HI", "MIX"), lang, mapOf("EN" to "English", "HI" to "हिंदी Hindi", "MIX" to "Hindi + English")) { lang = it; st.comLang = it; vm.saveSettingsToCloud() }
            Sub("Style")
            FlowRowSimple(listOf("0", "1", "2"), style.toString(), mapOf("0" to "Classic", "1" to "Excited", "2" to "Analytical")) { style = it.toInt(); st.comStyle = style; vm.saveSettingsToCloud() }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(voice, { voice = it; st.voice = it; GameAudio.refresh() }); Spacer(Modifier.width(8.dp)); Text("Spoken commentary (phone text-to-speech)", color = C.Text) }
            if (lang != "EN" && !GameAudio.hindiVoiceAvailable()) Sub("Hindi voice is not installed on this phone: Hindi commentary is shown as text. To hear it, install 'Hindi (India)' in Android Settings → Text-to-speech → Google → Install voice data.", size = 11)

            SectionTitle("Graphics")
            var v3d by remember { mutableStateOf(st.view3d) }
            var photos by remember { mutableStateOf(st.photos) }
            @Composable fun sw(v: Boolean, label: String, set: (Boolean) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) { Switch(v, { set(it); GameAudio.refresh(); GameAudio.click() }); Spacer(Modifier.width(8.dp)); Text(label, color = C.Text) }
            sw(v3d, "3D match view") { v3d = it; st.view3d = it }
            var gfx by remember { mutableIntStateOf(st.gfx) }
            Sub("Graphics quality")
            Row {
                for ((q, l) in listOf(0 to "Low", 1 to "Medium", 2 to "High")) {
                    val sel = gfx == q
                    SmallBtn(l, if (sel) C.Gold else C.Card, Modifier.weight(1f), dark = sel) { gfx = q; st.gfx = q }
                }
            }
            Sub(when (gfx) { 0 -> "Low: lightweight 2.5D renderer for older phones (fastest, lowest battery)."; 1 -> "Medium: real-time 3D (Filament PBR), soft shadows, dynamic resolution to keep it smooth."; else -> "High: 3D with 4x MSAA, ambient occlusion, high-res shadows and floodlight bloom (flagship phones)." })
            sw(photos, "Download real player photos (Wikimedia Commons, needs internet once; cached for offline)") { photos = it; st.photos = it; Faces.enabled = it }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(autosave, { autosave = it; st.autosave = it }); Spacer(Modifier.width(8.dp)); Text("Autosave after every action", color = C.Text) }
            vm.save?.let { GreenButton("Save now (slot ${it.slot})", Modifier.fillMaxWidth()) { vm.manualSave() } }

            SectionTitle("Account & cloud save")
            CcmCard {
                if (vm.signedIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(vm.avatar, vm.profile?.username ?: vm.email, 44.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(vm.profile?.displayName?.ifBlank { null } ?: vm.profile?.username ?: "Account", color = C.Gold, fontWeight = FontWeight.Bold)
                            Sub("@${vm.profile?.username ?: "…"} • ${vm.email}")
                        }
                        OutlineBtn("My Account") { vm.nav(Screen.Account) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Sub("Careers, coins, progress and these settings are saved privately to your account (only you can read them). Status: ${vm.syncStatus.ifEmpty { "up to date" }} • last sync ${st.lastSync}")
                    Row { OutlineBtn("Sync now") { vm.syncInBackground() } }
                } else {
                    Text("Playing as guest", color = C.Gold, fontWeight = FontWeight.Bold)
                    Sub("Guest progress stays on this phone only (1 slot). Create a free account for 3 permanent career slots, cloud save, profile photo and the online social feed.")
                    Row { GoldButton("Log in") { vm.nav(Screen.Login) }; Spacer(Modifier.width(8.dp)); OutlineBtn("Create account") { vm.nav(Screen.SignUp) } }
                }
            }

            SectionTitle("Data sources")
            CcmCard {
                KV("Players", "${Repo.ds.players.size}")
                KV("Dataset built", Repo.ds.meta.built)
                for ((k, c) in Repo.ds.meta.coverage) KV("Cricsheet $k", "${c.matches} matches • ${c.first} → ${c.last}")
                Spacer(Modifier.height(6.dp))
                Sub(Repo.ds.meta.source)
                if (Repo.ds.meta.note.isNotEmpty()) Sub(Repo.ds.meta.note)
                Spacer(Modifier.height(6.dp))
                Sub("• Official career totals: Wikipedia ‘Infobox cricketer’ (sourced from ESPNcricinfo), with the as-of date shown on each profile.")
                Sub("• Detailed splits (balls, 4s/6s, SR, economy, form): Cricsheet ball-by-ball data (ODC-By 1.0). Partial for older careers, and Afghanistan matches are withheld by Cricsheet.")
                Repo.ds.meta.photoSource.takeIf { it.isNotEmpty() }?.let { Sub("• $it (${Repo.ds.players.count { p -> p.img != null }} players)") }
                Sub("• Uncapped Indian IPL players: country inferred as India (marked on profile).")
                Sub("• Ratings are game abstractions derived from those stats; club names (except real IPL franchises) and youth players are fictional.")
            }
            SectionTitle("About")
            Sub("Cricket Manager Pro v2.0 — original game. Home-screen key art and logo are original AI-generated artwork (fictional player, no real-person likeness, no federation or sponsor logos). All sound effects and music are original, synthesised for this game. Team badges are original artwork; no official logos are used.")
            Spacer(Modifier.height(30.dp))
        }
    }
}
