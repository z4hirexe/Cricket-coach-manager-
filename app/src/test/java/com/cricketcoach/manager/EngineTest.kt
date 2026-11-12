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

/** Headless end-to-end tests of the game engine against the real dataset. */
class EngineTest {
    companion object {
        @BeforeClass @JvmStatic fun load() {
            if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
        }
    }

    private fun career(setup: String = "SQUAD", seed: Int = 1): CareerSave =
        Career.newCareer(1, "Test", "Siliguri Strikers", "SIL", "India", 0xFF00C853, 0xFFFFC107, 0, 0, setup, 1.0, 1)

    @Test fun datasetLoaded() {
        assertTrue(Repo.ds.players.size > 800)
        val kohli = Repo.ds.players.first { it.name == "Virat Kohli" }
        assertEquals(15183.0, kohli.off!!["ODI"]!!.runs!!, 0.1)
        assertTrue(Repo.attrs[kohli.id]!!.overall >= 80)
    }

    @Test fun newCareerAndSquads() {
        val s = career()
        val u = s.userClub()
        assertEquals(4, u.division)
        assertTrue("user squad ${u.squad.size}", u.squad.size >= 15)
        assertEquals(32, s.clubs.values.count { it.kind == "CLUB" })
        for (c in s.clubs.values.filter { it.kind == "CLUB" }) assertTrue("${c.name} ${c.squad.size}", c.squad.size >= 15)
        // no player in two clubs
        val all = s.clubs.values.filter { it.kind == "CLUB" }.flatMap { it.squad }
        assertEquals(all.size, all.toSet().size)
        assertEquals(4, s.league.size)
        for (d in 1..4) println("Div $d strengths: " + s.clubs.values.filter { it.kind == "CLUB" && it.division == d }.sortedByDescending { Career.strength(s, it) }.joinToString { "${it.short}${if (it.isUser) "(YOU)" else ""} ${"%.1f".format(Career.strength(s, it))}" })
        val fa = s.players.values.filter { it.clubId.isEmpty() && !it.retired && !it.academy }.sortedByDescending { it.overall() }
        println("Best free agents: " + fa.take(8).joinToString { "${it.name} ${it.overall()} ${Money.fmt(Money.value(it.overall(), it.age(s.year), 1.0))}" })
        println("User balance ${Money.fmt(u.balance)} purse ${Money.fmt(Career.purse(s, u))}")
    }

    @Test fun fullSeasonsWithPromotionAndAuction() {
        val s = career()
        val rnd = Random(7)
        repeat(4) { season ->
            if (s.auction != null) {
                val a = s.auction!!
                if (a.phase == "RETENTION") Auction.confirmRetention(s, a, s.userClub().squad.take(3))
                // user makes some bids then auto-completes
                var g = 0
                while (!a.done && g++ < 30) { Auction.userBid(s, a); Auction.tick(s, a, rnd); Auction.tick(s, a, rnd); Auction.tick(s, a, rnd); Auction.tick(s, a, rnd) }
                Auction.autoComplete(s, a, rnd)
                assertTrue(a.done)
            }
            run {
                var gg = 0
                val fas = s.players.values.filter { it.clubId.isEmpty() && !it.retired && !it.academy }.sortedBy { Money.value(it.overall(), it.age(s.year), s.economy) }
                println("Season ${s.season}: squad ${s.userClub().squad.size}, purse ${Money.fmt(Career.purse(s, s.userClub()))}, FAs ${fas.size}")
                for (fa in fas) { if (s.userClub().squad.size >= 15 || gg++ > 40) break; val r = Career.signFreeAgent(s, fa.id); if (!r.startsWith("Signed")) println("  sign failed: $r") }
            }
            val err = Career.startSeason(s)
            assertEquals("season $season start", null, err)
            var guard = 0
            while (!Career.leagueFinished(s) && guard++ < 60) {
                val day = s.league.flatMap { it.fixtures }.filter { !it.played }.minOf { it.day }
                s.day = day
                val list = Career.fixturesOnDay(s, day)
                for ((c, f) in list) {
                    if (f.home == s.userClubId || f.away == s.userClubId) {
                        // play user's match ball by ball through the live API
                        val m = Career.makeSim(s, c, f, rnd)
                        m.userTeam = s.userClubId
                        m.autoToss()
                        var b = 0
                        while (!m.finished && b++ < 400) { if (b % 3 == 0) m.nextOver() else m.nextBall() }
                        if (!m.finished) m.simToEnd()
                        Career.finishFixture(s, c, f, m)
                    } else Career.quickSim(s, c, f, rnd)
                }
                Career.weeklyUpdate(s, list.map { it.second.home }.toSet(), rnd)
            }
            assertTrue("league finished", Career.leagueFinished(s))
            for (c in s.league) assertNotNull("champion div ${c.division}", c.champion)
            val d1 = s.league.first { it.division == 1 }
            assertTrue(d1.fixtures.any { it.stage == "Final" && it.played })
            Career.endSeason(s, rnd)
            assertEquals("OFFSEASON", s.phase)
            for (d in 1..4) assertEquals("div $d size", 8, s.clubs.values.count { it.kind == "CLUB" && it.division == d })
            Career.beginNextSeason(s)
        }
        // save round-trip
        val json = Gson().toJson(s)
        val back = Gson().fromJson(json, CareerSave::class.java)
        assertEquals(s.players.size, back.players.size)
        assertEquals(s.userClub().name, back.userClub().name)
        println("JSON size after 4 seasons: ${json.length / 1024} KB; records ${s.records.size}; awards ${s.awards.size}")
        println(s.history.joinToString("\n"))
    }

    @Test fun megaAuctionCareer() {
        val s = career("MEGA")
        val a = s.auction
        assertNotNull(a)
        Auction.confirmRetention(s, a!!, emptyList())
        Auction.autoComplete(s, a, Random(3))
        assertTrue(a.done)
        assertTrue("sold ${a.sold.size}", a.sold.size > 100)
    }

    @Test fun allTournamentTypes() {
        val rnd = Random(11)
        for (t in Tournaments.TYPES) {
            val s = career()
            val pool = Tournaments.teamsFor(t.id)
            val teams = when (t.id) { "BILAT" -> listOf("N_IND", "N_AUS"); "CUSTOM" -> listOf("N_IND", "N_PAK", "F_CSK", "F_MI", "N_ENG"); else -> emptyList() }
            for (fmt in if (t.fixedFormat != null) listOf(t.fixedFormat) else listOf("T20", "ODI", "TEST")) {
                if (t.id == "ASIA" && fmt == "TEST") continue
                val comp = Tournaments.create(s, t.id, teams.firstOrNull() ?: pool.first(), fmt, teams, 3, false, "SEMIS")
                var g = 0
                while (comp.stage != "DONE" && g++ < 200) { val fx = CompEngine.nextUnplayed(comp) ?: break; Career.quickSim(s, comp, fx, rnd) }
                assertEquals("${t.id} $fmt finished", "DONE", comp.stage)
                println("${comp.name} [$fmt]: champion ${comp.champion?.let { s.clubs[it]?.name }} • ${comp.fixtures.size} matches")
            }
        }
    }

    @Test fun iplRostersAreReal() {
        val s = career()
        Tournaments.ensureIpl(s)
        for (f in Consts.IPL) {
            val c = s.clubs[f.id]!!
            assertTrue("${f.name} squad ${c.squad.size}", c.squad.size >= 12)
            val realCount = c.squad.count { Repo.byId[it]?.ipl == f.name }
            println("${f.short}: ${c.squad.size} players (${realCount} from real roster) e.g. ${c.squad.take(4).mapNotNull { s.players[it]?.name }}")
        }
    }

    @Test fun matchSanity() {
        val s = career()
        Tournaments.ensureNations(s)
        val rnd = Random(5)
        var totals = 0; var n = 0; var homeWins = 0
        val comp = Competition(id = "X", name = "Test", key = "X", format = "T20", kind = "NATION", series = true)
        repeat(60) {
            val f = Fixture("f$it", "N_IND", "N_ZIM", "Match", 0, 1)
            val m = Career.makeSim(s, comp, f, rnd)
            m.autoToss(); m.simToEnd()
            totals += m.innings.first().runs; n++
            if (m.winner == "N_IND") homeWins++
        }
        for (n in Consts.NATIONS) println("${n.short} strength ${"%.1f".format(Career.strength(s, s.clubs[n.id]!!))} XI: ${s.clubs[n.id]!!.xi.take(5).mapNotNull { s.players[it]?.name }}")
        println("T20 avg first-innings total ${totals / n}, India beat Zimbabwe $homeWins/60")
        assertTrue(totals / n in 120..230)
        assertTrue(homeWins >= 35)
        assertTrue("retired players excluded", s.clubs["N_ENG"]!!.squad.none { s.players[it]!!.retired })
        // Test match
        val tc = Competition(id = "Y", name = "Test", key = "Y", format = "TEST", kind = "NATION", series = true)
        repeat(5) {
            val m = Career.makeSim(s, tc, Fixture("t$it", "N_ENG", "N_AUS", "Match", 0, 1), rnd)
            m.autoToss(); m.simToEnd()
            println("Test: ${m.resultText} • ${m.innings.joinToString { it.scoreText() }}")
            assertTrue(m.finished)
        }
    }

    @Test fun ballEventsForVisualsAndCharts() {
        val s = career()
        Tournaments.ensureNations(s)
        val rnd = Random(9)
        val comp = Competition(id = "Z", name = "Test", key = "Z", format = "T20", kind = "NATION", series = true)
        repeat(10) {
            val m = Career.makeSim(s, comp, Fixture("e$it", "N_AUS", "N_ENG", "Match", 0, 1), rnd)
            m.autoToss(); m.simToEnd()
            for ((i, inn) in m.innings.withIndex()) {
                val ev = m.events.filter { it.inn == i }
                assertEquals("runs in events = innings total", inn.runs, ev.sumOf { it.runs })
                assertEquals("wickets in events", inn.wk, ev.count { it.kind == "WICKET" })
                assertEquals("legal balls", inn.balls, ev.count { it.kind != "WIDE" && it.kind != "NB" })
                assertEquals("fours", inn.bc.sumOf { it.fours }, ev.count { it.kind == "FOUR" } + ev.count { it.kind == "NB" && it.bat == 4 })
                assertTrue(ev.all { it.angle in 0f..360f && it.dist in 0f..1.3f })
            }
            assertTrue(m.events.any { it.highlight })
        }
    }

    @Test fun castFor3DMatchesRealSquads() {
        val s = career()
        Tournaments.ensureNations(s)
        val rnd = Random(21)
        for (fmt in listOf("T20", "ODI")) {
            val comp = Competition(id = "Z$fmt", name = "Test", key = "Z$fmt", format = fmt, kind = "NATION", series = true)
            val m = Career.makeSim(s, comp, Fixture("c$fmt", "N_IND", "N_AUS", "Match", 0, 1), rnd)
            m.autoToss(); m.simToEnd()
            var spin = 0; var left = 0
            for (ev in m.events) {
                val c = com.cricketcoach.manager.ui.screens.castFor(m, ev, s.players)
                assertNotNull(c); c!!
                val inn = m.innings[ev.inn]
                assertEquals(ev.batter, c.strikerId); assertEquals(ev.bowler, c.bowlerId)
                assertTrue("bowler fields for bowling side", ev.bowler in m.xiOf(inn.bowlTeam))
                assertTrue("keeper is a fielder, not the bowler", c.keeperId in m.xiOf(inn.bowlTeam) && c.keeperId != c.bowlerId)
                assertEquals(9, c.fielderIds.size)
                assertTrue(c.nonStrikerId != c.strikerId || inn.wk >= 9)
                if (c.bowlerSpin) spin++; if (c.strikerLeft) left++
                if (ev.kind == "WICKET") assertTrue("dismissal parsable: ${ev.how}", listOf("b ", "lbw", "st ", "c ", "run out").any { ev.how.startsWith(it) || ev.how.contains("run out") })
            }
            assertTrue("some spin bowled", spin > 0)
        }
    }
}
