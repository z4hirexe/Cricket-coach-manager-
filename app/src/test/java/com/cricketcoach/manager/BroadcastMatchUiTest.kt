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
import com.cricketcoach.manager.ui.screens.BroadcastMatchScreen
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

/**
 * v2.7 broadcast match screen, driven through the real UI:
 * ball tracker tap -> delivery history, automatic over summary, bowler change rules, new-batter selection,
 * auto play stopping at decisions, Test day/session header.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w891dp-h411dp-land-xxhdpi")
class BroadcastMatchUiTest {
    @get:Rule val compose = createComposeRule()

    @Before fun data() {
        if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
        File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
    }

    private fun setup(format: String, toss: String, seed: Int = 9): Pair<GameVM, MatchSim> {
        val vm = GameVM(ApplicationProvider.getApplicationContext<Application>())
        val s = Modes.newCareer(1, "T", Modes.INTL, "N_IND", 1.0, 1, Random(5))
        vm.save = s
        val comp = Competition(id = "UT", name = "UI Cup", key = "UT", format = format, kind = "NATION", series = true)
        val m = Career.makeSim(s, comp, Fixture("ut", "N_IND", "N_AUS", "Match", 0, 1), Random(seed))
        m.userTeam = "N_IND"
        m.doToss("N_IND", toss)
        vm.sim = m
        vm.stack.clear(); vm.stack.add(Screen.Main); vm.stack.add(Screen.Live)
        compose.mainClock.autoAdvance = false
        compose.setContent { CcmTheme { BroadcastMatchScreen(vm) } }
        compose.mainClock.advanceTimeBy(600)
        return vm to m
    }
    private fun tap(desc: String) { compose.onNodeWithContentDescription(desc).performClick(); compose.mainClock.advanceTimeBy(300) }
    private fun has(text: String) = compose.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
    private fun nextBall() = tap("Next ball ›")

    /** Plays legal balls one by one, resolving a new-batter overlay the way the user would, until [until] holds. */
    private fun playUntil(m: MatchSim, max: Int = 400, until: () -> Boolean) {
        var n = 0
        while (!until() && n++ < max && !m.finished) {
            when {
                has("WICKET · NEW BATTER") -> { compose.onNodeWithContentDescription("Send in batter").performClick(); compose.mainClock.advanceTimeBy(300) }
                has("END OF OVER") -> { compose.onNodeWithContentDescription("Next over").performClick(); compose.mainClock.advanceTimeBy(300) }
                else -> nextBall()
            }
        }
    }

    @Test fun ballTapOpensDeliveryHistoryFromEngineData() {
        val (_, m) = setup("T20", "BAT")
        nextBall()
        val e = m.events.first()
        assertEquals(1, m.events.size)
        // tracker chip is labelled with over.ball + outcome, tapping opens the detail dialog for that exact ball
        compose.onNodeWithContentDescription("Ball ${e.over}.${e.ball} ${Delivery.label(e)}").performClick(); compose.mainClock.advanceTimeBy(300)
        assertTrue(has("Over ${e.over}.${e.ball}"))
        assertTrue("bowler + batter shown", has("${m.pn(e.bowler)} to ${m.pn(e.batter)}"))
        assertTrue("line/length come from the engine", e.line.isNotEmpty() && e.length.isNotEmpty())
        assertTrue(has(e.line) || has(e.line.replaceFirstChar { it.uppercase() }))
        if (e.kind == "DOT") assertTrue("dot ball explains the defensive/beaten response", e.shot.isNotEmpty() && has(e.shot))
    }

    @Test fun overSummaryOpensAutomaticallyAndBlocksNextOverUntilConfirmed() {
        val (vm, m) = setup("T20", "BAT")
        playUntil(m) { has("END OF OVER 1") }
        assertTrue(has("END OF OVER 1"))
        val legal = m.events.count { it.legal }
        assertEquals("exactly one over played", 6, legal)
        // the next ball must not be playable while the summary is up
        nextBall()
        assertEquals(6, m.events.count { it.legal })
        assertTrue(has("CRR"))
        compose.onNodeWithContentDescription("Next over").performClick(); compose.mainClock.advanceTimeBy(300)
        assertFalse(has("END OF OVER 1"))
        nextBall()
        assertEquals(7, m.events.count { it.legal })
        assertFalse(vm.autoPlay)
    }

    @Test fun userBowlingMustPickAnEligibleBowlerAndQuotaIsEnforced() {
        val (_, m) = setup("T20", "BOWL")
        // start of innings: user bowls first -> bowler picker appears before the first ball
        assertTrue(has("START OF INNINGS"))
        assertTrue(has("CHANGE BOWLER"))
        compose.onNodeWithContentDescription("Confirm bowler").assertIsNotEnabled()
        val opts = m.bowlerOptions()
        val first = opts.first { it.eligible }
        compose.onNodeWithText(m.players[first.pid]!!.name, substring = true).performClick(); compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithContentDescription("Confirm bowler").assertIsEnabled().performClick(); compose.mainClock.advanceTimeBy(300)
        assertEquals(first.pid, m.cur!!.bowler)
        assertEquals(4, m.bowlerQuota())
        // engine rule: the same bowler cannot bowl consecutive overs
        playUntil(m) { has("END OF OVER 1") }
        assertFalse("consecutive over not allowed", m.bowlerOptions().first { it.pid == first.pid }.eligible)
        assertFalse(m.setBowler(first.pid))
    }

    @Test fun wicketPausesForManualBatterSelection() {
        val (_, m) = setup("T20", "BAT", seed = 3)
        var sawOverlay = false
        var n = 0
        while (!sawOverlay && n++ < 300 && !m.finished) {
            if (has("END OF OVER")) { compose.onNodeWithContentDescription("Next over").performClick(); compose.mainClock.advanceTimeBy(300) }
            else nextBall()
            if (m.needsBatter()) sawOverlay = has("WICKET · NEW BATTER")
        }
        assertTrue("wicket fell and the selection overlay opened", sawOverlay)
        val before = m.cur!!.balls
        nextBall()
        assertEquals("no ball can be bowled until a batter is sent in", before, m.cur!!.balls)
        val avail = m.availableBatters()
        assertTrue(avail.isNotEmpty())
        val pick = avail.last()
        compose.onNodeWithText(m.players[pick.pid]!!.name, substring = true).performClick(); compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithContentDescription("Send in batter").performClick(); compose.mainClock.advanceTimeBy(300)
        assertFalse(m.needsBatter())
        val inn = m.cur!!
        assertTrue("chosen batter is now at the crease", pick.pid == inn.bc[inn.striker].pid || pick.pid == inn.bc[inn.nonStriker].pid)
    }

    @Test fun autoPlayStopsAtTheEndOfTheOver() {
        val (vm, m) = setup("T20", "BAT")
        tap("▶ Auto play")
        assertTrue(vm.autoPlay)
        compose.mainClock.advanceTimeBy(60_000)
        assertTrue("auto play halted at a decision point", has("END OF OVER") || has("WICKET · NEW BATTER"))
        val legal = m.events.count { it.legal }
        assertTrue("stopped within the first over, not beyond", legal <= 6)
        compose.mainClock.advanceTimeBy(30_000)
        assertEquals("no progress while the overlay is up", legal, m.events.count { it.legal })
    }

    @Test fun testMatchHeaderShowsDayAndSession() {
        val (_, _) = setup("TEST", "BAT")
        assertTrue(has("DAY 1 • SESSION 1"))
    }
}
