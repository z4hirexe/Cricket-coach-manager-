package com.cricketcoach.manager

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.CcmTheme
import com.cricketcoach.manager.ui.GameVM
import com.cricketcoach.manager.ui.Screen
import com.cricketcoach.manager.ui.screens.LiveMatchScreen
import com.cricketcoach.manager.ui.screens.boardInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

/** Phase F: every control on the fullscreen match rail really drives the match (Compose UI test on Robolectric). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi")
class LiveBroadcastUiTest {
    @get:Rule val compose = createComposeRule()

    @Before fun data() {
        if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
        File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
    }

    private fun setup(format: String): Pair<GameVM, MatchSim> {
        val vm = GameVM(ApplicationProvider.getApplicationContext<Application>())
        vm.settings.view3d = false            // 2D: no animation gate, every tap applies at once
        val s = Modes.newCareer(1, "T", Modes.INTL, "N_IND", 1.0, 1, Random(5))
        vm.save = s
        val comp = Competition(id = "UT", name = "UI Cup", key = "UT", format = format, kind = "NATION", series = true)
        val m = Career.makeSim(s, comp, Fixture("ut", "N_IND", "N_AUS", "Match", 0, 1), Random(9))
        m.userTeam = "N_IND"
        m.doToss("N_IND", "BAT")
        vm.sim = m
        vm.stack.clear(); vm.stack.add(Screen.Main); vm.stack.add(Screen.Live)
        compose.setContent { CcmTheme { LiveMatchScreen(vm) } }
        compose.waitForIdle()
        return vm to m
    }
    private fun tap(desc: String) { compose.onNodeWithContentDescription(desc).performClick(); compose.waitForIdle() }

    @Test fun railControlsDriveTheMatch() {
        val (vm, m) = setup("T20")
        // Ball
        tap("Ball"); assertEquals(1, m.events.size)
        // Over: completes the current over
        tap("Over"); assertEquals(0, m.cur!!.balls % 6); assertTrue(m.cur!!.balls >= 6)
        // Approach
        tap("Attack"); assertEquals(2, m.club("N_IND").aggression)
        tap("Defend"); assertEquals(0, m.club("N_IND").aggression)
        tap("Normal"); assertEquals(1, m.club("N_IND").aggression)
        // Auto on / off
        compose.mainClock.autoAdvance = false
        tap("Auto"); assertTrue(vm.autoPlay)
        tap("Pause"); assertFalse(vm.autoPlay)
        compose.mainClock.autoAdvance = true
        // Panels open and close
        tap("Commentary"); compose.onNodeWithContentDescription("Close panel").assertExists()
        tap("Highlights"); assertEquals(2, compose.onAllNodesWithText("HIGHLIGHTS").fetchSemanticsNodes().size)   // rail label + panel title
        tap("Scorecard"); assertEquals(2, compose.onAllNodesWithText("SCORECARD").fetchSemanticsNodes().size)
        tap("Wagon"); compose.onNodeWithText("Manhattan").assertExists()
        compose.onNodeWithText("Manhattan").performClick(); compose.waitForIdle(); tap("Wagon")
        compose.onAllNodesWithContentDescription("Close panel").assertCountEquals(0)
        // TV scoreboard shows the batting side's score and the bowler's figures
        assertTrue(compose.onAllNodesWithText(m.cur!!.scoreText()).fetchSemanticsNodes().isNotEmpty())
        // Innings: closes the first innings
        tap("Innings"); assertEquals(2, m.innings.size); assertNotNull(m.cur!!.target)
        assertTrue(boardInfo(m).situation.contains("need"))
        // Sim end -> result card with Continue
        tap("Sim end"); assertTrue(m.finished)
        compose.onNodeWithText("FULL TIME").assertExists()
        compose.onNodeWithText("Continue").assertExists()
        // Ball is disabled once the match is over
        compose.onNodeWithContentDescription("Ball").assertIsNotEnabled()
    }

    @Test fun declareAndTestScoreboard() {
        val (_, m) = setup("TEST")
        compose.onNodeWithContentDescription("Declare").assertIsEnabled()
        tap("Over"); tap("Over")
        val runs = m.cur!!.runs
        tap("Declare")
        assertTrue(m.innings[0].declared)
        assertEquals(2, m.innings.size)
        val bi = boardInfo(m)
        assertTrue(bi.testLine!!.startsWith("2nd INNINGS"))
        assertTrue(bi.situation.contains("trail by $runs") || runs == 0)
        // the user is now bowling: declaring is not possible
        compose.onNodeWithContentDescription("Declare").assertIsNotEnabled()
    }

    @Test fun limitedOversHasNoDeclareButton() {
        setup("ODI")
        compose.onAllNodesWithContentDescription("Declare").assertCountEquals(0)
        compose.onNodeWithText("PROJ").assertExists()
    }
}
