package com.cricketcoach.manager.preview

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.ui.CcmTheme
import com.cricketcoach.manager.ui.GameVM
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
        compose.mainClock.advanceTimeBy(500)
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
        Career.startSeason(s)
        repeat(days) {
            val d = s.league.flatMap { it.fixtures }.filter { !it.played }.minOf { it.day }
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
    @Test fun dashboardDomestic() { val vm = careerVm(); shot("30_dashboard_bengal.png") { Dashboard(vm) } }
    @Test fun tableDomestic() { val vm = careerVm(days = 8); shot("31_points_table.png") { TableScreen(vm) } }
    @Test fun social() { val vm = careerVm(days = 10); shot("40_social.png") { SocialScreen(vm) } }
    @Test fun selection() { val vm = careerVm(Modes.INTL, "N_IND", 0); shot("32_selection_india.png") { SelectionScreen(vm) } }
    @Test fun settings() { val vm = vm(); shot("50_settings.png") { SettingsScreen(vm) } }
}
