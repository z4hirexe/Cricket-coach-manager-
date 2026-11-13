package com.cricketcoach.manager.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.R
import com.cricketcoach.manager.ui.*
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip

@Composable
fun AppRoot(vm: GameVM) {
    var splashDone by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        when {
            vm.loading || !splashDone -> Splash(loading = vm.loading) { splashDone = true }
            vm.loadError != null -> Text(vm.loadError!!, color = C.Red, modifier = Modifier.padding(24.dp))
            else -> {
                val s = vm.screen
                val needsSave = s !is Screen.Main && s !is Screen.Career && s !is Screen.NewCareer && s !is Screen.SettingsScr && s !is Screen.Database && s !is Screen.Profile &&
                    s !is Screen.CareerType && s !is Screen.ChooseTeam && s !is Screen.NewTeam && s !is Screen.Social && s !is Screen.About && s !is Screen.NewGame
                val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                if (needsSave && vm.save == null) { LaunchedEffect(Unit) { vm.goHome() } }
                else if (landscape && vm.save != null && s.inShell()) GameShell(vm) { Screens(vm, s) }
                else Screens(vm, s)
            }
        }
        if (vm.busy) BusyOverlay()
        val t = vm.toast
        if (t != null) {
            LaunchedEffect(t) { delay(3200); if (vm.toast == t) vm.toast = null }
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                Snackbar(containerColor = C.CardHi, contentColor = C.Text, action = { TextButton(onClick = { vm.toast = null }) { Text("OK", color = C.Gold) } }) { Text(t) }
            }
        }
    }
}

@Composable
private fun Screens(vm: GameVM, s: Screen) {
    AnimatedContent(targetState = s, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { scr ->
        when (scr) {
            is Screen.Main -> MainMenu(vm)
            is Screen.Career -> CareerSlots(vm)
            is Screen.NewCareer -> CareerTypeScreen(vm)
            is Screen.Dashboard -> Dashboard(vm)
            is Screen.MyClub -> MyClubScreen(vm)
            is Screen.Squad -> SquadScreen(vm, scr.clubId)
            is Screen.Database -> DatabaseScreen(vm)
            is Screen.Profile -> ProfileScreen(vm, scr.pid)
            is Screen.AuctionScr -> AuctionScreen(vm)
            is Screen.Transfers -> TransfersScreen(vm)
            is Screen.Fixtures -> FixturesScreen(vm)
            is Screen.PreMatch -> PreMatchScreen(vm)
            is Screen.Live -> LiveMatchScreen(vm)
            is Screen.Table -> TableScreen(vm)
            is Screen.TournamentsScr -> TournamentsScreen(vm)
            is Screen.Statistics -> StatisticsScreen(vm)
            is Screen.Finances -> FinancesScreen(vm)
            is Screen.SettingsScr -> SettingsScreen(vm)
            is Screen.SeasonSummary -> SeasonSummaryScreen(vm)
            is Screen.CareerType -> CareerTypeScreen(vm)
            is Screen.ChooseTeam -> ChooseTeamScreen(vm, scr.mode)
            is Screen.NewTeam -> NewTeamScreen(vm)
            is Screen.Selection -> SelectionScreen(vm)
            is Screen.Social -> SocialScreen(vm)
            is Screen.Inbox -> InboxScreen(vm)
            is Screen.Calendar -> CalendarScreen(vm)
            is Screen.SocialProfile -> SocialProfileScreen(vm, scr.pid)
            is Screen.SquadRegistration -> SquadRegistrationScreen(vm, scr.compId)
            is Screen.YorkerHome -> YorkerScreen(vm)
            is Screen.YorkerProfile -> YorkerProfileScreen(vm, scr.key)
            is Screen.YorkerThread -> YorkerThreadScreen(vm, scr.postId)
            is Screen.MatchReport -> MatchReportScreen(vm, scr.compId, scr.fxId)
            is Screen.About -> AboutScreen(vm)
            is Screen.NewGame -> NewGameScreen(vm)
        }
    }
}

private val TIPS = listOf(
    "Pace bowlers lose sharpness when fitness drops below 70 - rotate them in long tours.",
    "Spinners extract more turn from day 3 onwards in red-ball matches.",
    "A set batter is worth protecting: avoid sending a nightwatchman late in the day.",
    "Registration windows close before the first fixture - lock your best 15 early.",
    "In T20s, two quiet overs at the death win more matches than one great powerplay.",
    "Auction tip: keep 20% of your purse for the accelerated rounds.",
    "Form matters: players in red form rarely turn it around in a single match.",
    "Dew in evening games helps the chasing side - think twice before batting first.",
    "Your batting order changes the 3D field: left-right pairs force bowlers to adjust.",
    "Overseas slots are limited in the IPL - pick quality over quantity."
)

/** Short status lines under the loading bar (what is being prepared). */
private val STEPS = listOf("Loading player database", "Preparing squads", "Building the cricket calendar", "Warming up the commentators", "Rolling the pitch", "Switching on the floodlights")

/**
 * Loading screen: the user-supplied batter artwork is shown as-is (Fit, never redrawn or replaced). A small supplied
 * game logo sits in the safe top corner; progress, status and tips stay in a bottom scrim so the photo remains visible
 * on portrait phones, landscape phones and tablets. System-bar / cutout insets keep controls reachable.
 */
@Composable
fun Splash(loading: Boolean = true, onDone: () -> Unit = {}) {
    val prog = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val out = remember { Animatable(1f) }
    var tip by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2600); tip = (tip + 1) % TIPS.size } }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(700)) }
    LaunchedEffect(Unit) { prog.animateTo(0.86f, tween(1900, easing = FastOutSlowInEasing)) }
    LaunchedEffect(loading) {
        if (!loading) {
            while (prog.isRunning && prog.value < 0.86f) delay(30)
            prog.animateTo(1f, tween(380)); delay(160)
            out.animateTo(0f, tween(320)); onDone()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).graphicsLayer { alpha = out.value }) {
        val landscape = maxWidth > maxHeight
        val p = prog.value
        // The supplied loading image is preserved in full: Fit avoids cropping the bat / gloves on narrow phones.
        Image(painterResource(R.drawable.loading_hero), null,
            Modifier.fillMaxSize().graphicsLayer { alpha = fade.value },
            contentScale = ContentScale.Fit, alignment = Alignment.Center)
        // Only a bottom scrim, so the supplied photo is not covered by a heavy side gradient.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(if (landscape) 132.dp else 176.dp)
            .background(Brush.verticalGradient(0f to Color(0x00000000), 0.45f to Color(0xB3000000), 1f to Color(0xF2000000))))
        BrandLogo(if (landscape) 64.dp else 76.dp,
            Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.systemBars)).padding(14.dp).graphicsLayer { alpha = fade.value })
        Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 620.dp).fillMaxWidth()
            .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.systemBars))
            .padding(horizontal = if (landscape) 28.dp else 20.dp, vertical = 12.dp).graphicsLayer { alpha = fade.value },
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(Color(0x26FFFFFF))) {
                    Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(DS.Gold))
                }
                Spacer(Modifier.width(12.dp))
                Text("${(p * 100).toInt()}%", color = DS.Gold, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(46.dp), maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.height(6.dp))
            Text(STEPS[(p * (STEPS.size - 1)).toInt().coerceIn(0, STEPS.size - 1)].uppercase() + "…", color = DS.Sub, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text("TIP", color = DS.GoldInk, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).background(DS.Gold).padding(horizontal = 5.dp, vertical = 1.dp))
                Spacer(Modifier.width(8.dp))
                AnimatedContent(tip, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tip") { i ->
                    Text(TIPS[i], color = DS.White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(Brand.EDITION.uppercase() + " • v" + Brand.VERSION, color = Color(0x66FFFFFF), fontSize = 9.sp, letterSpacing = 1.sp, maxLines = 1)
        }
    }
}

/** "Simulating…" overlay with rotating tips. */
@Composable
private fun BusyOverlay() {
    var tip by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1100); tip = (tip + 1) % STEPS.size } }
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(Color(0xF00A1224)).padding(horizontal = 28.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = DS.Gold, strokeWidth = 3.dp)
            Spacer(Modifier.height(10.dp))
            Text("SIMULATING", color = C.Text, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text(STEPS[tip % STEPS.size] + "…", color = C.Sub, fontSize = 11.sp)
        }
    }
}
