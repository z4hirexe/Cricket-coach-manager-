package com.cricketcoach.manager

import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

/** Phase A: the cricket calendar maps every engine fixture to a date and adds off-field events. */
class ScheduleTest {
    @Before fun data() {
        if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
        File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
    }

    private fun career(mode: String, team: String): CareerSave {
        val s = Modes.newCareer(1, "T", mode, team, 1.0, 1, Random(3))
        s.auction = null
        assertNull(Career.startSeason(s))
        return s
    }

    @Test fun everyFixtureHasADateInOrder() {
        for ((mode, team) in listOf(Modes.IPL to "F_KKR", Modes.INTL to "N_IND", Modes.DOMESTIC to "S_BEN")) {
            val s = career(mode, team)
            val all = s.league.flatMap { c -> c.fixtures.map { c to it } }
            val m = Schedule.matches(s)
            assertEquals("$mode: one calendar entry per fixture", all.size, m.size)
            for ((c, f) in all) {
                val d = Schedule.dateOf(s, f.day)
                assertEquals("$mode round-trip day ${f.day}", f.day.coerceAtLeast(1), Schedule.dayOf(s, d))
                assertTrue(m.any { it.fx === f && it.date == d && it.comp === c })
            }
            // later game days never come before earlier ones
            val byDay = all.map { it.second.day }.distinct().sorted().map { Schedule.dateOf(s, it) }
            assertEquals(byDay, byDay.sorted())
            assertTrue("$mode user matches flagged", m.any { it.mine })
        }
    }

    @Test fun iplHasAuctionTransferTrainingAndDisclaimer() {
        val s = career(Modes.IPL, "F_KKR")
        val ev = Schedule.events(s)
        assertTrue(ev.any { it.cat == Schedule.AUCTION })
        assertTrue(ev.any { it.cat == Schedule.TRANSFER && it.endDate.isAfter(it.date) })
        assertTrue(ev.any { it.cat == Schedule.TRAINING })
        assertTrue(ev.any { it.cat == Schedule.EVENT && it.title.contains("opener") })
        assertEquals(LocalDate.of(s.year, 3, 22), Schedule.seasonStart(s))
        assertTrue(Schedule.DISCLAIMER.contains("fictional", ignoreCase = true))
        // categories
        assertTrue(Schedule.matches(s).all { Schedule.IPL in it.cats })
        // no training day on a match day of the user
        val mine = Schedule.matches(s).filter { it.mine }.map { it.date }.toSet()
        assertTrue(ev.filter { it.cat == Schedule.TRAINING }.none { it.date in mine })
    }

    @Test fun intlIccEventIsATournamentAndMonthFilterWorks() {
        val s = career(Modes.INTL, "N_IND")
        val m = Schedule.matches(s)
        assertTrue(m.any { Schedule.TOURN in it.cats && Schedule.INTL in it.cats })
        val first = m.minByOrNull { it.date }!!
        val inMonth = Schedule.inMonth(m, YearMonth.from(first.date))
        assertTrue(inMonth.contains(first))
        assertTrue(Schedule.on(m, first.date).contains(first))
        assertFalse(Schedule.events(s).any { it.cat == Schedule.AUCTION })   // no auction in international mode
    }

    @Test fun todayFollowsTheEngineDay() {
        val s = career(Modes.DOMESTIC, "S_BEN")
        val rnd = Random(9)
        repeat(3) {
            val d = s.league.flatMap { it.fixtures }.filter { !it.played }.minOf { it.day }
            s.day = d
            for ((c, f) in Career.fixturesOnDay(s, d)) Career.quickSim(s, c, f, rnd)
        }
        assertEquals(Schedule.dateOf(s, s.day), Schedule.today(s))
        val played = Schedule.matches(s).filter { it.fx!!.played }
        assertTrue(played.isNotEmpty())
        assertTrue(played.all { Schedule.status(s, it.comp!!, it.fx!!, null).let { st -> st == "RESULT" || st == "NO RESULT" } })
    }
}
