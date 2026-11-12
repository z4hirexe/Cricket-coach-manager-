package com.cricketcoach.manager.preview

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.ui.CcmTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import com.cricketcoach.manager.ui.GameVM
import com.cricketcoach.manager.ui.Screen
import com.cricketcoach.manager.ui.screens.*
import com.cricketcoach.manager.game.*
import androidx.compose.runtime.Composable
import kotlin.random.Random
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders real app screens to docs/previews (phone landscape 2400x1080-class, 891x411 dp @ xxhdpi). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi")
class ScreenPreviews {
    @get:Rule val compose = createComposeRule()
    private val out = File("../docs/previews/screens").apply { mkdirs() }

    @Before fun data() { if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) } }

    private fun vm() = GameVM(ApplicationProvider.getApplicationContext<Application>())

    @Test fun home() {
        val vm = vm()
        compose.setContent { CcmTheme { MainMenu(vm) } }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage(File(out, "01_home.png").path)
    }

    @Test fun splash() {
        compose.setContent { CcmTheme { Splash() } }
        compose.mainClock.advanceTimeBy(1400)
        compose.onRoot().captureRoboImage(File(out, "00_splash.png").path)
    }

    private fun shot(name: String, ms: Long = 1200, content: @Composable () -> Unit) {
        compose.setContent { CcmTheme { content() } }
        compose.mainClock.advanceTimeBy(ms)
        compose.onRoot().captureRoboImage(File(out, name).path)
    }

    /** A domestic (Bengal) career that has played its first weeks, so feeds/tables have real content. */
    private fun careerVm(mode: String = Modes.DOMESTIC, team: String = "S_BEN", days: Int = 6): GameVM {
        File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
        val vm = vm()
        val rnd = Random(4)
        val s = Modes.newCareer(1, "Arjun Sen", mode, team, 1.0, 1, rnd)
        s.auction = null
        Career.startSeason(s)
        repeat(days) {
            val d = s.league.flatMap { it.fixtures }.filter { !it.played }.minOfOrNull { it.day } ?: return@repeat
            s.day = d
            for ((c, f) in Career.fixturesOnDay(s, d)) Career.quickSim(s, c, f, rnd)
            Career.weeklyUpdate(s, emptySet(), rnd)
        }
        vm.save = s
        return vm
    }

    @Test fun careerType() { val vm = vm(); shot("10_career_type.png") { CareerTypeScreen(vm) } }
    @Test fun chooseState() { val vm = vm(); File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }; shot("11_choose_state.png") { ChooseTeamScreen(vm, Modes.DOMESTIC) } }
    @Test fun chooseIpl() { val vm = vm(); shot("12_choose_ipl.png") { ChooseTeamScreen(vm, Modes.IPL) } }
    @Test fun newTeam() { val vm = vm(); shot("13_new_team.png") { NewTeamScreen(vm) } }
    @Test fun login() { val vm = vm(); shot("20_login.png") { LoginScreen(vm) } }
    @Test fun signUp() { val vm = vm(); shot("21_signup.png") { SignUpScreen(vm) } }
    /** Career screens are shown the way the app shows them: inside the sidebar + top-bar shell. */
    private fun inShell(vm: GameVM, scr: Screen, name: String, ms: Long = 1500, content: @Composable () -> Unit) {
        vm.stack.clear(); vm.stack.add(Screen.Main); vm.stack.add(Screen.Dashboard); if (scr != Screen.Dashboard) vm.stack.add(scr)
        shot(name, ms) { com.cricketcoach.manager.ui.GameShell(vm) { content() } }
    }

    /** Commentary 2.0 feed after a few overs of a live T20 (English booth and Hinglish booth). */
    private fun commentaryShot(lang: String, name: String) {
        val vm = careerVm(Modes.INTL, "N_IND", 0)
        vm.settings.comLang = lang; vm.settings.comStyle = if (lang == "HG") 1 else 0
        val s = vm.save!!
        val comp = com.cricketcoach.manager.game.Competition(id = "PV", name = "Border Cup", key = "PV", format = "T20", kind = "NATION", series = true)
        val m = com.cricketcoach.manager.game.Career.makeSim(s, comp, com.cricketcoach.manager.game.Fixture("pv", "N_IND", "N_AUS", "Match", 0, 1), Random(7))
        m.autoToss()
        repeat(9 * 6 + 2) { if (!m.finished) { m.nextBall(); vm.updateCommentary(m) } }
        shot(name) { Box(Modifier.fillMaxSize().background(com.cricketcoach.manager.ui.C.Bg)) { Row(Modifier.fillMaxSize()) { Box(Modifier.weight(1f)) { CommentaryFeed(vm) } } } }
    }
    /** Phase 6: IPL mini-auction stage mid-bidding (clock stopped so the frame is deterministic). */
    private fun auctionVm(hammered: Int): GameVM {
        val vm = careerVm(Modes.IPL, "F_CSK", 0)
        val s = vm.save!!
        s.auctionDoneThisSeason = false
        val a = Auction.start(s, "MINI"); s.auction = a
        val rnd = Random(12)
        repeat(hammered) { Auction.resolveLot(s, a, rnd) }
        // a few live steps so the paddle wall and the ticker have something to show
        var g = 0
        while (a.lotBidders.size < 3 && g++ < 40) { if (Auction.tick(s, a, rnd)) break }
        if (a.curBidder != s.userClubId) Auction.userBid(s, a)
        Auction.tick(s, a, rnd)
        vm.stack.clear(); vm.stack.add(Screen.Main); vm.stack.add(Screen.Dashboard); vm.stack.add(Screen.AuctionScr)
        return vm
    }
    @Test fun auctionStage() { val vm = auctionVm(3); shot("70_auction_stage.png", 1600) { AuctionScreen(vm, preview = 1) } }
    @Test fun auctionSold() { val vm = auctionVm(5); shot("71_auction_sold.png", 1400) { AuctionScreen(vm, preview = 2) } }

    /** Phase 7: redesigned player profile (hero, radar, condition, form) for a real star and a career-mode view. */
    @Test fun profileStar() { val vm = careerVm(Modes.INTL, "N_IND", 2); inShell(vm, Screen.Profile("ba607b88"), "72_profile_kohli.png", 1600) { ProfileScreen(vm, "ba607b88") } }
    /** Full-length profile overview (tall canvas) so the radar, gauges and form chart are visible. */
    @Config(sdk = [34], qualifiers = "w891dp-h1250dp-xxhdpi")
    @Test fun profileOverviewFull() { val vm = careerVm(Modes.IPL, "F_MI", 2); vm.stack.clear(); vm.stack.add(Screen.Main); vm.stack.add(Screen.Profile("462411b3")); shot("74_profile_overview_full.png", 1600) { ProfileScreen(vm, "462411b3") } }
    @Test fun profileBowler() { val vm = careerVm(Modes.IPL, "F_MI", 2); inShell(vm, Screen.Profile("462411b3"), "73_profile_bumrah.png", 1600) { ProfileScreen(vm, "462411b3") } }

    @Test fun commentaryEnglish() = commentaryShot("EN", "60_commentary_en.png")
    @Test fun commentaryHinglish() = commentaryShot("HG", "61_commentary_hinglish.png")

    @Test fun dashboardDomestic() { val vm = careerVm(); inShell(vm, Screen.Dashboard, "30_dashboard_bengal.png") { Dashboard(vm) } }
    @Test fun dashboardIntl() { val vm = careerVm(Modes.INTL, "N_IND", 4); inShell(vm, Screen.Dashboard, "33_dashboard_india.png") { Dashboard(vm) } }
    @Test fun dashboardIpl() { val vm = careerVm(Modes.IPL, "F_CSK", 5); inShell(vm, Screen.Dashboard, "34_dashboard_ipl.png") { Dashboard(vm) } }
    @Test fun inbox() { val vm = careerVm(days = 8); inShell(vm, Screen.Inbox, "35_inbox.png") { InboxScreen(vm) } }
    @Test fun socialIntl() { val vm = careerVm(Modes.INTL, "N_IND", 6); inShell(vm, Screen.Social, "41_cricgram_india.png", 2000) { SocialScreen(vm) } }
    @Test fun fixturesInShell() { val vm = careerVm(days = 6); inShell(vm, Screen.Fixtures, "36_fixtures_shell.png") { FixturesScreen(vm) } }
    @Test fun tableDomestic() { val vm = careerVm(days = 8); shot("31_points_table.png") { TableScreen(vm) } }
    @Test fun social() { val vm = careerVm(days = 10); inShell(vm, Screen.Social, "40_cricgram_bengal.png", 2000) { SocialScreen(vm) } }
    @Test fun selection() { val vm = careerVm(Modes.INTL, "N_IND", 0); shot("32_selection_india.png") { SelectionScreen(vm) } }
    @Test fun settings() { val vm = vm(); shot("50_settings.png") { SettingsScreen(vm) } }
}
