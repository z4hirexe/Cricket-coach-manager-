package com.cricketcoach.manager.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

private fun freeSlot(vm: GameVM): Int? = (1..3).firstOrNull { sl -> vm.saves.none { it.slot == sl } && vm.slotAllowed(sl) }

/** Starts the new-career flow in the first free slot (or explains why it can't). */
fun startNewCareerFlow(vm: GameVM, target: Screen) {
    val free = freeSlot(vm)
    if (free != null) { pendingSlot = free; vm.nav(target) }
    else { vm.msg("All 3 career slots are used - delete one first."); vm.nav(Screen.Career) }
}

@Composable
fun CareerTypeScreen(vm: GameVM) {
    ScreenScaffold(vm, "New Career • Choose Career Type") {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Sub("Slot $pendingSlot • saved on this device")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxSize()) {
                val icons = mapOf(Modes.INTL to Icons.Filled.Star, Modes.DOMESTIC to Icons.Filled.Place, Modes.IPL to Icons.Filled.ShoppingCart, Modes.CLUB to Icons.Filled.Create)
                Modes.TYPES.forEachIndexed { i, t ->
                    CareerTypeCard(t, icons[t.id] ?: Icons.Filled.Star, Modifier.weight(1f).fillMaxHeight().padding(start = if (i > 0) 8.dp else 0.dp)) {
                        GameAudio.click()
                        if (t.id == Modes.CLUB) vm.replaceTop(Screen.NewTeam) else vm.nav(Screen.ChooseTeam(t.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun CareerTypeCard(t: CareerTypeDef, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF16306B), Color(0xFF0A1530))))
        .border(1.dp, Brush.verticalGradient(listOf(C.Gold, Color(0x33E9B949))), RoundedCornerShape(14.dp))
        .clickable(onClick = onClick).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(C.GoldBrush), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color(0xFF1A1200), modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(t.title.uppercase(), color = C.Gold, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
        Text(t.sub, color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(t.desc, color = C.Sub, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(C.GoldBrush).padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
            Text("SELECT", color = Color(0xFF1A1200), fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp)
        }
    }
}

private data class TeamInfo(val id: String, val name: String, val short: String, val primary: Long, val secondary: Long, val venue: String, val note: String, val realPlayers: Int)

private fun teamInfos(mode: String): List<TeamInfo> = when (mode) {
    Modes.DOMESTIC -> {
        val counts = Repo.domestic.values.groupingBy { it.lowercase() }.eachCount()
        Modes.STATES.map { TeamInfo(it.id, it.name, it.short, it.primary, it.secondary, it.venue, "${it.zone} Zone", counts[it.name.lowercase()] ?: 0) }
    }
    Modes.IPL -> {
        val latest = Repo.ds.players.mapNotNull { it.iplLast?.take(4) }.maxOrNull() ?: "2026"
        Consts.IPL.map { f -> TeamInfo(f.id, f.name, f.short, f.primary, f.secondary, Tournaments.venueOf(f.id) ?: "", "Real $latest roster", Repo.ds.players.count { it.ipl == f.name && it.iplLast?.startsWith(latest) == true }) }
    }
    else -> Consts.NATIONS.map { n -> TeamInfo(n.id, n.name, n.short, n.primary, n.secondary, Modes.NATION_VENUES[n.id] ?: "", n.country, Repo.ds.players.count { it.country == n.country }) }
}

@Composable
fun ChooseTeamScreen(vm: GameVM, mode: String) {
    val teams = remember(mode) { teamInfos(mode) }
    var sel by remember { mutableStateOf(teams.firstOrNull { it.short == "BEN" || it.short == "IND" || it.short == "KKR" } ?: teams.first()) }
    var manager by remember { mutableStateOf("") }
    val title = when (mode) { Modes.INTL -> "International • Choose Country"; Modes.DOMESTIC -> "Domestic • Choose State Team"; else -> "IPL • Choose Franchise" }
    ScreenScaffold(vm, title) {
        Row(Modifier.fillMaxSize().padding(10.dp)) {
            LazyVerticalGrid(GridCells.Adaptive(118.dp), Modifier.weight(1.6f).fillMaxHeight()) {
                items(teams, key = { it.id }) { t ->
                    val on = t.id == sel.id
                    Column(Modifier.padding(3.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (on) Brush.verticalGradient(listOf(Color(0xFF2A4A8F), Color(0xFF14295A))) else Brush.verticalGradient(listOf(C.Card, C.Surface)))
                        .border(if (on) 2.dp else 1.dp, if (on) C.Gold else Color(0x33E9B949), RoundedCornerShape(10.dp))
                        .clickable { GameAudio.click(); sel = t }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Badge(t.short, t.primary, t.secondary, (t.id.hashCode() and 7), 40.dp)
                        Text(t.name, color = if (on) C.Gold else C.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
                        Text(if (mode == Modes.DOMESTIC) "${t.realPlayers} real player${if (t.realPlayers == 1) "" else "s"}" else t.note, color = C.Sub, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 10.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(Color(0xF2122453), Color(0xF20A1530)))).border(1.dp, C.Gold.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .verticalScroll(rememberScrollState()).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Badge(sel.short, sel.primary, sel.secondary, (sel.id.hashCode() and 7), 76.dp)
                Spacer(Modifier.height(4.dp))
                Text(sel.name, color = C.Gold, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Sub("Home: ${sel.venue}")
                Sub(when (mode) {
                    Modes.DOMESTIC -> "${sel.note} • ${sel.realPlayers} real players at their current state side (Wikipedia), squad completed with FICTIONAL domestic players"
                    Modes.IPL -> "${sel.realPlayers} players from the real latest-season roster (Cricsheet)"
                    else -> "${sel.realPlayers} real ${sel.note} players in the database"
                })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(manager, { manager = it.take(24) }, label = { Text("Head coach name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(when (mode) {
                    Modes.DOMESTIC -> "Season: Duleep Trophy → Ranji Trophy → Syed Mushtaq Ali Trophy → Vijay Hazare Trophy → Irani Cup"
                    Modes.IPL -> "Season: 70-match league (real 2-group format) → Qualifier 1 / Eliminator / Qualifier 2 / Final • mini auction every year, mega auction every 3rd"
                    else -> "Season: home Test series, away ODI series, T20I series + the ICC event of the year"
                }, color = C.Text, fontSize = 11.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                GoldButton("START CAREER", Modifier.fillMaxWidth(), enabled = !vm.busy) { vm.newModeCareer(pendingSlot, manager, mode, sel.id) }
            }
        }
    }
}

/** New Team builder: name, badge (built-in or uploaded), colours, kit, home stadium, budget, squad setup. */
@Composable
fun NewTeamScreen(vm: GameVM) {
    val ctx = LocalContext.current
    var manager by remember { mutableStateOf("") }
    var club by remember { mutableStateOf("") }
    var short by remember { mutableStateOf("") }
    var stadium by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("India") }
    var colorIdx by remember { mutableIntStateOf(0) }
    var logo by remember { mutableIntStateOf(0) }
    var logoFile by remember { mutableStateOf("") }
    var jersey by remember { mutableIntStateOf(0) }
    var setup by remember { mutableStateOf("SQUAD") }
    var boost by remember { mutableStateOf(false) }
    val colors = Consts.CLUB_COLORS
    val (pc, sc) = colors[colorIdx]
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val raw = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            val jpg = raw?.let { squareJpeg(it, 256, 90) }
            if (jpg == null) vm.msg("Could not read that image") else {
                val dir = File(ctx.filesDir, "team_logos").apply { mkdirs() }
                val f = File(dir, "logo_${System.currentTimeMillis()}.jpg"); f.writeBytes(jpg); logoFile = f.absolutePath
            }
        }
    }
    val preview = Club(id = "preview", name = club.ifBlank { "Your Club" }, short = short.ifBlank { "NEW" }, primary = pc, secondary = sc, logo = logo, logoFile = logoFile)
    ScreenScaffold(vm, "New Team") {
        Row(Modifier.fillMaxSize().padding(10.dp)) {
            Column(Modifier.weight(0.9f).fillMaxHeight().clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(Color(pc).copy(alpha = 0.55f), Color(0xF20A1530)))).border(1.dp, C.Gold.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .verticalScroll(rememberScrollState()).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ClubBadge(preview, 92.dp)
                Spacer(Modifier.height(6.dp))
                Text(preview.name, color = C.GoldLight, fontWeight = FontWeight.Black, fontSize = 19.sp, textAlign = TextAlign.Center)
                Sub("${preview.short} • ${country}")
                Spacer(Modifier.height(6.dp))
                JerseyPreview(pc, sc, jersey, 96.dp)
                Spacer(Modifier.height(6.dp))
                StadiumGraphic(12000, pc, Modifier.fillMaxWidth().height(70.dp))
                Sub(stadium.ifBlank { "${club.ifBlank { "Your Club" }} Ground" } + " • 12,000 seats")
                Sub("Slot $pendingSlot • starts in Division 4 • budget ${Money.fmt(25.0 * vm.settings.economy + if (boost) 10.0 * vm.settings.economy else 0.0)}")
            }
            Column(Modifier.weight(1.3f).fillMaxHeight().padding(start = 10.dp).verticalScroll(rememberScrollState())) {
                Row {
                    OutlinedTextField(club, { club = it.take(28) }, label = { Text("Team name") }, singleLine = true, modifier = Modifier.weight(1.4f))
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(short, { short = it.uppercase().filter { c -> c.isLetter() }.take(4) }, label = { Text("Short (2-4)") }, singleLine = true, modifier = Modifier.weight(0.8f))
                }
                Row {
                    OutlinedTextField(manager, { manager = it.take(24) }, label = { Text("Head coach") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(stadium, { stadium = it.take(40) }, label = { Text("Home stadium name") }, singleLine = true, modifier = Modifier.weight(1.2f))
                }
                SectionTitle("Home country (league)")
                FlowRowSimple(Consts.CLUBS.keys.toList(), country) { country = it }
                SectionTitle("Team colours")
                Row(Modifier.fillMaxWidth()) {
                    colors.forEachIndexed { i, (a, b) ->
                        Box(Modifier.padding(2.dp).size(28.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(a), Color(b))))
                            .border(if (i == colorIdx) 2.dp else 0.dp, C.Gold, CircleShape).clickable { colorIdx = i }, contentAlignment = Alignment.Center) { if (i == colorIdx) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
                SectionTitle("Team logo")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    for (i in Consts.LOGO_NAMES.indices) Box(Modifier.padding(2.dp).clip(RoundedCornerShape(8.dp)).background(if (i == logo && logoFile.isEmpty()) C.CardHi else Color.Transparent)
                        .clickable { logo = i; logoFile = "" }.padding(3.dp)) { Badge("", pc, sc, i, 32.dp) }
                    Spacer(Modifier.width(6.dp))
                    OutlineBtn(if (logoFile.isEmpty()) "Upload logo" else "Change upload") { picker.launch("image/*") }
                }
                Sub(if (logoFile.isNotEmpty()) "Custom logo uploaded (kept privately on this phone)" else Consts.LOGO_NAMES[logo])
                SectionTitle("Kit")
                Row {
                    for (i in Consts.JERSEY_NAMES.indices) Box(Modifier.padding(2.dp).clip(RoundedCornerShape(8.dp)).background(if (i == jersey) C.CardHi else Color.Transparent).clickable { jersey = i }.padding(3.dp)) { JerseyPreview(pc, sc, i, 40.dp) }
                }
                SectionTitle("Squad, budget & contracts")
                FlowRowSimple(listOf("SQUAD", "MEGA"), setup, labels = mapOf("SQUAD" to "Ready-made squad", "MEGA" to "Build via Mega Auction")) { setup = it }
                Sub(if (setup == "MEGA") "All squads are dissolved into a mega auction: retain up to 4, then bid live against 31 AI clubs. Contracts: 3 years." else "You receive a Division 4 squad of real domestic/fringe players on 1-3 year contracts; sign more in Transfers.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(boost, { boost = it }, colors = SwitchDefaults.colors(checkedTrackColor = C.Gold))
                    Spacer(Modifier.width(6.dp))
                    Text("Investor boost: +${Money.fmt(10.0 * vm.settings.economy)} budget for 500 coins (you have ${vm.coins})", color = C.Text, fontSize = 12.sp)
                }
                Sub("After creating the team you can enter it in custom tournaments (Tournaments) alongside nations and IPL franchises.")
                Spacer(Modifier.height(10.dp))
                GoldButton("CREATE TEAM & START", Modifier.fillMaxWidth(), enabled = !vm.busy && club.isNotBlank()) {
                    vm.newCareer(pendingSlot, manager, club, short.ifBlank { club.filter { it.isLetter() }.take(3).uppercase() }, country, pc, sc, logo, jersey, setup, stadium, logoFile, boost)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** International call-ups / domestic squad building (career types without a transfer market). */
@Composable
fun SelectionScreen(vm: GameVM) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val u = s.userClub()
    var role by remember { mutableStateOf("ALL") }
    var q by remember { mutableStateOf("") }
    val max = Modes.maxSquad(s)
    val squad = remember(vm.tick) { u.squad.mapNotNull { s.players[it] }.sortedByDescending { it.overall() } }
    val pool = remember(vm.tick, role, q) {
        Modes.selectionPool(s).filter { (role == "ALL" || it.role == role) && (q.isBlank() || it.name.contains(q, true)) }.take(150)
    }
    ScreenScaffold(vm, if (s.mode == Modes.INTL) "National Selection" else "State Squad Selection") {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // squad summary
            DsCard(pad = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        DsTitle("${u.name} squad", size = 17.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(if (s.mode == Modes.DOMESTIC) "Guest professionals from other states: ${s.guestPros}/3 used. Register 18–25 for each series."
                            else "Build a pool of up to $max. Register 18–25 for each series (15 for ICC events) in the Match Centre.",
                            color = DS.Sub, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${squad.size}/$max", color = if (squad.size >= max) DS.Gold else DS.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 25.sp)
                        Text("SELECTED", color = DS.Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                ProgressTrack(squad.size / max.toFloat(), color = if (squad.size >= 18) DS.Good else DS.Gold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (r in listOf("BAT", "WK", "AR", "BOWL")) {
                        Row(verticalAlignment = Alignment.CenterVertically) { RoleTag(r, small = true); Spacer(Modifier.width(4.dp)); Text("${squad.count { it.role == r }}", color = DS.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.weight(1f).fillMaxWidth()) {
                // selected squad
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    SectionLabel("Selected squad", trailing = { Text("${squad.size}", color = DS.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                    if (squad.isEmpty()) EmptyState("squad", "No players selected", "Call up players from the list on the right.")
                    else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(squad, key = { it.id }) { p -> SelectionRow(vm, s, p, selected = true) }
                    }
                }
                Spacer(Modifier.width(10.dp))
                // available players
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    SectionLabel("Available players", trailing = { Text("${pool.size}", color = DS.Sub, fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                    DsSegmented(listOf("ALL" to "All", "BAT" to "Bat", "WK" to "WK", "AR" to "AR", "BOWL" to "Bowl"), role, Modifier.fillMaxWidth()) { role = it }
                    Spacer(Modifier.height(6.dp))
                    SearchBox(q, { q = it }, "Search players…")
                    Spacer(Modifier.height(6.dp))
                    if (pool.isEmpty()) EmptyState("search", "No players match", "Try a different role or search term.")
                    else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(pool, key = { it.id }) { p -> SelectionRow(vm, s, p, selected = false) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionRow(vm: GameVM, s: CareerSave, p: GPlayer, selected: Boolean) {
    val u = s.userClub()
    DsCard(modifier = Modifier.fillMaxWidth(), pad = 8.dp, onClick = { vm.nav(Screen.Profile(p.id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerFace(p.id, p.name, 38.dp)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, color = DS.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (selected && p.id in u.xi) { Spacer(Modifier.width(5.dp)); DsTag("XI", DS.Gold) }
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoleTag(p.role, small = true); Spacer(Modifier.width(5.dp))
                    Text("${p.country} · ${p.age(s.year)}y${if (p.fictional) " · Fictional" else ""}", color = DS.Sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                RatingPill(p.overall())
                Spacer(Modifier.height(5.dp))
                if (selected) DsButton("Drop", kind = DsKind.DANGER, height = 30.dp) { vm.act { Modes.drop(it, p.id) } }
                else DsButton(if (s.mode == Modes.INTL) "Call up" else "Sign", height = 30.dp) { vm.act { Modes.callUp(it, p.id) } }
            }
        }
    }
}

@Suppress("unused")
@Composable
private fun GoldRing(modifier: Modifier) = Canvas(modifier) { drawCircle(C.Gold, style = Stroke(3f), center = Offset(size.width / 2, size.height / 2)) }

@Suppress("unused")
@Composable
private fun LogoImage(path: String, modifier: Modifier) {
    val bmp = remember(path) { runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
    if (bmp != null) Image(bmp, null, modifier, contentScale = ContentScale.Crop)
}

/** Centre-crop + downscale an uploaded image to a square JPEG (team logos). */
private fun squareJpeg(bytes: ByteArray, size: Int, quality: Int = 85): ByteArray? = try {
    val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IllegalArgumentException()
    val side = minOf(src.width, src.height)
    val crop = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val out = Bitmap.createScaledBitmap(crop, size, size, true)
    val bos = ByteArrayOutputStream(); out.compress(Bitmap.CompressFormat.JPEG, quality, bos)
    bos.toByteArray()
} catch (_: Exception) { null }
