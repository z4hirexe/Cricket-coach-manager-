package com.cricketcoach.manager.ui.screens

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.game.Commentary
import com.cricketcoach.manager.ui.*

/** Settings categories (v2.6). Every control below changes real behaviour; nothing is decorative. */
val SETTINGS_CATS = listOf(
    "general" to "General", "language" to "Language", "commentary" to "Commentary Language", "audio" to "Audio", "graphics" to "Graphics",
    "display" to "Display", "match" to "Match Settings", "notifications" to "Notifications", "account" to "Account", "data" to "Data & Save",
)

/** What the device can actually run: Filament 3D needs OpenGL ES 3.0; the High tier needs ES 3.1 and >= 4 GB RAM. */
object DeviceGfx {
    class Caps(val gles: Int, val ramGb: Double, val lowRam: Boolean) {
        val maxTier: Int get() = when { gles < 0x30000 || lowRam -> 0; gles >= 0x30001 && ramGb >= 3.5 -> 2; else -> 1 }
        val glesLabel: String get() = "OpenGL ES ${gles shr 16}.${gles and 0xFFFF}"
    }
    fun caps(ctx: Context): Caps {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return Caps(0x30000, 4.0, false)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val gles = am.deviceConfigurationInfo?.reqGlEsVersion ?: 0x30000
        return Caps(if (gles == 0) 0x30000 else gles, mi.totalMem / 1_073_741_824.0, am.isLowRamDevice)
    }
}

@Composable
fun SettingsScreen(vm: GameVM) {
    var cat by rememberSaveable { mutableStateOf("general") }
    ScreenScaffold(vm, "Settings") {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 600.dp
            if (wide) Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(200.dp).fillMaxHeight().background(DS.Navy2).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    for ((k, l) in SETTINGS_CATS) CatRow(l, k == cat) { cat = k }
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(DS.Line))
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp)) {
                    SettingsPanel(vm, cat)
                    Spacer(Modifier.height(24.dp))
                }
            } else Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((k, l) in SETTINGS_CATS) CatChip(l, k == cat) { cat = k }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SettingsPanel(vm, cat)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun CatRow(label: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 42.dp).semantics { selected = on }.clickable(role = Role.Tab) { GameAudio.click(); onClick() }
        .background(if (on) DS.Navy4 else DS.Navy2), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(22.dp).background(if (on) DS.Gold else DS.Navy2))
        Spacer(Modifier.width(13.dp))
        Text(label, color = if (on) DS.White else DS.Sub, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun CatChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(label, color = if (on) DS.GoldInk else DS.Sub, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) DS.Gold else DS.Navy3).border(1.dp, if (on) DS.Gold else DS.Line, RoundedCornerShape(16.dp))
            .semantics { selected = on }.clickable(role = Role.Tab) { GameAudio.click(); onClick() }.padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable
private fun Group(title: String, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(10.dp))
    DsLabel(title)
    if (note != null) Text(note, color = DS.Muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp))
    Spacer(Modifier.height(6.dp))
    DsCard(Modifier.fillMaxWidth(), pad = 12.dp, content = content)
}

@Composable
private fun RowLabel(title: String, sub: String? = null) {
    Text(title, color = DS.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    if (sub != null) Text(sub, color = DS.Sub, fontSize = 11.sp, lineHeight = 15.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SettingsPanel(vm: GameVM, cat: String) {
    val st = vm.settings
    val title = SETTINGS_CATS.firstOrNull { it.first == cat }?.second ?: ""
    DsTitle(title, size = 22.sp)
    when (cat) {
        "general" -> {
            var economy by remember { mutableFloatStateOf(st.economy) }
            var diff by remember { mutableIntStateOf(st.difficulty) }
            Group("New careers", "Used when you start a new career.") {
                RowLabel("Difficulty", "Board expectations, AI transfer & auction aggression")
                DsSegmented(listOf("0" to "Easy", "1" to "Normal", "2" to "Hard"), diff.toString(), Modifier.fillMaxWidth()) { diff = it.toInt(); st.difficulty = diff; vm.saveSettingsToCloud() }
                Spacer(Modifier.height(12.dp))
                RowLabel("Economy ${"%.1f".format(economy)}×", "Salary caps, prices and revenues")
                Slider(economy, { economy = it; st.economy = it }, valueRange = 0.5f..2f, steps = 14, onValueChangeFinished = { vm.saveSettingsToCloud() },
                    colors = SliderDefaults.colors(thumbColor = DS.Gold, activeTrackColor = DS.Gold, inactiveTrackColor = DS.Line))
            }
            vm.save?.let { s ->
                Group("Current career") {
                    Text("Economy ${"%.1f".format(s.economy)}× • difficulty ${listOf("Easy", "Normal", "Hard")[s.difficulty.coerceIn(0, 2)]} • season ${s.year}", color = DS.Sub, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    DsButton("Apply economy ${"%.1f".format(economy)}× to this career", kind = DsKind.SECONDARY, height = DS.BtnHSmall) { s.economy = economy.toDouble(); vm.persist(); vm.msg("Economy updated") }
                }
            }
        }
        "language" -> {
            var ds by remember { mutableStateOf(st.dateStyle) }
            Group("Interface language", "Menus and screens are in English in this version. Commentary has its own language setting.") {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("English", color = DS.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); DsTag("Active") }
            }
            Group("Date format", "Used by the calendar, fixtures and match reports.") {
                DsSegmented(listOf("IN" to "28 Mar 2026", "US" to "Mar 28, 2026", "ISO" to "2026-03-28"), ds, Modifier.fillMaxWidth()) { ds = it; st.dateStyle = it; vm.saveSettingsToCloud(); vm.bump() }
            }
        }
        "commentary" -> {
            var lang by remember { mutableStateOf(st.comLang) }
            var style by remember { mutableIntStateOf(st.comStyle) }
            var voice by remember { mutableStateOf(st.voice) }
            var expertOn by remember { mutableStateOf(st.comExpert) }
            var overCards by remember { mutableStateOf(st.comOverCards) }
            Group("Language") {
                DsSegmented(listOf("EN" to "English", "HI" to "हिंदी", "HG" to "Hinglish", "MIX" to "EN + HI"), lang, Modifier.fillMaxWidth()) { lang = it; st.comLang = it; vm.saveSettingsToCloud() }
                if (lang != "EN" && !GameAudio.hindiVoiceAvailable()) Text("Hindi voice is not installed on this phone: Hindi lines are shown as text. Install 'Hindi (India)' in Android Settings → Text-to-speech.",
                    color = DS.Muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Group("Style & booth") {
                DsSegmented(listOf("0" to "Classic", "1" to "Excited", "2" to "Analytical"), style.toString(), Modifier.fillMaxWidth()) { style = it.toInt(); st.comStyle = style; vm.saveSettingsToCloud() }
                val booth = Commentary.boothFor(lang, style)
                Text("On air: " + booth.joinToString("  &  ") { "${it.name} (${it.role})" } + if (!expertOn) " - analyst off" else "", color = DS.Gold, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                DsSwitchRow("Expert analyst", "Second voice after wickets, milestones and every few overs", expertOn) { expertOn = it; st.comExpert = it; vm.saveSettingsToCloud() }
                DsSwitchRow("End-of-over cards", "Scoreboard summary in the commentary feed", overCards) { overCards = it; st.comOverCards = it; vm.saveSettingsToCloud() }
                DsSwitchRow("Spoken commentary", "Phone text-to-speech", voice) { voice = it; st.voice = it; GameAudio.refresh(); vm.saveSettingsToCloud() }
                Spacer(Modifier.height(6.dp))
                DsButton("Preview voices", kind = DsKind.SECONDARY, height = DS.BtnHSmall) {
                    GameAudio.speakAs(when (lang) { "HI" -> "छक्का! गेंद सीधी स्टैंड्स में!"; "HG" -> "Kya shot hai! Seedha stands mein chhakka!"; else -> "That's huge! Into the stands for six!" }, if (lang == "HI") "HI" else "EN", booth[0], true)
                    if (expertOn && booth.size > 1) GameAudio.speakAs(when (lang) { "HI", "MIX" -> "बेहतरीन टाइमिंग, कोई ताक़त की ज़रूरत नहीं थी।"; "HG" -> "Perfect timing, power ki zaroorat hi nahi thi."; else -> "Beautiful timing - he barely needed any power." },
                        if (lang == "HI" || lang == "MIX") "HI" else "EN", booth[1], false)
                }
            }
        }
        "audio" -> AudioPanel(vm)
        "graphics" -> {
            val ctx = LocalContext.current
            val caps = remember { DeviceGfx.caps(ctx) }
            var v3d by remember { mutableStateOf(st.view3d) }
            var gfx by remember { mutableIntStateOf(st.gfx.coerceAtMost(caps.maxTier)) }
            var photos by remember { mutableStateOf(st.photos) }
            LaunchedEffect(Unit) { if (st.gfx > caps.maxTier) st.gfx = caps.maxTier }
            Group("Renderer quality", "Only the tiers this device supports are shown (${caps.glesLabel}, ${"%.1f".format(caps.ramGb)} GB RAM).") {
                val tiers = listOf("0" to "Low", "1" to "Medium", "2" to "High").take(caps.maxTier + 1)
                if (tiers.size > 1) DsSegmented(tiers, gfx.toString(), Modifier.fillMaxWidth()) { gfx = it.toInt(); st.gfx = gfx; vm.saveSettingsToCloud() }
                else Text("Low (this device does not support the 3D renderer)", color = DS.White, fontSize = 14.sp)
                Text(when (gfx) { 0 -> "Low: lightweight 2.5D renderer - fastest, lowest battery."; 1 -> "Medium: real-time 3D (Filament), soft shadows, dynamic resolution."
                    else -> "High: 3D with 4x MSAA, ambient occlusion, high-res shadows and floodlight bloom." }, color = DS.Sub, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Group("Match view") {
                DsSwitchRow("3D match view", if (gfx == 0) "Needs Medium or High quality" else "Animated 3D stadium in the Match Centre", v3d && gfx > 0, enabled = gfx > 0) { v3d = it; st.view3d = it; vm.saveSettingsToCloud() }
            }
            Group("Player photos") {
                DsSwitchRow("Download real player photos", "Wikimedia Commons, needs internet once, cached offline", photos) { photos = it; st.photos = it; Faces.enabled = it; vm.saveSettingsToCloud() }
            }
        }
        "display" -> {
            var imm by remember { mutableStateOf(st.immersive) }
            var keep by remember { mutableStateOf(st.keepScreenOn) }
            var scale by remember { mutableIntStateOf(st.uiScale) }
            Group("Screen") {
                DsSwitchRow("Fullscreen", "Hide the status and navigation bars (swipe from the edge to reveal)", imm) { imm = it; st.immersive = it; vm.bump(); vm.saveSettingsToCloud() }
                DsSwitchRow("Keep screen on during matches", "The display stays awake while a live match is open", keep) { keep = it; st.keepScreenOn = it; vm.bump(); vm.saveSettingsToCloud() }
            }
            Group("Text size", "Scales all interface text.") {
                DsSegmented(listOf("0" to "Compact", "1" to "Standard", "2" to "Large"), scale.toString(), Modifier.fillMaxWidth()) { scale = it.toInt(); st.uiScale = scale; vm.bump(); vm.saveSettingsToCloud() }
            }
        }
        "match" -> {
            var speed by remember { mutableFloatStateOf(st.simSpeedMs.toFloat()) }
            var play by remember { mutableIntStateOf(st.playSpeed) }
            var wk by remember { mutableStateOf(st.autoPauseWicket) }
            Group("Live match") {
                RowLabel("Ball delay ${speed.toInt()} ms", "Pause between deliveries when playing ball by ball / auto")
                Slider(speed, { speed = it; st.simSpeedMs = it.toInt() }, valueRange = 60f..1200f, onValueChangeFinished = { vm.saveSettingsToCloud() },
                    colors = SliderDefaults.colors(thumbColor = DS.Gold, activeTrackColor = DS.Gold, inactiveTrackColor = DS.Line))
                Spacer(Modifier.height(6.dp))
                RowLabel("Default playback speed", "3D animation and simulation both follow it")
                DsSegmented(listOf(1, 2, 4, 6, 8).map { it.toString() to "${it}×" }, play.toString(), Modifier.fillMaxWidth()) { play = it.toInt(); st.playSpeed = play; vm.saveSettingsToCloud() }
                Spacer(Modifier.height(6.dp))
                DsSwitchRow("Pause auto-play after a wicket", "Stops Auto when a wicket falls so you can change plans", wk) { wk = it; st.autoPauseWicket = it; vm.saveSettingsToCloud() }
            }
        }
        "notifications" -> {
            var res by remember { mutableStateOf(st.notifyResults) }
            var soc by remember { mutableStateOf(st.notifySocial) }
            Group("In-game notifications", "Shown inside the game only; no system notifications are sent.") {
                DsSwitchRow("Result pop-ups", "Show your result after simulated days", res) { res = it; st.notifyResults = it; vm.saveSettingsToCloud() }
                DsSwitchRow("Social badges", "Unread counters for Yorker in the sidebar", soc) { soc = it; st.notifySocial = it; vm.bump(); vm.saveSettingsToCloud() }
            }
        }
        "account" -> Group("Account & cloud") {
            if (vm.signedIn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(vm.avatar, vm.profile?.username ?: vm.email, 44.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(vm.profile?.displayName?.ifBlank { null } ?: vm.profile?.username ?: "Account", color = DS.White, fontWeight = FontWeight.Bold)
                        Text("@${vm.profile?.username ?: "…"} • ${vm.email}", color = DS.Sub, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                DsButton("My account", kind = DsKind.SECONDARY, height = DS.BtnHSmall) { vm.nav(Screen.Account) }
            } else {
                Text("Playing as guest", color = DS.White, fontWeight = FontWeight.Bold)
                Text("Guest progress stays on this phone (1 slot). A free account gives 3 permanent career slots, cloud save and a profile photo.", color = DS.Sub, fontSize = 12.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DsButton("Log in", height = DS.BtnHSmall) { vm.nav(Screen.Login) }
                    DsButton("Create account", kind = DsKind.SECONDARY, height = DS.BtnHSmall) { vm.nav(Screen.SignUp) }
                }
            }
        }
        "data" -> {
            var autosave by remember { mutableStateOf(st.autosave) }
            Group("Saving") {
                DsSwitchRow("Autosave", "After every match and action", autosave) { autosave = it; st.autosave = it }
                vm.save?.let { s -> Spacer(Modifier.height(6.dp)); DsButton("Save now (slot ${s.slot})", height = DS.BtnHSmall) { vm.manualSave() } }
            }
            if (vm.signedIn) Group("Cloud") {
                Text("Status: ${vm.syncStatus.ifEmpty { "up to date" }} • last sync ${st.lastSync}", color = DS.Sub, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                DsButton("Sync now", kind = DsKind.SECONDARY, height = DS.BtnHSmall) { vm.syncInBackground() }
            }
            Group("Career slots", "Load, delete or restore careers. Old saves from earlier versions are upgraded automatically and keep their season year.") {
                DsButton("Manage career slots", kind = DsKind.SECONDARY, height = DS.BtnHSmall) { vm.nav(Screen.Career) }
            }
            Group("Data sources") {
                Text("Player data, schedule sources and credits are listed in About.", color = DS.Sub, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                DsButton("Open About", kind = DsKind.GHOST, height = DS.BtnHSmall) { vm.nav(Screen.About) }
            }
        }
    }
}

/** Audio: master + five channels, each with its own slider and mute; all routed through GameAudio.mix(). */
@Composable
private fun AudioPanel(vm: GameVM) {
    val st = vm.settings
    var master by remember { mutableStateOf(st.mute) }
    @Composable fun ch(label: String, get: () -> Float, set: (Float) -> Unit, muted: () -> Boolean, setMute: (Boolean) -> Unit, test: () -> Unit) {
        var v by remember { mutableFloatStateOf(get()) }
        var m by remember { mutableStateOf(muted()) }
        DsVolumeRow(label, v, m, enabled = !master, onValue = { v = it; set(it); GameAudio.refresh() }, onDone = { test(); vm.saveSettingsToCloud() },
            onMute = { m = it; setMute(it); GameAudio.refresh(); vm.saveSettingsToCloud() })
    }
    Group("Mixer", "Music is controlled only here.") {
        ch("Master", { st.volume }, { st.volume = it }, { master }, { master = it; st.mute = it }) { GameAudio.play("bat") }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DS.Line))
        ch("Commentary", { st.volCommentary }, { st.volCommentary = it }, { st.muteCom }, { st.muteCom = it }) {
            GameAudio.speak(if (st.comLang == "HI") "क्या शानदार शॉट है!" else "What a shot that is!", if (st.comLang == "HI") "HI" else "EN") }
        ch("Crowd", { st.volCrowd }, { st.volCrowd = it }, { st.muteCrowd }, { st.muteCrowd = it }) { GameAudio.play("cheer") }
        ch("Bat & Ball", { st.volBat }, { st.volBat = it }, { st.muteBat }, { st.muteBat = it }) { GameAudio.play("batbig") }
        ch("Music", { st.volMusic }, { st.volMusic = it }, { st.muteMusic }, { st.muteMusic = it; if (!it) st.music = true }) { }
        ch("Menu SFX", { st.volUi }, { st.volUi = it }, { st.muteUi }, { st.muteUi = it }) { GameAudio.play("gavel") }
    }
    var react by remember { mutableStateOf(st.crowdReact) }
    Group("Atmosphere") {
        DsSwitchRow("Dynamic crowd", "Chants after boundaries, dhol & bugle, a nervous hush in tight finishes", react, enabled = !master) {
            react = it; st.crowdReact = it; vm.saveSettingsToCloud(); if (it) { GameAudio.play("roar"); GameAudio.playLater(650, "dhol", 0.75f) } }
        Text("Stadium ambience follows the Crowd channel.", color = DS.Muted, fontSize = 11.sp)
    }
    Group("Sound test") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Six" to { GameAudio.onBat("SIX"); GameAudio.playLater(250, "roar"); GameAudio.playLater(900, "dhol", 0.75f) },
                "Four" to { GameAudio.onBat("FOUR"); GameAudio.playLater(250, "cheer"); GameAudio.playLater(750, "bugle", 0.55f) },
                "Wicket" to { GameAudio.play("stumps"); GameAudio.playLater(120, "wicket") },
                "Appeal" to { GameAudio.play("pad"); GameAudio.playLater(150, "appeal") },
                "SOLD!" to { GameAudio.auctionSold(true) }).forEach { (l, f) -> DsButton(l, kind = DsKind.GHOST, height = DS.BtnHSmall, enabled = !master) { f() } }
        }
    }
}
