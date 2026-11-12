package com.cricketcoach.manager.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.R
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.Modes
import com.cricketcoach.manager.game.OfficialSchedule
import com.cricketcoach.manager.game.Yorker
import com.cricketcoach.manager.ui.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* =============================================================================================== main menu */

/**
 * Main menu (v2.6): exactly four entries - NEW GAME, LOAD GAME, SETTINGS, ABOUT - over the floodlit stadium, with the
 * 2027 logo. "Continue career" lives inside New Game / Load Game.
 */
@Composable
fun MainMenu(vm: GameVM) {
    val zoom = rememberInfiniteTransition(label = "kb")
    val k by zoom.animateFloat(1f, 1.06f, infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Reverse), label = "kbz")
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { vm.refreshSaves(); enter.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    val last = vm.saves.firstOrNull { it.slot == vm.settings.lastSlot } ?: vm.saves.maxByOrNull { it.updated }
    BoxWithConstraints(Modifier.fillMaxSize().background(DS.Navy)) {
        val landscape = maxWidth > maxHeight
        val fullW = maxWidth
        Image(painterResource(R.drawable.stadium_bg), null, Modifier.fillMaxSize().graphicsLayer { scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0.5f, 0.35f) },
            contentScale = ContentScale.Crop)
        // controlled navy grade: darker towards the menu side, readable everywhere, no colourful gradients
        Box(Modifier.fillMaxSize().background(Color(0x99060B1A)))
        Box(Modifier.fillMaxSize().background(if (landscape) Brush.horizontalGradient(0f to Color(0x33060B1A), 0.55f to Color(0xB3060B1A), 1f to Color(0xF2060B1A))
            else Brush.verticalGradient(0f to Color(0x33060B1A), 0.45f to Color(0xA6060B1A), 1f to Color(0xF5060B1A))))
        AccountChip(vm, Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start)).padding(start = 14.dp, top = 10.dp))
        Text("v${Brand.VERSION}", color = DS.Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End)).padding(top = 12.dp, end = 16.dp))
        val a = enter.value
        if (landscape) {
            val logo = (maxHeight * 0.62f).coerceIn(120.dp, 300.dp)
            Column(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(maxWidth * 0.52f).padding(start = 28.dp).graphicsLayer { alpha = a },
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                BrandLogo(logo, Modifier.graphicsLayer { val s = 0.94f + 0.06f * a; scaleX = s; scaleY = s })
                Spacer(Modifier.height(6.dp))
                Text(Brand.TAGLINE.uppercase(), color = DS.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, maxLines = 1, softWrap = false)
            }
            Column(Modifier.align(Alignment.CenterEnd).width((maxWidth * 0.36f).coerceIn(250.dp, 340.dp)).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                .padding(end = 28.dp).graphicsLayer { alpha = a; translationX = (1f - a) * 60f }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuButtons(vm, last?.let { "${it.club} • Season ${it.season}" })
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 56.dp).graphicsLayer { alpha = a },
                horizontalAlignment = Alignment.CenterHorizontally) {
                BrandLogo((fullW * 0.72f).coerceAtMost(320.dp))
                Spacer(Modifier.height(6.dp))
                Text(Brand.TAGLINE.uppercase(), color = DS.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, maxLines = 1, softWrap = false)
                Spacer(Modifier.height(26.dp))
                Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { MenuButtons(vm, last?.let { "${it.club} • Season ${it.season}" }) }
            }
        }
        Text("Real teams • ${Repo.ds.players.size} players • fictional likenesses", color = Color(0x80FFFFFF), fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start)).padding(start = 18.dp, bottom = 10.dp))
    }
}

@Composable
private fun MenuButtons(vm: GameVM, lastSub: String?) {
    MenuButton("NEW GAME", if (vm.save != null || lastSub != null) "Continue or start a career" else "Start your first career", Icons.Filled.PlayArrow, primary = true) { vm.nav(Screen.NewGame) }
    MenuButton("LOAD GAME", lastSub ?: "Career slots & cloud saves", Icons.Filled.List) { vm.nav(Screen.Career) }
    MenuButton("SETTINGS", "Audio • graphics • display • data", Icons.Filled.Settings) { vm.nav(Screen.SettingsScr) }
    MenuButton("ABOUT", "Version, credits & data sources", Icons.Filled.Info) { vm.nav(Screen.About) }
}

/** Compact premium menu button: 52dp tall, gold primary / navy secondary with a thin gold rule. */
@Composable
fun MenuButton(title: String, sub: String, icon: ImageVector, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, label = "mb")
    val shape = RoundedCornerShape(9.dp)
    val ink = if (primary) DS.GoldInk else DS.White
    Row(Modifier.fillMaxWidth().height(52.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = if (enabled) 1f else 0.45f }.clip(shape)
        .background(if (primary) DS.Gold else Color(0xE60A1224)).border(1.dp, if (primary) DS.Gold else DS.Gold.copy(alpha = 0.35f), shape)
        .semantics { contentDescription = title }
        .clickable(src, null, enabled = enabled, role = Role.Button) { GameAudio.click(); onClick() }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (primary) DS.GoldInk else DS.Gold, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ink, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 1.6.sp, maxLines = 1, softWrap = false, lineHeight = 19.sp)
            Text(sub, color = ink.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 12.sp)
        }
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AccountChip(vm: GameVM, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xCC0A1224)).border(1.dp, DS.Line, RoundedCornerShape(24.dp))
        .clickable { GameAudio.click(); vm.nav(if (vm.signedIn) Screen.Account else Screen.Login) }.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Avatar(vm.avatar, vm.profile?.username ?: "G", 28.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(if (vm.signedIn) (vm.profile?.displayName?.ifBlank { null } ?: vm.profile?.username ?: vm.email) else "Guest • Log in", color = DS.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            Text("Lv ${vm.level} • ${vm.coins} coins", color = DS.Sub, fontSize = 10.sp, maxLines = 1)
        }
    }
}

/* =============================================================================================== new game hub */

/** NEW GAME: continue the latest career, start a new career (International / Domestic / IPL) or create your own team. */
@Composable
fun NewGameScreen(vm: GameVM) {
    LaunchedEffect(Unit) { vm.refreshSaves() }
    val last = vm.saves.firstOrNull { it.slot == vm.settings.lastSlot } ?: vm.saves.maxByOrNull { it.updated }
    ScreenScaffold(vm, "New Game") {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 640.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (vm.save != null || last != null) {
                    DsCard(accent = DS.Gold) {
                        DsLabel("Continue career")
                        Spacer(Modifier.height(4.dp))
                        val cur = vm.save
                        Text(cur?.userClub()?.name ?: last!!.club, color = DS.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1)
                        Text(if (cur != null) "${Modes.label(cur.mode)} • Season ${cur.season} (${cur.year}) • ${phaseName(cur)}"
                            else "${Modes.label(last!!.mode)} • Season ${last.season} • Manager ${last.manager} • saved ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(last.updated))}",
                            color = DS.Sub, fontSize = 12.sp, maxLines = 2)
                        Spacer(Modifier.height(8.dp))
                        DsButton("Continue", Modifier.widthIn(min = 160.dp), icon = Icons.Filled.PlayArrow) { if (vm.save != null) vm.goHome() else last?.let { vm.loadCareer(it.slot) } }
                    }
                }
                val cards: List<@Composable (Modifier) -> Unit> = listOf(
                    { m -> HubCard(m, "New career", "International • Domestic • IPL", "Pick a real national side, a state team or an IPL franchise. Careers start in the ${Brand.CAREER_START} season.",
                        Icons.Filled.Add, "Start") { startNewCareerFlow(vm, Screen.CareerType) } },
                    { m -> HubCard(m, "Create your team", "Name • badge • colours • stadium", "Build your own club from scratch and grow it through the divisions.",
                        Icons.Filled.Create, "Create") { startNewCareerFlow(vm, Screen.NewTeam) } },
                    { m -> HubCard(m, "Player database", "${Repo.ds.players.size} real players", "Browse ratings, career stats and data sources before you pick your side.",
                        Icons.Filled.Search, "Browse", primary = false) { vm.nav(Screen.Database) } },
                )
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { cards.forEach { it(Modifier.weight(1f)) } }
                else cards.forEach { it(Modifier.fillMaxWidth()) }
                Text(if (vm.signedIn) "Signed in: 3 career slots, saved to your account." else "Guest: 1 temporary slot on this phone. Log in for 3 permanent slots.",
                    color = DS.Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun HubCard(modifier: Modifier, title: String, tag: String, body: String, icon: ImageVector, cta: String, primary: Boolean = true, onClick: () -> Unit) {
    DsCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(DS.Navy4).border(1.dp, DS.Gold.copy(alpha = 0.4f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = DS.Gold, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { DsTitle(title, size = 17.sp); Text(tag, color = DS.Gold, fontSize = 11.sp, maxLines = 1) }
        }
        Spacer(Modifier.height(6.dp))
        Text(body, color = DS.Sub, fontSize = 12.sp, lineHeight = 16.sp, minLines = 2, maxLines = 3)
        Spacer(Modifier.height(8.dp))
        DsButton(cta, kind = if (primary) DsKind.PRIMARY else DsKind.SECONDARY, height = DS.BtnHSmall, onClick = onClick)
    }
}

/* =============================================================================================== about */

@Composable
fun AboutScreen(vm: GameVM) {
    ScreenScaffold(vm, "About") {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 640.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val head: @Composable () -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandLogo(if (wide) 132.dp else 104.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            BrandStack(size = 22.sp, center = false)
                            Spacer(Modifier.height(4.dp))
                            Text("Version ${Brand.VERSION} • ${Brand.EDITION}", color = DS.Sub, fontSize = 12.sp)
                            Text("Careers start in the ${Brand.CAREER_START} season", color = DS.Muted, fontSize = 11.sp)
                        }
                    }
                }
                DsCard { head() }
                DsCard {
                    DsLabel("The game")
                    Spacer(Modifier.height(4.dp))
                    AboutLine("Manage real national, domestic and IPL teams: squads, playing XI, batting order, bowling plans, auctions, transfers and registrations.")
                    AboutLine("Every ball is simulated by the match engine with English / Hindi / Hinglish commentary, a TV-style scoreboard and a 3D stadium.")
                    AboutLine("Stumpstagram and ${Yorker.NAME} are in-game social networks. Follower counts are in-game estimates; fictional players are never verified.")
                }
                DsCard {
                    DsLabel("Schedules")
                    Spacer(Modifier.height(4.dp))
                    AboutLine("IPL 2026: fixture dates, start times and venues follow the published official schedule (league stage 28 Mar - final 31 May 2026). Results are always simulated by the game.")
                    AboutLine("All other competitions, and seasons after 2026, use generated fixtures. These are labelled \"generated\" in the calendar and are not official schedules.")
                }
                DsCard {
                    DsLabel("Data sources")
                    Spacer(Modifier.height(4.dp))
                    KV("Players", "${Repo.ds.players.size}")
                    KV("Dataset built", Repo.ds.meta.built)
                    for ((key, c) in Repo.ds.meta.coverage) KV("Cricsheet $key", "${c.matches} matches • ${c.first} → ${c.last}")
                    AboutLine(Repo.ds.meta.source)
                    if (Repo.ds.meta.note.isNotEmpty()) AboutLine(Repo.ds.meta.note)
                    AboutLine("Official career totals: Wikipedia 'Infobox cricketer' (sourced from ESPNcricinfo), with the as-of date on each profile.")
                    AboutLine("Ball-by-ball splits: Cricsheet (ODC-By 1.0). Ratings are game abstractions derived from those stats.")
                    Repo.ds.meta.photoSource.takeIf { it.isNotEmpty() }?.let { AboutLine("$it (optional download, Settings > Graphics).") }
                }
                DsCard {
                    DsLabel("Art, audio & legal")
                    Spacer(Modifier.height(4.dp))
                    AboutLine("Logo, loading art, key art and social photo art are original generated artwork of fictional people. No real-person likeness, federation, league or sponsor logos are used.")
                    AboutLine("Team badges are original artwork. All sound effects and music are original and synthesised for this game. Fonts: Barlow Condensed (SIL Open Font License).")
                    AboutLine("${Brand.FULL} is an independent game and is not affiliated with, or endorsed by, any cricket board, league, team or player.")
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun AboutLine(s: String) = Text("• $s", color = DS.Sub, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(vertical = 2.dp))
