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
import androidx.compose.foundation.horizontalScroll
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

fun phaseName(s: CareerSave) = when (s.phase) { "PRESEASON" -> "Pre-season"; "LEAGUE" -> "League stage"; "PLAYOFFS" -> "Playoffs"; "OFFSEASON" -> "Off-season"; else -> s.phase }

fun continueLabel(vm: GameVM): String {
    val s = vm.save ?: return "Continue"
    if (vm.pending != null) return "Go to Match"
    return when (s.phase) { "PRESEASON" -> "Start Season"; "OFFSEASON" -> "Season Review"; else -> "Continue ▶" }
}

