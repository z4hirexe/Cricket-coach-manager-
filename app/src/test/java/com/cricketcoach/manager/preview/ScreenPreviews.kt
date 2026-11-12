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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    // Phase B: premium auction broadcast
    @Test fun auctionRetention() {
        val vm = careerVm(Modes.IPL, "F_MI", 0); val s = vm.save!!
        s.auctionDoneThisSeason = false; s.auction = Auction.start(s, "MEGA")
        vm.stack.clear(); vm.stack.add(Screen.Main); vm.stack.add(Screen.Dashboard); vm.stack.add(Screen.AuctionScr)
        shot("79_auction_retention.png", 1400) { AuctionScreen(vm, preview = 1) }
    }
    @Test fun auctionReport() {
        val vm = careerVm(Modes.IPL, "F_RCB", 0); val s = vm.save!!
        s.auctionDoneThisSeason = false; val a = Auction.start(s, "MEGA"); s.auction = a
        val u = s.userClub(); Auction.confirmRetention(s, a, u.squad.mapNotNull { s.players[it] }.sortedByDescending { it.overall() }.take(3).map { it.id })
        val rnd = Random(7)
        // the user wins a few lots, then the rest is simulated
        repeat(6) { if (Auction.userBid(s, a) == null) { var g = 0; while (!Auction.tick(s, a, rnd) && g++ < 30) { if (a.curBidder != s.userClubId) Auction.userBid(s, a) } } else Auction.resolveLot(s, a, rnd) }
        Auction.autoComplete(s, a, rnd)
        vm.stack.clear(); vm.stack.add(Screen.Main); vm.stack.add(Screen.Dashboard); vm.stack.add(Screen.AuctionScr)
        shot("80_auction_report.png", 1400) { AuctionScreen(vm, preview = 1) }
    }
    @Config(sdk = [34], qualifiers = "w740dp-h360dp-land-xxhdpi")
    @Test fun auctionStageCompact() { val vm = auctionVm(12); shot("81_auction_stage_compact.png", 1600) { AuctionScreen(vm, preview = 1) } }
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
    @Test fun socialIntl() { val vm = careerVm(Modes.INTL, "N_IND", 6); inShell(vm, Screen.Social, "41_stumpstagram_india.png", 2000) { SocialScreen(vm) } }
    @Test fun fixturesInShell() { val vm = careerVm(days = 6); inShell(vm, Screen.Fixtures, "36_fixtures_shell.png") { FixturesScreen(vm) } }
    @Test fun tableDomestic() { val vm = careerVm(days = 8); shot("31_points_table.png") { TableScreen(vm) } }
    @Test fun social() { val vm = careerVm(days = 10); inShell(vm, Screen.Social, "40_stumpstagram_bengal.png", 2000) { SocialScreen(vm) } }
    @Test fun selection() { val vm = careerVm(Modes.INTL, "N_IND", 0); shot("32_selection_india.png") { SelectionScreen(vm) } }
    // Phase A: cricket calendar / match schedule centre
    @Test fun calendarIpl() { val vm = careerVm(Modes.IPL, "F_CSK", 5); inShell(vm, Screen.Calendar, "75_calendar_ipl.png", 1600) { CalendarScreen(vm) } }
    @Test fun calendarIntl() { val vm = careerVm(Modes.INTL, "N_IND", 2); inShell(vm, Screen.Calendar, "76_calendar_intl.png", 1600) { CalendarScreen(vm) } }
    @Test fun calendarDomestic() {
        val vm = careerVm(Modes.DOMESTIC, "S_BEN", 6)
        val s = vm.save!!
        // open on the user's next Ranji match so the match cards are visible
        val next = Schedule.matches(s).filter { it.mine && it.fx?.played == false }.minByOrNull { it.date }?.date
        inShell(vm, Screen.Calendar, "77_calendar_domestic.png", 1600) { CalendarScreen(vm, next) }
    }
    @Test fun calendarPreseasonIpl() {
        File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
        val vm = vm(); val s = Modes.newCareer(1, "Arjun Sen", Modes.IPL, "F_KKR", 1.0, 1, Random(4)); vm.save = s
        inShell(vm, Screen.Calendar, "78_calendar_preseason_auction.png", 1600) { CalendarScreen(vm, Schedule.seasonStart(s).minusDays(12)) }
    }
    // ---- Phase C: per-player 3D appearance
    @Test fun profileKeeper() { val vm = careerVm(Modes.IPL, "F_CSK", 2); inShell(vm, Screen.Profile("4a8a2e3b"), "77_profile_dhoni_keeper.png", 1600) { ProfileScreen(vm, "4a8a2e3b") } }
    @Test fun looksGallery() {
        val ids = listOf("ba607b88" to "India", "462411b3" to "India", "919a3be2" to "India", "4a8a2e3b" to "India", "8a75e999" to "Pakistan", "3241e3fd" to "West Indies",
            "gen-1" to "India", "gen-7" to "England", "gen-12" to "South Africa", "gen-31" to "West Indies")
        val gears = listOf(com.cricketcoach.manager.render3d.Gear.BATTING, com.cricketcoach.manager.render3d.Gear.BOWLING, com.cricketcoach.manager.render3d.Gear.BATTING,
            com.cricketcoach.manager.render3d.Gear.KEEPING, com.cricketcoach.manager.render3d.Gear.BATTING, com.cricketcoach.manager.render3d.Gear.FIELDING,
            com.cricketcoach.manager.render3d.Gear.FIELDING, com.cricketcoach.manager.render3d.Gear.BOWLING, com.cricketcoach.manager.render3d.Gear.FIELDING, com.cricketcoach.manager.render3d.Gear.KEEPING)
        val kits = listOf(0xFF1565C0 to 0xFFFF9800, 0xFF0D47A1 to 0xFFFFC107, 0xFF1565C0 to 0xFFFF9800, 0xFFFDD835 to 0xFF0D47A1, 0xFF1B5E20 to 0xFFFFFFFF,
            0xFF7B1F3A to 0xFFFFC107, 0xFF4A148C to 0xFFFFD54F, 0xFF0D2B6B to 0xFFE53935, 0xFF006B3F to 0xFFFFC107, 0xFF7B1F3A to 0xFFFFC107)
        shot("82_looks_gallery.png", 800) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF071029)).padding(10.dp)) {
                androidx.compose.material3.Text("PHASE C · PER-PLAYER MODELS (2D twin of the 3D look) — curated approximate looks + fictional generated looks",
                    color = androidx.compose.ui.graphics.Color(0xFFE9B949), fontSize = 12.sp)
                Row(Modifier.fillMaxSize()) {
                    ids.forEachIndexed { i, (id, c) ->
                        val l = com.cricketcoach.manager.render3d.Look.of(id, c)
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            KitFigure(l, androidx.compose.ui.graphics.Color(kits[i].first), androidx.compose.ui.graphics.Color(kits[i].second), gears[i], Modifier.weight(1f).fillMaxWidth())
                            androidx.compose.material3.Text((Repo.byId[id]?.name?.split(" ")?.last() ?: "Fictional #${id.removePrefix("gen-")}"),
                                color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp, maxLines = 1)
                            androidx.compose.material3.Text("${l.build.name.lowercase()} ${l.heightCm}cm", color = androidx.compose.ui.graphics.Color(0xFF8FA0C0), fontSize = 8.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    // ---- Phase D: Stumpstagram
    @Test fun stumpsPlayers() { val vm = careerVm(Modes.IPL, "F_MI", 6); inShell(vm, Screen.Social, "85_stumps_players.png", 1600) { SocialScreen(vm, initialTab = 3) } }
    @Test fun stumpsProfile() {
        val vm = careerVm(Modes.INTL, "N_IND", 8); val s = vm.save!!
        Stumps.ensureSeeded(s); Stumps.toggleFollow(s, "ba607b88")
        Stumps.playerPost(s, "ba607b88", "CENTURY", 22.0, "Special feeling. Thank you all for the love! 💯 122* (98) v AUS #IND #Century", Random(2))
        inShell(vm, Screen.SocialProfile("ba607b88"), "86_stumps_profile_kohli.png", 1600) { SocialProfileScreen(vm, "ba607b88") }
    }
    @Test fun stumpsFollowing() {
        val vm = careerVm(Modes.IPL, "F_CSK", 10); val s = vm.save!!
        Stumps.ensureSeeded(s); s.userClub().squad.take(4).forEach { Stumps.toggleFollow(s, it) }; Stumps.toggleFollow(s, "462411b3")
        inShell(vm, Screen.Social, "87_stumps_following.png", 1600) { SocialScreen(vm, initialTab = 1) }
    }

    @Test fun settings() { val vm = vm(); shot("50_settings.png") { SettingsScreen(vm) } }
}
