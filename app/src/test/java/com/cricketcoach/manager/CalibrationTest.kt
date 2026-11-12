package com.cricketcoach.manager

import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * v2.5 Phase G — score calibration guard. Simulates many matches per format and checks that the
 * average first-innings totals / draw rates stay in realistic bands (wider than the tuning targets
 * so the test is not flaky; tune with tools/simharness).
 */
class CalibrationTest {
    companion object {
        @BeforeClass @JvmStatic fun load() {
            if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
            File("src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
        }
        private val TOP = listOf("N_IND", "N_AUS", "N_ENG", "N_SA", "N_NZ", "N_PAK", "N_SL", "N_WI")
    }

    private class Stat { var n = 0; var first = 0L; var maxI = 0; var draws = 0
        fun add(m: MatchSim) { n++; first += m.innings[0].runs; maxI = maxOf(maxI, m.innings.maxOf { it.runs }); if (m.draw) draws++ }
        val avg get() = first.toDouble() / n
        val drawPct get() = draws * 100 / n
    }

    private fun intl(fmt: String, n: Int): Stat {
        val s = Modes.newCareer(1, "C", Modes.INTL, "N_IND", 1.0, 1, Random(1))
        val comp = Competition(id = "C$fmt", name = "Calib", key = "C$fmt", format = fmt, kind = "NATION", series = true)
        val rnd = Random(77); val st = Stat()
        repeat(n) { i ->
            val h = TOP[rnd.nextInt(TOP.size)]; var a = TOP[rnd.nextInt(TOP.size)]; while (a == h) a = TOP[rnd.nextInt(TOP.size)]
            val m = Career.makeSim(s, comp, Fixture("c$i", h, a, "Match", 0, 1), Random(rnd.nextLong()))
            m.autoAll = true; m.simToEnd(); st.add(m)
        }
        return st
    }

    private fun domestic(key: String, n: Int): Stat {
        val d = Modes.newCareer(1, "C", Modes.DOMESTIC, "S_BEN", 1.0, 1, Random(3)); Career.startSeason(d)
        val c = d.league.first { it.key == key }
        val rnd = Random(91); val st = Stat()
        for (f in c.fixtures.filter { it.home.startsWith("S_") && it.away.startsWith("S_") }.take(n)) {
            val m = Career.makeSim(d, c, f, Random(rnd.nextLong())); m.autoAll = true; m.simToEnd(); st.add(m)
        }
        return st
    }

    private fun check(label: String, st: Stat, range: ClosedFloatingPointRange<Double>, maxCap: Int, drawMax: Int = 100) {
        println("$label n=${st.n} avg1st=${"%.1f".format(st.avg)} max=${st.maxI} draw=${st.drawPct}%")
        assertTrue("$label avg ${st.avg} not in $range", st.avg in range)
        assertTrue("$label max innings ${st.maxI} > $maxCap", st.maxI <= maxCap)
        assertTrue("$label draws ${st.drawPct}% > $drawMax%", st.drawPct <= drawMax)
    }

    @Test fun t20Totals() = check("Intl T20", intl("T20", 80), 145.0..185.0, 300)
    @Test fun odiTotals() = check("Intl ODI", intl("ODI", 60), 240.0..300.0, 480)
    @Test fun testTotals() = check("Intl TEST", intl("TEST", 24), 270.0..400.0, 900, drawMax = 45)

    @Test fun iplTotals() {
        val ip = Modes.newCareer(1, "C", Modes.IPL, "F_MI", 1.0, 1, Random(2)); ip.auction = null; Career.startSeason(ip)
        val ipl = ip.league.first(); val rnd = Random(5); val st = Stat()
        for (f in ipl.fixtures.take(60)) { val m = Career.makeSim(ip, ipl, f, Random(rnd.nextLong())); m.autoAll = true; m.simToEnd(); st.add(m) }
        check("IPL", st, 165.0..210.0, 300)
    }

    @Test fun smatTotals() = check("SMAT", domestic("SMAT", 60), 160.0..205.0, 300)
    @Test fun vhtTotals() = check("VHT", domestic("VHT", 60), 255.0..315.0, 480)
    @Test fun ranjiTotals() = check("RANJI", domestic("RANJI", 24), 270.0..390.0, 950, drawMax = 70)
}
