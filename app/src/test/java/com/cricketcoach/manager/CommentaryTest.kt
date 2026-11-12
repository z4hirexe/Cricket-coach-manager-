package com.cricketcoach.manager

import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Commentary 2.0: full matches in every language/style, special calls, exact over cards, no repeats, plus synthetic hat-trick / maiden. */
class CommentaryTest {
    companion object {
        @BeforeClass @JvmStatic fun load() {
            if (!Repo.isLoaded()) File("src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
        }
    }

    private fun save(): CareerSave {
        val s = Career.newCareer(1, "Test", "Siliguri Strikers", "SIL", "India", 0xFF00C853, 0xFFFFC107, 0, 0, "SQUAD", 1.0, 1)
        Tournaments.ensureNations(s); return s
    }
    private fun match(s: CareerSave, fmt: String, seed: Int, h: String = "N_IND", a: String = "N_AUS"): MatchSim {
        val comp = Competition(id = "C$fmt$seed", name = "Border Cup", key = "C", format = fmt, kind = "NATION", series = true)
        val m = Career.makeSim(s, comp, Fixture("f$seed", h, a, "Match", 0, 1), Random(seed))
        m.autoToss(); m.simToEnd(); return m
    }
    private fun run(m: MatchSim, lang: String, style: Int, expert: Boolean = true): List<ComLine> {
        val c = Commentary(lang, style, Random(3), expert)
        return m.events.flatMap { c.process(it, m) }
    }
    private val devanagari = '\u0900'..'\u097F'

    @Test fun fullMatchesAllLanguages() {
        val s = save()
        val sample = StringBuilder()
        for ((fi, fmt) in listOf("T20", "ODI", "TEST").withIndex()) {
            val m = match(s, fmt, 11 + fi)
            val ballMain = m.events.size
            for (lang in listOf("EN", "HI", "HG", "MIX")) for (style in 0..2) {
                val lines = run(m, lang, style)
                val ctx = "$fmt/$lang/$style"
                // every delivery gets exactly one main line
                val mains = lines.filter { it.kind in setOf("DOT", "RUN", "FOUR", "SIX", "WIDE", "NB", "WICKET", "APPEAL") && it.speaker == 0 }
                assertEquals("$ctx main lines", ballMain, mains.size)
                // no unreplaced placeholders
                lines.forEach { assertTrue("$ctx placeholder in: ${it.text} / ${it.detail}", !it.text.contains('{') && !it.text.contains('}') && !it.detail.contains('{')) }
                // language
                val spokenTexts = lines.filter { it.kind != "RESULT" && it.kind != "OVER" }
                if (lang == "HI") assertTrue("$ctx Devanagari", spokenTexts.count { l -> l.text.any { it in devanagari } } > spokenTexts.size * 0.8)
                if (lang == "HG" || lang == "EN") assertTrue("$ctx no Devanagari", spokenTexts.none { l -> l.text.any { it in devanagari } })
                if (lang == "MIX") assertTrue("$ctx analyst in Hindi", lines.filter { it.kind == "EXPERT" }.all { l -> l.text.any { it in devanagari } })
                // no back-to-back repeats of the main call within 6 balls
                val texts = mains.map { it.text }
                val repeats = texts.indices.count { i -> (maxOf(0, i - 6) until i).any { j -> texts[j] == texts[i] } }
                assertTrue("$ctx repeats $repeats", repeats <= texts.size / 100 + 1)
                // over cards = completed overs; milestones match the scorecard
                val overs = lines.count { it.kind == "OVER" }
                val completed = m.events.count { it.kind != "WIDE" && it.kind != "NB" && it.ball == 6 }
                assertTrue("$ctx over cards $overs vs $completed", overs in completed - 1..completed)
                val fifties = m.innings.sumOf { inn -> inn.bc.count { it.runs >= 50 } }
                val hundreds = m.innings.sumOf { inn -> inn.bc.count { it.runs >= 100 } }
                assertEquals("$ctx FIFTY calls", fifties, lines.count { it.tag == "FIFTY" })
                assertEquals("$ctx HUNDRED calls", hundreds, lines.count { it.tag == "HUNDRED" })
                val simMaidens = m.innings.sumOf { inn -> inn.bw.values.sumOf { it.maidens } }
                val maidenCalls = lines.count { it.tag == "MAIDEN" || it.tag == "WICKET MAIDEN" }
                assertTrue("$ctx maidens $maidenCalls vs sim $simMaidens", maidenCalls in simMaidens..simMaidens + m.innings.size)
                assertEquals("$ctx one result", 1, lines.count { it.kind == "RESULT" })
                assertEquals("$ctx intro", true, lines.first().kind == "INTRO")
                // the departing batter's line uses the batter who was actually out (also non-striker run-outs)
                for (e in m.events.filter { it.kind == "WICKET" && it.outPid != null && it.outPid != it.batter }) {
                    val w = lines.first { it.evId == e.id && it.kind == "WICKET" }
                    assertTrue("$ctx run-out names ${w.text}", w.text.contains(m.pn(e.outPid!!)))
                }
                if (style == 0 && lang != "MIX") {
                    sample.append("\n===== $fmt · $lang · ${Commentary.boothFor(lang, style).joinToString(" & ") { it.name }} · ${m.resultText}\n")
                    lines.take(70).forEach { sample.append("${it.over.padEnd(5)} [${it.kind}${if (it.tag.isNotEmpty()) "/" + it.tag else ""}] ${if (it.speaker == 1) "  >> " else ""}${it.text}${if (it.detail.isNotEmpty()) " | " + it.detail.replace("\n", " | ") else ""}\n") }
                    sample.append("...\n"); lines.takeLast(12).forEach { sample.append("${it.over.padEnd(5)} [${it.kind}${if (it.tag.isNotEmpty()) "/" + it.tag else ""}] ${it.text}\n") }
                }
                println("$ctx: ${lines.size} lines, ${lines.count { it.kind == "CALL" }} calls, ${lines.count { it.kind == "EXPERT" }} analyst, $overs over cards, tags ${lines.mapNotNull { it.tag.ifEmpty { null } }.groupingBy { it }.eachCount()}")
            }
            // analyst off -> no EXPERT lines
            assertTrue(run(m, "EN", 0, expert = false).none { it.kind == "EXPERT" })
        }
        File("../docs/commentary_sample.txt").writeText(sample.toString())
    }

    /** Fast-forward: tracker-only events must keep stats exact for the lines that are emitted later. */
    @Test fun fastForwardKeepsStatsExact() {
        val s = save(); val m = match(s, "T20", 21)
        val full = Commentary("EN", 0, Random(1)).let { c -> m.events.flatMap { c.process(it, m) } }.filter { it.kind == "OVER" }
        val c = Commentary("EN", 0, Random(1))
        val ff = m.events.flatMapIndexed { i, ev -> c.process(ev, m, emit = i >= m.events.size - 30) }.filter { it.kind == "OVER" }
        assertTrue(ff.isNotEmpty())
        for (o in ff) assertEquals(full.first { it.evId == o.evId }.detail, o.detail)
    }

    private fun ev(id: Int, over: Int, ball: Int, s: MatchSim, batter: String, bowler: String, kind: String, runs: Int = 0, how: String = "", outPid: String? = null) =
        BallEv(id, 0, over, ball, s.home.id, batter, bowler, runs, if (kind == "WICKET") 0 else runs, kind, how, 0f, 0.1f, "", false, outPid, null)

    @Test fun hatTrickAndMaiden() {
        val s = save()
        val comp = Competition(id = "H", name = "Hat-trick Cup", key = "H", format = "T20", kind = "NATION", series = true)
        val m = Career.makeSim(s, comp, Fixture("h1", "N_ENG", "N_SA", "Match", 0, 1), Random(2))
        val bats = s.clubs["N_ENG"]!!.xi; val bowler = s.clubs["N_SA"]!!.xi.last()
        for (lang in listOf("EN", "HI", "HG")) {
            val c = Commentary(lang, 1, Random(4))
            val out = ArrayList<ComLine>()
            // over 0: six dots -> maiden
            for (b in 1..6) out += c.process(ev(b - 1, 0, b, m, bats[0], bowler, "DOT"), m)
            // over 1 by the same bowler (test only): W, W, W on balls 1-3
            out += c.process(ev(6, 1, 1, m, bats[0], bowler, "WICKET", how = "b ${m.pn(bowler)}", outPid = bats[0]), m)
            out += c.process(ev(7, 1, 2, m, bats[2], bowler, "WICKET", how = "lbw b ${m.pn(bowler)}", outPid = bats[2]), m)
            out += c.process(ev(8, 1, 3, m, bats[3], bowler, "WICKET", how = "b ${m.pn(bowler)}", outPid = bats[3]), m)
            val tags = out.map { it.tag }
            assertTrue("$lang maiden", "MAIDEN" in tags)
            assertTrue("$lang hat-trick ball", "HAT-TRICK BALL" in tags)
            assertTrue("$lang hat-trick", "HAT-TRICK" in tags)
            assertTrue("$lang golden duck", out.any { it.kind == "WICKET" && it.evId == 7 && (it.text.contains("golden", true) || it.text.contains("गोल्डन")) })
            assertTrue("$lang 3-for", "3-FOR" in tags)
            val card = out.first { it.kind == "OVER" }
            assertTrue(card.detail.contains("1-1-0-0"))
        }
    }

    @Test fun shotRegions() {
        assertEquals(0, Commentary.region(5f)); assertEquals(0, Commentary.region(350f))
        assertEquals(1, Commentary.region(80f)); assertEquals(4, Commentary.region(180f))
        assertEquals(7, Commentary.region(280f)); assertEquals(8, Commentary.region(320f)); assertEquals(9, Commentary.region(40f))
    }
}
