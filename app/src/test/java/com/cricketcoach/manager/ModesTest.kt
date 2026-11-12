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

/** Plays complete seasons of every career type exactly like GameVM.advanceDay does. */
class ModesTest {
    companion object {
        @BeforeClass @JvmStatic fun load() {
            if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
            File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
        }
    }

    /** Returns number of user matches played. */
    private fun playSeason(s: CareerSave, rnd: Random): Int {
        assertEquals(null, Career.startSeason(s))
        var userMatches = 0; var guard = 0
        while (s.phase != "OFFSEASON" && guard++ < 3000) {
            if (Career.leagueFinished(s)) { Career.endSeason(s, rnd); break }
            val nextDay = s.league.flatMap { it.fixtures }.filter { !it.played }.minOfOrNull { it.day } ?: run { Career.endSeason(s, rnd); null } ?: break
            s.day = nextDay
            val list = Career.fixturesOnDay(s, nextDay)
            assertTrue("day $nextDay has fixtures", list.isNotEmpty())
            for ((c, f) in list) {
                if (f.home == s.userClubId || f.away == s.userClubId) userMatches++
                Career.quickSim(s, c, f, rnd)
            }
            if (s.weeklyDoneDay != nextDay) { Career.weeklyUpdate(s, list.map { it.second.home }.toSet(), rnd); s.weeklyDoneDay = nextDay }
        }
        assertEquals("OFFSEASON", s.phase)
        return userMatches
    }

    private fun check(mode: String, team: String, seasons: Int = 2) {
        val rnd = Random(11)
        val s = Modes.newCareer(1, "Tester", mode, team, 1.0, 1, rnd)
        assertEquals(mode, s.mode)
        val u = s.userClub()
        println("== ${Modes.label(mode)}: ${u.name}, squad ${u.squad.size}, comps ${s.league.joinToString { "${it.name}[${it.teams.size}]" }}")
        assertTrue("squad ${u.squad.size}", u.squad.size >= 15)
        repeat(seasons) { n ->
            val um = playSeason(s, rnd)
            for (c in s.league) {
                assertTrue("${c.name} finished (${c.stage})", c.stage == "DONE")
                if (!c.series) assertNotNull("${c.name} champion", c.champion)
                assertTrue("${c.name} all played", c.fixtures.all { it.played })
            }
            println("Season ${s.season} (${s.year}): user matches $um • " + s.league.joinToString { "${it.name} → ${s.clubs[it.champion]?.name}" })
            println("   followers ${s.followers}, posts ${s.posts.size}, board ${s.boardRating}%, kinds ${s.posts.map { it.kind }.toSet()}")
            assertTrue("user played", um > 0)
            assertTrue("social posts", s.posts.size >= 5)
            // the save must stay serialisable (cloud save) and small
            val json = Gson().toJson(s)
            assertTrue("save ${json.length}", json.length < 8_000_000)
            if (n < seasons - 1) {
                Career.beginNextSeason(s)
                if (s.mode == Modes.IPL) { // pre-season mini auction (as the Auction room does), user lets AI auto-complete
                    if (s.auction == null) s.auction = Auction.start(s, "MINI")
                    val a = s.auction!!; if (a.phase == "RETENTION") Auction.confirmRetention(s, a, s.userClub().squad.take(4))
                    Auction.autoComplete(s, a, rnd)
                    var g = 0
                    while (s.userClub().squad.size < 15 && g++ < 10) { val fa = s.players.values.filter { it.clubId.isEmpty() && !it.retired && !it.academy }.maxByOrNull { it.overall() } ?: break; fa.clubId = s.userClubId; fa.contractYears = 2; s.userClub().squad.add(fa.id) }
                }
                println("   next season squad ${s.userClub().squad.size}")
                assertTrue(s.league.isNotEmpty()); assertTrue("squad after renewals ${s.userClub().squad.size}", s.userClub().squad.size >= 11) }
        }
    }

    @Test fun internationalIndia() = check(Modes.INTL, "N_IND")
    @Test fun domesticBengal() {
        val s = Modes.newCareer(1, "T", Modes.DOMESTIC, "S_BEN", 1.0, 1, Random(3))
        val ben = s.userClub()
        val real = ben.squad.mapNotNull { s.players[it] }.filter { !it.fictional }
        println("Bengal real players: ${real.map { it.name }}")
        assertTrue("Bengal has real players", real.size >= 3)
        assertTrue("fictional players are labelled", ben.squad.mapNotNull { s.players[it] }.filter { it.fictional }.none { it.real })
        val keys = s.league.map { it.key }.toSet()
        println("Domestic comps: ${s.league.map { "${it.key}:${it.format}" }}")
        for (k in listOf("DULEEP", "RANJI", "SMAT", "VHT")) assertTrue("has $k", k in keys)
        check(Modes.DOMESTIC, "S_BEN")
    }
    @Test fun iplKkr() {
        val s = Modes.newCareer(1, "T", Modes.IPL, "F_KKR", 1.0, 1, Random(3))
        val ipl = s.league.first()
        assertEquals(10, ipl.teams.size)
        println("IPL fixtures at start: ${ipl.fixtures.size}, stages ${ipl.fixtures.map { it.stage }.toSet()}, max per day ${ipl.fixtures.groupBy { it.day }.values.maxOf { it.size }}")
        assertEquals(70, ipl.fixtures.size)
        check(Modes.IPL, "F_KKR")
    }

    @Test fun commentaryHindiEnglish() {
        val s = Modes.newCareer(1, "T", Modes.INTL, "N_IND", 1.0, 1, Random(5))
        val comp = s.league.first { it.format == "T20" || it.format == "ODI" }
        val f = comp.fixtures.first()
        val m = Career.makeSim(s, comp, f, Random(5))
        m.autoToss(); m.simToEnd()
        for (lang in listOf("EN", "HI", "MIX")) for (style in 0..2) {
            val c = Commentary(lang, style, Random(1))
            val lines = m.events.map { c.line(it, m, live = true) }
            val texts = lines.map { it.text }
            assertTrue(lines.all { it.text.isNotBlank() })
            val distinct = lines.map { it.text }.toSet().size
            if (lang == "HI") assertTrue("Devanagari", lines.count { l -> l.text.any { it in '\u0900'..'\u097F' } } > lines.size / 2)
            println("$lang/$style: ${lines.size} balls, $distinct distinct, ${texts.indices.count { i -> (maxOf(0, i - 6) until i).any { j -> texts[j] == texts[i] } }} near-repeats • " + lines.filter { it.kind == "SIX" || it.kind == "WICKET" }.take(2).joinToString(" | ") { it.text })
            val repeats = texts.indices.count { i -> (maxOf(0, i - 6) until i).any { j -> texts[j] == texts[i] } }
            assertTrue("repeats in 6-ball window: $repeats", repeats <= lines.size / 100)
            assertTrue("variety $distinct/${lines.size}", distinct > lines.size * 0.3)
        }
    }

    @Test fun firstClassTotalsRealistic() {
        val s = Modes.newCareer(1, "T", Modes.DOMESTIC, "S_BEN", 1.0, 1, Random(9))
        val ranji = s.league.first { it.key == "RANJI" }
        val totals = mutableListOf<Int>(); var draws = 0
        for (f in ranji.fixtures.take(24)) {
            val m = Career.makeSim(s, ranji, f, Random(f.id.hashCode()))
            m.autoToss(); m.simToEnd()
            totals += m.innings.filter { it.wk == 10 || it.balls > 0 }.map { it.runs }
            if (m.winner == null) draws++
        }
        val sorted = totals.sorted()
        println("Ranji innings: n=${totals.size} median ${sorted[sorted.size / 2]} max ${sorted.last()} >600: ${totals.count { it > 600 }} draws $draws/24")
        assertTrue("median first-class innings ${sorted[sorted.size / 2]}", sorted[sorted.size / 2] in 180..420)
        assertTrue("max ${sorted.last()}", sorted.last() < 800)
    }

    @Test fun newTeamBuilder() {
        val s = Career.newCareer(2, "Coach", "Siliguri Tigers", "SIT", "India", 0xFF0D47A1, 0xFFE9B949, 2, 1, "SQUAD", 1.0, 1)
        val u = s.userClub(); u.stadium = "Kanchenjunga Stadium"; u.logoFile = "/data/x.jpg"
        val back = Gson().fromJson(Gson().toJson(s), CareerSave::class.java)
        assertEquals("Kanchenjunga Stadium", back.userClub().stadium)
        assertEquals("/data/x.jpg", back.userClub().logoFile)
        assertEquals(Modes.CLUB, back.mode)
    }
}
