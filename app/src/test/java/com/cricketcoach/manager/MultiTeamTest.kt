package com.cricketcoach.manager

import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** v2.6 Stage 1: one manager, three teams, merged IPL -> International -> Domestic calendar. */
class MultiTeamTest {
    companion object {
        @BeforeClass @JvmStatic fun load() {
            if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
            File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
        }
    }

    @Test fun multiTeamCareerMergedSeason() {
        val rnd = Random(21)
        val s = Modes.newMultiCareer(1, "Multi Coach", "N_IND", "S_MUM", "F_KKR", 1.0, 1, rnd)
        assertTrue(s.isMultiTeam)
        assertEquals(listOf("F_KKR", "N_IND", "S_MUM"), s.managedTeamIds)
        assertEquals("F_KKR", s.userClubId)
        for (id in s.managedTeamIds) assertTrue("$id is user", s.clubs[id]!!.isUser)
        assertTrue("national squad", s.clubs["N_IND"]!!.squad.size >= 15)
        assertTrue("state squad", s.clubs["S_MUM"]!!.squad.size >= 15)
        assertTrue("franchise squad", s.clubs["F_KKR"]!!.squad.size >= 15)

        val ipl = s.league.filter { it.kind == "FRANCHISE" }
        val intl = s.league.filter { it.kind == "NATION" }
        val dom = s.league.filter { it.kind == "DOMESTIC" }
        assertTrue("has IPL", ipl.isNotEmpty()); assertTrue("has international", intl.isNotEmpty()); assertTrue("has domestic", dom.isNotEmpty())
        assertEquals("F_KKR", ipl.first { it.key == "IPL" }.userTeam)
        assertEquals("N_IND", intl.first { it.series }.userTeam)
        assertEquals("S_MUM", dom.first { it.key == "RANJI" }.userTeam)
        assertEquals("S_MUM", dom.first { it.key == "SMAT" }.userTeam)
        assertEquals("S_MUM", dom.first { it.key == "VHT" }.userTeam)

        val iplEnd = ipl.flatMap { it.fixtures }.maxOf { it.day }
        val intlStart = intl.flatMap { it.fixtures }.minOf { it.day }
        val intlEnd = intl.flatMap { it.fixtures }.maxOf { it.day }
        val domStart = dom.flatMap { it.fixtures }.filter { it.day > 0 }.minOf { it.day }
        assertTrue("IPL before international ($iplEnd < $intlStart)", iplEnd < intlStart)
        assertTrue("international before domestic ($intlEnd < $domStart)", intlEnd < domStart)
        // no managed team is scheduled in two different windows on the same game day
        val byDay = s.league.flatMap { c -> c.fixtures.filter { it.day > 0 }.map { it.day to c } }
            .groupBy({ it.first }, { it.second })
        for ((day, comps) in byDay) {
            val windows = comps.map { it.kind }.toSet()
            assertTrue("day $day mixes windows $windows", windows.size <= 1 || comps.all { it.kind == "DOMESTIC" })
        }

        // registration is required for each managed team's competitions, not just the active one
        assertTrue(Registration.required(s, ipl.first { it.key == "IPL" }))
        assertTrue(Registration.required(s, intl.first { it.series }))
        assertTrue(Registration.required(s, dom.first { it.key == "RANJI" }))
        assertNotNull(Registration.nextPending(s))

        assertEquals(null, Career.startSeason(s))
        val played = HashMap<String, Int>()
        var guard = 0
        while (s.phase != "OFFSEASON" && guard++ < 4000) {
            if (Career.leagueFinished(s)) { Career.endSeason(s, rnd); break }
            val nextDay = s.league.flatMap { it.fixtures }.filter { !it.played }.minOfOrNull { it.day } ?: run { Career.endSeason(s, rnd); null } ?: break
            s.day = nextDay
            val list = Career.fixturesOnDay(s, nextDay)
            assertTrue("day $nextDay has fixtures", list.isNotEmpty())
            for ((c, f) in list) {
                val ut = c.userTeam
                if (ut != null && (f.home == ut || f.away == ut)) played[ut] = (played[ut] ?: 0) + 1
                Career.quickSim(s, c, f, rnd)
            }
            if (s.weeklyDoneDay != nextDay) { Career.weeklyUpdate(s, list.map { it.second.home }.toSet(), rnd); s.weeklyDoneDay = nextDay }
        }
        assertEquals("OFFSEASON", s.phase)
        for (id in s.managedTeamIds) assertTrue("$id played matches (${played[id]})", (played[id] ?: 0) > 0)
        for (c in s.league) {
            assertTrue("${c.name} finished (${c.stage})", c.stage == "DONE")
            assertTrue("${c.name} all played", c.fixtures.all { it.played })
        }
        assertTrue("summary mentions all teams", s.seasonSummary.any { it.contains("India") } && s.seasonSummary.any { it.contains("Mumbai") } && s.seasonSummary.any { it.contains("Kolkata") })

        // save round-trip keeps the multi-team shape and migration is additive
        val back = SaveMigration.migrate(Gson().fromJson(Gson().toJson(s), CareerSave::class.java))
        assertTrue(back.isMultiTeam)
        assertEquals(s.managedTeamIds, back.managedTeamIds)
        assertEquals(s.userClubId, back.userClubId)
        assertEquals(SaveMigration.CURRENT, back.dataVersion)

        // next season still builds the merged calendar and the IPL auction targets the franchise
        Career.beginNextSeason(s)
        assertTrue(s.league.any { it.kind == "FRANCHISE" } && s.league.any { it.kind == "NATION" } && s.league.any { it.kind == "DOMESTIC" })
        s.setActiveTeam("N_IND")
        val a = s.auction ?: Auction.start(s, "MINI")
        s.auction = a
        if (a.phase == "RETENTION") Auction.confirmRetention(s, a, s.marketClub().squad.take(4))
        Auction.autoComplete(s, a, rnd)
        assertEquals("F_KKR", Auction.team(s, a).id)
        assertTrue("franchise squad after auction", s.clubs["F_KKR"]!!.squad.size >= 11)
    }

    @Test fun singleTeamSavesAreUnchanged() {
        val s = Modes.newCareer(2, "Solo", Modes.IPL, "F_CSK", 1.0, 1, Random(4))
        assertTrue(!s.isMultiTeam)
        assertEquals("F_CSK", s.userClubId)
        val back = SaveMigration.migrate(Gson().fromJson(Gson().toJson(s), CareerSave::class.java))
        assertTrue(!back.isMultiTeam)
        assertEquals("F_CSK", back.userClubId)
        assertEquals("F_CSK", back.activeTeamId)
        assertEquals(SaveMigration.CURRENT, back.dataVersion)
    }
}
