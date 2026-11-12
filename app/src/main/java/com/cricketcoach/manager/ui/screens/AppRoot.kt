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
                    s !is Screen.CareerType && s !is Screen.ChooseTeam && s !is Screen.NewTeam && s !is Screen.Social
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
        }
    }
}

private val TIPS = listOf(
    "SCOUTING OPPOSITION WEAKNESSES", "ROLLING THE PITCH", "PAINTING THE CREASES", "CHECKING THE WEATHER FORECAST", "STUDYING THE BATTING ORDER",
    "SETTING DEATH-OVER FIELDS", "POLISHING THE NEW BALL", "BRIEFING THE DRESSING ROOM", "REVIEWING LAST SEASON'S FOOTAGE", "LOADING THE VERIFIED PLAYER DATABASE"
)

/** Cinematic loading screen: manager's desk, logo, progress bar and rotating tips. */
@Composable
fun Splash(loading: Boolean = true, onDone: () -> Unit = {}) {
    val prog = remember { androidx.compose.animation.core.Animatable(0f) }
    var tip by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1300); tip = (tip + 1) % TIPS.size } }
    LaunchedEffect(Unit) { prog.animateTo(0.86f, androidx.compose.animation.core.tween(1700, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    LaunchedEffect(loading) {
        if (!loading) {
            while (prog.isRunning && prog.value < 0.86f) delay(30)
            prog.animateTo(1f, androidx.compose.animation.core.tween(350)); delay(180); onDone()
        }
    }
    val logoScale by androidx.compose.animation.core.animateFloatAsState(if (prog.value > 0.05f) 1f else 0.85f, androidx.compose.animation.core.tween(900), label = "logo")
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.cricketcoach.manager.R.drawable.loading_desk), null, Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0x00000000), Color(0x22000000), Color(0xCC000000)), radius = 1400f)))
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.cricketcoach.manager.R.drawable.logo_cmp), "Cricket Manager Pro",
            Modifier.align(Alignment.Center).padding(bottom = 40.dp).size(210.dp).graphicsLayer { scaleX = logoScale; scaleY = logoScale })
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(0.72f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(6.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(prog.value).fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF0EA5E9), Color(0xFF22D3EE), Color(0xFFA5F3FC)))))
                }
                Spacer(Modifier.width(14.dp))
                Text("${(prog.value * 100).toInt()}%", color = Color(0xFF22D3EE), fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.width(52.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("LOADING: ${TIPS[tip]}...", color = Color(0xFFE2E8F0), fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.5.sp)
        }
    }
}

/** "Simulating…" overlay with rotating tips. */
@Composable
private fun BusyOverlay() {
    var tip by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1100); tip = (tip + 1) % TIPS.size } }
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(Color(0xF00B111C)).padding(horizontal = 28.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF22D3EE), strokeWidth = 3.dp)
            Spacer(Modifier.height(10.dp))
            Text("SIMULATING", color = C.Text, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text(TIPS[tip].lowercase().replaceFirstChar { it.uppercase() } + "…", color = C.Sub, fontSize = 11.sp)
        }
    }
}
