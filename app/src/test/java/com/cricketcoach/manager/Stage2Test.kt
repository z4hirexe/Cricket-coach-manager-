package com.cricketcoach.manager

import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/** Stage 2: 5-day selection windows, 15-16 squads, 50-70 international pools, standalone tournaments. */
class Stage2Test {
    companion object {
        @BeforeClass @JvmStatic fun load() {
            if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
            File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
        }
    }

    @Test fun selectionWindowsAreFiveDaysBeforeFirstMatch() {
        val s = Modes.newCareer(1, "Selector", Modes.INTL, "N_IND", 1.0, 1, Random(7))
        val comps = s.league.filter { Registration.required(s, it) }
        assertTrue(comps.isNotEmpty())
        for (c in comps) {
            val u = c.userTeam!!
            val fx = c.fixtures.filter { it.home == u || it.away == u }.minByOrNull { it.day }!!
            val matchDate = Schedule.fixtureDate(s, c, fx)
            assertEquals(matchDate.minusDays(5).toString(), c.selectionDate)
        }
        assertTrue("calendar shows selection events", Schedule.all(s).any { !it.isMatch && it.title.contains("Squad selection") })
    }

    @Test fun internationalPoolIs50To70AndSquadIs15To16() {
        val s = Modes.newCareer(1, "Selector", Modes.INTL, "N_IND", 1.0, 1, Random(8))
        val tour = s.league.first { it.series && Registration.required(s, it) }
        val pool = Registration.pool(s, tour)
        assertTrue("pool ${pool.size}", pool.size in 50..70)
        val auto = Registration.autoSelect(s, tour)
        assertTrue("auto ${auto.size}", auto.size in 15..16)
        assertEquals(emptyList<String>(), Registration.problems(s, tour, auto))
        assertEquals(null, Registration.register(s, tour, auto))
        val c = s.userClub()
        assertTrue("selected players are in the club squad for the XI", auto.all { it in c.squad })
        assertTrue(Registration.xiPool(s, tour)!!.containsAll(auto))
    }

    @Test fun standaloneTournamentBuildsFixtures() {
        val s = Modes.newCareer(0, "Cup", Modes.INTL, "N_IND", 1.0, 1, Random(9))
        s.league.clear()
        val t = Tournaments.create(s, "T20_WC", "N_IND", null)
        assertNotNull(t)
        assertEquals("N_IND", t.userTeam)
        assertTrue(t.fixtures.isNotEmpty())
        assertTrue(t.teams.size >= 12)
    }
}
