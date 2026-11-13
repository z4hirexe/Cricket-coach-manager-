package com.cricketcoach.manager

import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Phase E: tournament squad registration, lock + replacements, match-day team sheet, bowling roles in the engine. */
class RegistrationTest {
    companion object {
        @BeforeClass @JvmStatic fun load() {
            if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
            File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
        }
        fun career(mode: String, team: String, seed: Int = 3): CareerSave {
            val s = Modes.newCareer(1, "T", mode, team, 1.0, 1, Random(seed))
            s.auction = null
            assertNull(Career.startSeason(s))
            return s
        }
    }

    @Test fun whichCompetitionsNeedRegistration() {
        val ipl = career(Modes.IPL, "F_KKR")
        val r = Registration.rule(ipl, ipl.league.first { it.key == "IPL" })!!
        assertEquals(15, r.min); assertEquals(16, r.max); assertEquals(8, r.overseasMax)
        val dom = career(Modes.DOMESTIC, "S_BEN")
        for (k in listOf("RANJI", "VHT", "SMAT")) assertNotNull(k, Registration.rule(dom, dom.league.first { it.key == k }))
        assertNull(Registration.rule(dom, dom.league.first { it.key == "DULEEP" }))   // zonal side, not the user's team
        val intl = career(Modes.INTL, "N_IND")
        val tours = intl.league.filter { it.series }
        assertTrue(tours.isNotEmpty() && tours.all { Registration.rule(intl, it)?.min == 15 })
        intl.league.firstOrNull { it.key in setOf("ODI_WC", "T20_WC", "CT") && it.userTeam != null }?.let { assertEquals(16, Registration.rule(intl, it)!!.max) }
        // news reminder at season start
        assertTrue(ipl.news.any { it.contains("Squad registration required") })
    }

    @Test fun registrationRulesAndAutoSelect() {
        val s = career(Modes.IPL, "F_KKR")
        val comp = s.league.first()
        val c = s.userClub()
        assertFalse(Registration.registered(s, comp))
        val auto = Registration.autoSelect(s, comp)
        assertTrue("auto ${auto.size}", auto.size in 15..16)
        assertEquals(emptyList<String>(), Registration.problems(s, comp, auto))
        // too few / too many / no keeper / overseas
        assertTrue(Registration.problems(s, comp, auto.take(14)).any { it.contains("at least 15") })
        val noWk = auto.filter { s.players[it]!!.role != "WK" }
        assertTrue(Registration.problems(s, comp, noWk).any { it.contains("wicket-keeper") })
        val outsider = s.players.values.first { it.clubId != c.id && !it.retired }
        assertTrue(Registration.problems(s, comp, auto.drop(1) + outsider.id).any { it.contains("Not available") })
        assertNotNull(Registration.register(s, comp, auto.take(14)))
        assertNull(Registration.register(s, comp, auto))
        assertTrue(Registration.registered(s, comp) && !Registration.locked(s, comp))
        // re-registering before the first match is allowed
        assertNull(Registration.register(s, comp, auto.dropLast(1)))
        assertEquals(auto.size - 1, comp.reg.size)
    }

    @Test fun lockReplacementsAndXiFromRegisteredSquadOnly() {
        val s = career(Modes.IPL, "F_KKR")
        val comp = s.league.first(); val c = s.userClub(); val u = c.id
        // a full 25-man squad (domestic free agents) so there are spares to bring in
        var g = 0
        while (c.squad.size < 25 && g++ < 30) {
            val fa = s.players.values.filter { it.clubId.isEmpty() && !it.retired && !it.academy && it.country == "India" }.maxByOrNull { it.overall() } ?: break
            fa.clubId = c.id; c.squad.add(fa.id)
        }
        val reg = Registration.autoSelect(s, comp).take(18)
        assertNull(Registration.register(s, comp, reg))
        val unregistered = c.squad.filter { it !in reg && s.players[it]?.retired == false }
        assertTrue("need spare players", unregistered.isNotEmpty())
        // put an unregistered player in the XI: the match engine drops him
        c.xi[0] = unregistered.first()
        val fx = comp.fixtures.filter { it.home == u || it.away == u }.minByOrNull { it.day }!!
        val sim = Career.makeSim(s, comp, fx, Random(1))
        assertTrue(sim.xiOf(u).all { it in reg })
        assertEquals(11, sim.xiOf(u).size)
        assertTrue(Registration.sheetErrors(s, comp, c).isEmpty())
        // play the first match -> locked
        sim.autoToss(); sim.simToEnd(); Career.finishFixture(s, comp, fx, sim)
        assertTrue(Registration.locked(s, comp))
        assertNotNull(Registration.register(s, comp, Registration.autoSelect(s, comp)))
        // a fit player cannot be replaced
        val fit = reg.first { s.players[it]!!.injuryDays == 0 }
        assertNotNull(Registration.replace(s, comp, fit, unregistered.first()))
        // injured players can, up to the limit
        val rule = Registration.rule(s, comp)!!
        val spares = Registration.replacementPool(s, comp).filter { !TeamSelector.isOverseas(it, c) }.map { it.id }
        val toInjure = comp.reg.take(rule.replacements + 1)
        toInjure.forEach { s.players[it]!!.injuryDays = 14; s.players[it]!!.injury = "hamstring" }
        var done = 0
        for ((i, out) in toInjure.withIndex()) {
            val inn = spares.getOrNull(i) ?: break
            val err = Registration.replace(s, comp, out, inn)
            if (done < rule.replacements) { assertNull(err); done++; assertTrue(inn in comp.reg && out !in comp.reg) } else assertNotNull(err)
        }
        assertEquals(done, comp.regRepl)
        assertEquals(done, comp.regLog.size)
    }

    @Test fun autoSimulatedMatchesRegisterADefaultSquad() {
        val s = career(Modes.DOMESTIC, "S_BEN")
        val ranji = s.league.first { it.key == "RANJI" }
        val u = s.userClubId
        val fx = ranji.fixtures.filter { it.home == u || it.away == u }.minByOrNull { it.day }!!
        Career.quickSim(s, ranji, fx, Random(5))
        assertTrue(ranji.regDone && ranji.regAuto)
        assertTrue(ranji.reg.size in 15..16)
        assertTrue(Registration.locked(s, ranji))
        // INTL: the national pool is topped up to the minimum with free call-ups
        val n = career(Modes.INTL, "N_IND")
        val tour = n.league.first { it.series }
        val nf = tour.fixtures.first()
        Career.quickSim(n, tour, nf, Random(6))
        assertTrue(tour.reg.size >= 15)
        assertTrue(n.userClub().squad.size >= 15)
    }

    @Test fun teamSheetErrors() {
        val s = career(Modes.IPL, "F_MI")
        val comp = s.league.first(); val c = s.userClub()
        assertNull(Registration.register(s, comp, Registration.autoSelect(s, comp)))
        TeamSelector.autoPick(c, s.players, pool = Registration.xiPool(s, comp))
        if (c.vice !in c.xi || c.vice == c.captain) c.vice = c.xi.first { it != c.captain }
        assertEquals(emptyList<String>(), Registration.sheetErrors(s, comp, c))
        val removed = c.xi.removeAt(10)
        assertTrue(Registration.sheetErrors(s, comp, c).any { it.contains("10/11") })
        c.xi.add(removed)
        s.players[c.xi[3]]!!.injuryDays = 7
        assertTrue(Registration.sheetErrors(s, comp, c).any { it.startsWith("Injured") })
        s.players[c.xi[3]]!!.injuryDays = 0
        c.vice = c.captain
        assertTrue(Registration.sheetErrors(s, comp, c).any { it.contains("vice-captain") })
    }

    @Test fun bowlingRolesChangeWhoBowlsWhen() {
        val s = career(Modes.IPL, "F_CSK")
        val comp = s.league.first(); val c = s.userClub(); val u = c.id
        assertNull(Registration.register(s, comp, Registration.autoSelect(s, comp)))
        TeamSelector.autoPick(c, s.players, pool = Registration.xiPool(s, comp))
        val fx = comp.fixtures.first { it.home == u || it.away == u }
        val plan = c.bowlers.filter { it in c.xi }
        assertTrue(plan.size >= 5)
        val x = plan[3]            // a lower-priority bowler
        fun overs(role: String?, phase: IntRange, n: Int = 40): Int {
            c.bowlRoles.clear(); if (role != null) c.bowlRoles[x] = role
            var cnt = 0
            repeat(n) { i ->
                val m = Career.makeSim(s, comp, fx, Random(100 + i)); m.autoToss(); m.simToEnd()
                cnt += m.events.filter { it.bowler == x && it.batTeam != u && it.over in phase }.map { it.inn to it.over }.toSet().size
            }
            return cnt
        }
        val deathBase = overs(null, 16..19); val deathRole = overs("DEATH", 16..19)
        val ppBase = overs(null, 0..5); val ppRole = overs("NEW", 0..5)
        val partBase = overs(null, 0..9); val partRole = overs("PART", 0..9)
        println("death $deathBase -> $deathRole, powerplay $ppBase -> $ppRole, first 10 overs $partBase -> part-time $partRole")
        assertTrue(deathRole > deathBase + 15)
        assertTrue(ppRole > ppBase + 15)
        assertTrue(partRole < partBase)
        c.bowlRoles.clear()
    }
}
