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
// own SDK sandbox so nothing static (audio mixer, loopers) can leak into the preview renders
@Config(sdk = [33], qualifiers = "w891dp-h411dp-land-xxhdpi")
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
        compose.mainClock.autoAdvance = false     // the screen has looping animations (LIVE dot, on-air meter)
        compose.setContent { CcmTheme { LiveMatchScreen(vm) } }
        compose.mainClock.advanceTimeBy(500)
        return vm to m
    }
    private fun tap(desc: String) { compose.onNodeWithContentDescription(desc).performClick(); compose.mainClock.advanceTimeBy(400) }

    @Test fun railControlsDriveTheMatch() {
        val (vm, m) = setup("T20")
        // Ball
        tap("Ball"); assertEquals(1, m.events.size)
        compose.onNodeWithContentDescription("Over").assertIsEnabled()
        // Over: completes the current over
        val b0 = m.cur!!.balls
        tap("Over"); assertTrue("over played: $b0 -> ${m.cur!!.balls}", m.cur!!.balls >= b0 + 5 || m.innings.size > 1)
        // Approach
        tap("Attack"); assertEquals(2, m.club("N_IND").aggression)
        tap("Defend"); assertEquals(0, m.club("N_IND").aggression)
        tap("Normal"); assertEquals(1, m.club("N_IND").aggression)
        // Auto on / off
        tap("Auto"); assertTrue(vm.autoPlay)
        tap("Pause"); assertFalse(vm.autoPlay)
        // Panels open and close
        tap("Commentary"); compose.onNodeWithContentDescription("Close panel").assertExists()
        tap("Highlights"); assertEquals(2, compose.onAllNodesWithText("HIGHLIGHTS").fetchSemanticsNodes().size)   // rail label + panel title
        tap("Scorecard"); assertEquals(2, compose.onAllNodesWithText("SCORECARD").fetchSemanticsNodes().size)
        tap("Wagon"); compose.onNodeWithText("Manhattan").assertExists()
        compose.onNodeWithText("Manhattan").performClick(); compose.mainClock.advanceTimeBy(300); tap("Wagon")
        compose.onAllNodesWithContentDescription("Close panel").assertCountEquals(0)
        // TV scoreboard shows the batting side's score and the bowler's figures
        assertTrue(compose.onAllNodesWithText(m.cur!!.scoreText()).fetchSemanticsNodes().isNotEmpty())
        // Innings: closes the first innings
        tap("Innings"); assertEquals(2, m.innings.size); assertNotNull(m.cur!!.target)
        assertTrue(boardInfo(m).situation.contains("need"))
        // Sim end -> result card with Continue
        tap("Sim end"); assertTrue(m.finished)
        compose.onNodeWithText("FULL TIME").assertExists()
        compose.onNodeWithText("Continue", ignoreCase = true).assertExists()
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

    /** v2.6: no Back mid-match - the pause menu offers Resume / Settings / Exit match; exit asks first and loses nothing. */
    @Test fun pauseMenuResumeAndConfirmedExit() {
        val (vm, m) = setup("T20")
        tap("Ball"); tap("Ball")
        val balls = m.events.size
        tap("Pause menu")
        compose.onNodeWithText("MATCH PAUSED").assertExists()
        compose.onNodeWithContentDescription("Settings").assertExists()
        tap("Resume")
        compose.onAllNodesWithText("MATCH PAUSED").assertCountEquals(0)
        assertEquals(balls, m.events.size)
        // exit: first a confirmation, cancel keeps the match open
        tap("Pause menu"); tap("Exit match")
        compose.onNodeWithText("Exit match?").assertExists()
        compose.onNodeWithContentDescription("Cancel").performClick(); compose.mainClock.advanceTimeBy(400)
        assertFalse(Screen.Calendar in vm.stack)     // (the async account check may push Login in Robolectric - only the exit must not happen)
        compose.onAllNodesWithText("Exit match?").assertCountEquals(0)
        assertEquals(balls, m.events.size)
        // confirm: back to the calendar, the match is kept exactly at this ball
        tap("Exit match")
        compose.onAllNodesWithContentDescription("Exit match").onLast().performClick(); compose.mainClock.advanceTimeBy(400)
        assertEquals(Screen.Calendar, vm.stack.last())
        assertSame(m, vm.sim)
        assertEquals(balls, m.events.size)
        assertFalse(m.finished)
    }

    @Test fun limitedOversHasNoDeclareButton() {
        setup("ODI")
        compose.onAllNodesWithContentDescription("Declare").assertCountEquals(0)
        compose.onNodeWithText("PROJ").assertExists()
    }
}
