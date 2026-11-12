package com.cricketcoach.manager.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.ui.*
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer

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
                    s !is Screen.Login && s !is Screen.SignUp && s !is Screen.Forgot && s !is Screen.ResetPassword && s !is Screen.Account &&
                    s !is Screen.CareerType && s !is Screen.ChooseTeam && s !is Screen.NewTeam && s !is Screen.Social && s !is Screen.About && s !is Screen.NewGame
                val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                if (needsSave && vm.save == null) { LaunchedEffect(Unit) { vm.goHome() } }
                else if (landscape && vm.save != null && s.inShell()) GameShell(vm) { Screens(vm, s) }
                else Screens(vm, s)
            }
        }
        if (vm.busy) BusyOverlay()
        if (vm.guestImportOffer.isNotEmpty()) GuestImportDialog(vm)
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
            is Screen.Login -> LoginScreen(vm)
            is Screen.SignUp -> SignUpScreen(vm)
            is Screen.Forgot -> ForgotScreen(vm)
            is Screen.ResetPassword -> ResetPasswordScreen(vm)
            is Screen.Account -> AccountScreen(vm)
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
 * Loading screen (v2.6): black studio with the original batter artwork (bat on shoulders, stadium rim light), the 2027
 * lockup, an animated progress bar with percentage, rotating cricket tips and a fade into the menu. The art is anchored
 * to the right so the subject stays in frame on tall phones and wide tablets alike.
 */
@Composable
fun Splash(loading: Boolean = true, onDone: () -> Unit = {}) {
    val prog = remember { androidx.compose.animation.core.Animatable(0f) }
    val fade = remember { androidx.compose.animation.core.Animatable(0f) }
    val out = remember { androidx.compose.animation.core.Animatable(1f) }
    var tip by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2600); tip = (tip + 1) % TIPS.size } }
    LaunchedEffect(Unit) { fade.animateTo(1f, androidx.compose.animation.core.tween(900)) }
    LaunchedEffect(Unit) { prog.animateTo(0.86f, androidx.compose.animation.core.tween(1900, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    LaunchedEffect(loading) {
        if (!loading) {
            while (prog.isRunning && prog.value < 0.86f) delay(30)
            prog.animateTo(1f, androidx.compose.animation.core.tween(380)); delay(160)
            out.animateTo(0f, androidx.compose.animation.core.tween(320)); onDone()
        }
    }
    val drift = androidx.compose.animation.core.rememberInfiniteTransition(label = "drift")
    val z by drift.animateFloat(1.0f, 1.05f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(16000), androidx.compose.animation.core.RepeatMode.Reverse), label = "z")
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).graphicsLayer { alpha = out.value }) {
        val landscape = maxWidth > maxHeight
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.cricketcoach.manager.R.drawable.loading_hero), null,
            Modifier.fillMaxSize().graphicsLayer { alpha = fade.value; scaleX = z; scaleY = z; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.75f, 0.4f) },
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = if (landscape) Alignment.CenterEnd else androidx.compose.ui.BiasAlignment(0.55f, 0f))
        // keep the left side (logo + bar) readable: black falloff, no colour gradients
        Box(Modifier.fillMaxSize().background(if (landscape) androidx.compose.ui.graphics.Brush.horizontalGradient(0f to Color(0xF2000000), 0.42f to Color(0xB3000000), 0.7f to Color(0x00000000))
            else androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color(0x00000000), 0.55f to Color(0x66000000), 1f to Color(0xF2000000))))
        val p = prog.value
        Column(Modifier.align(if (landscape) Alignment.CenterStart else Alignment.BottomCenter).padding(start = if (landscape) 40.dp else 24.dp, end = 24.dp, bottom = if (landscape) 0.dp else 40.dp)
            .widthIn(max = 420.dp).graphicsLayer { alpha = fade.value }, horizontalAlignment = Alignment.Start) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(46.dp)
                Spacer(Modifier.width(12.dp))
                BrandStack(size = if (landscape) 26.sp else 22.sp, center = false)
            }
            Spacer(Modifier.height(if (landscape) 28.dp else 20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(Color(0x26FFFFFF))) {
                    Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(DS.Gold))
                }
                Spacer(Modifier.width(12.dp))
                Text("${(p * 100).toInt()}%", color = DS.Gold, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(46.dp), maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.height(6.dp))
            Text(STEPS[(p * (STEPS.size - 1)).toInt().coerceIn(0, STEPS.size - 1)].uppercase() + "…", color = DS.Sub, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, maxLines = 1)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text("TIP", color = DS.GoldInk, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).background(DS.Gold).padding(horizontal = 5.dp, vertical = 1.dp))
                Spacer(Modifier.width(8.dp))
                AnimatedContent(tip, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tip") { i ->
                    Text(TIPS[i], color = DS.White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
                }
            }
        }
        Text(Brand.EDITION.uppercase() + " • v" + Brand.VERSION, color = Color(0x66FFFFFF), fontSize = 9.sp, letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 10.dp))
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
