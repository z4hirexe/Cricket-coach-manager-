package com.cricketcoach.manager

import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** v2.7 broadcast UI: delivery detail on every ball, manual bowler / batter decisions, cricket-rule eligibility. */
class BroadcastEngineTest {
    companion object {
        @BeforeClass @JvmStatic fun load() {
            if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
        }
    }

    private fun career(): CareerSave = Career.newCareer(1, "Test", "Siliguri Strikers", "SIL", "India", 0xFF00C853, 0xFFFFC107, 0, 0, "SQUAD", 1.0, 1)

    private fun sim(format: String, seed: Int = 3): MatchSim {
        val s = career(); Tournaments.ensureNations(s)
        val comp = Competition(id = "X", name = "Test", key = "X", format = format, kind = "NATION", series = true)
        return Career.makeSim(s, comp, Fixture("f", "N_IND", "N_AUS", "Match", 0, 1), Random(seed))
    }

    @Test fun everyBallCarriesDeliveryDetail() {
        val m = sim("T20"); m.autoToss(); m.simToEnd()
        assertTrue(m.events.size > 200)
        for (e in m.events) {
            assertTrue("line ${e.over}.${e.ball}", e.line.isNotEmpty()); assertTrue(e.length.isNotEmpty()); assertTrue(e.shot.isNotEmpty()); assertTrue(e.speed in 60..170)
            assertTrue(e.pitchX in -1f..1f); assertTrue(e.pitchY in -0.1f..0.95f)
            assertEquals(e.kind == "WIDE" || e.kind == "NB", !e.legal)
            if (e.kind == "WIDE") assertEquals("No shot offered", e.shot)
            if (e.kind == "SIX") assertFalse("a six is never a defensive shot: ${e.shot}", e.shot.contains("defensive", true))
            if (e.kind == "DOT") assertTrue(e.note.startsWith("No run"))
            if (e.kind == "WICKET" && e.how.startsWith("b ")) assertTrue(e.note.contains("stumps"))
            if (e.shot == "Left alone") assertTrue(e.line.contains("OFF"))
        }
        // over grouping: legal deliveries per completed over == 6
        val first = m.overEvents(0, 0)
        assertEquals(6, first.count { it.legal })
        // running score recorded on the event
        val last = m.events.last()
        assertEquals(m.cur!!.runs, last.scoreRuns)
    }

    @Test fun manualBowlerDecisionPausesAndFollowsRules() {
        val m = sim("T20"); m.userTeam = "N_AUS"; m.manualDecisions = true
        m.doToss("N_IND", "BAT")       // India bat, Australia (user) bowl
        assertTrue(m.needsBowler()); assertFalse(m.needsBatter())
        val before = m.events.size
        m.nextBall(); assertEquals("engine must wait for the bowler", before, m.events.size)
        val opts = m.bowlerOptions()
        assertEquals(11, opts.size)
        assertTrue(opts.all { it.eligible })
        val first = opts.first { it.eligible }.pid
        assertTrue(m.setBowler(first)); assertFalse(m.needsBowler())
        // bowl the whole over
        var g = 0; while (m.cur!!.overStarted && g++ < 20) { if (m.needsBatter()) m.sendBatter(m.availableBatters().first().pid); m.nextBall() }
        assertEquals(6, m.cur!!.balls)
        assertTrue(m.needsBowler())
        assertNotNull(m.lastOverDone); assertEquals(1, m.lastOverDone!!.second)
        // consecutive overs are not allowed
        val again = m.bowlerOptions().first { it.pid == first }
        assertFalse(again.eligible); assertTrue(again.reason.contains("last over"))
        assertFalse(m.setBowler(first))
        // quota: 4 overs each in T20
        assertEquals(4, m.bowlerQuota())
        // bulk helpers still work (auto decisions) and end the match
        m.simToEnd(); assertTrue(m.finished)
    }

    @Test fun manualBatterDecisionAfterWicket() {
        val m = sim("ODI", 11); m.userTeam = "N_IND"; m.manualDecisions = true
        m.doToss("N_IND", "BAT")
        var guard = 0
        while (!m.needsBatter() && !m.finished && guard++ < 2000) m.nextBall()
        assertTrue("a wicket should fall", m.needsBatter())
        val inn = m.cur!!
        val sizeBefore = m.events.size
        m.nextBall(); assertEquals("engine must wait for the batter", sizeBefore, m.events.size)
        val avail = m.availableBatters()
        assertTrue(avail.size >= 2)
        assertTrue(avail.none { it.batted || it.out })
        // pick someone out of order (the last available batter) - allowed, order is rewritten
        val pick = avail.last().pid
        assertTrue(m.sendBatter(pick))
        assertFalse(m.needsBatter())
        assertEquals(pick, inn.bc[inn.nextIdx - 1].pid)
        assertTrue(inn.bc[inn.nextIdx - 1].batted)
        assertTrue(pick == inn.bc[inn.striker].pid || pick == inn.bc[inn.nonStriker].pid)
        // cannot send a batter twice
        assertFalse(m.sendBatter(pick))
        // match continues normally
        m.nextBall(); assertTrue(m.events.size > sizeBefore)
        m.simToEnd(); assertTrue(m.finished)
        // every batter in the card is unique
        for (i in m.innings) assertEquals(i.bc.size, i.bc.map { it.pid }.toSet().size)
    }

    @Test fun aiSidesAreNeverBlockedByManualMode() {
        val m = sim("T20", 5); m.userTeam = ""; m.manualDecisions = true
        m.autoToss(); var g = 0
        while (!m.finished && g++ < 3000) m.nextBall()
        assertTrue(m.finished)
    }
}
