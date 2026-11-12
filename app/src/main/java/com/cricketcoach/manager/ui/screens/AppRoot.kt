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

@Composable
fun AppRoot(vm: GameVM) {
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        when {
            vm.loading -> Splash()
            vm.loadError != null -> Text(vm.loadError!!, color = C.Red, modifier = Modifier.padding(24.dp))
            else -> {
                val s = vm.screen
                val needsSave = s !is Screen.Main && s !is Screen.Career && s !is Screen.NewCareer && s !is Screen.SettingsScr && s !is Screen.Database && s !is Screen.Profile &&
                    s !is Screen.Login && s !is Screen.SignUp && s !is Screen.Forgot && s !is Screen.ResetPassword && s !is Screen.Account &&
                    s !is Screen.CareerType && s !is Screen.ChooseTeam && s !is Screen.NewTeam && s !is Screen.Social
                if (needsSave && vm.save == null) { LaunchedEffect(Unit) { vm.goHome() } }
                else AnimatedContent(targetState = s, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { scr ->
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
                    }
                }
            }
        }
        if (vm.busy) Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = C.Gold); Spacer(Modifier.height(8.dp)); Text("Simulating…", color = C.Text) }
        }
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
fun Splash() {
    Box(Modifier.fillMaxSize().background(C.Bg), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.cricketcoach.manager.R.drawable.stadium_bg), null, Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, alpha = 0.35f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.cricketcoach.manager.R.drawable.logo_cmp), "Cricket Manager Pro", Modifier.size(170.dp))
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(Modifier.width(180.dp).height(3.dp), color = C.Gold, trackColor = Color(0x33E9B949))
            Spacer(Modifier.height(8.dp))
            Sub("Loading verified player database…")
        }
    }
}
