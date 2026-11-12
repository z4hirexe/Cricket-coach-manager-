package com.cricketcoach.manager.game

import kotlin.random.Random

/**
 * One commentary line. [speaker] indexes [Commentary.booth] (0 lead, 1 analyst, -1 = scoreboard card, not spoken).
 * [kind]: the delivery kind (DOT/RUN/FOUR/SIX/WIDE/NB/WICKET/APPEAL) for ball lines, or CALL, EXPERT, OVER, INTRO, INNINGS, RESULT.
 * [tag]: special-call badge (FIFTY, HUNDRED, MAIDEN, HAT-TRICK, 5-FOR, PARTNERSHIP...). [detail]: second line of an OVER card.
 */
data class ComLine(
    val evId: Int, val over: String, val text: String, val lang: String, val kind: String,
    val speaker: Int = 0, val tag: String = "", val detail: String = "", val seq: Int = 0, val runs: Int = 0
) {
    val key: String get() = "$evId-$seq"
    val spoken: Boolean get() = speaker >= 0
}

/** A (fictional) commentator: name, initials and colour for the feed, and TTS pitch/rate so the two voices sound different. */
data class Caster(val name: String, val role: String, val color: Long, val pitch: Float, val rate: Float, val female: Boolean)

/**
 * Commentary 2.0 — a two-voice commentary booth in English, Hindi, Hinglish or a mixed Hindi + English booth.
 *  - lead commentator calls every ball (shot direction, fielder names, dismissal type), styles: 0 Classic, 1 Excited, 2 Analytical
 *  - special calls: 50/100/150/200, team milestones, partnerships, 3-fors/5-fors, hat-trick ball + hat-trick, ducks, collapses,
 *    nervous nineties, free hits, maidens, wicket maidens, big overs, end of powerplay, death overs, last over, chase equation, winning hit
 *  - an expert analyst adds insight after wickets, milestones, maidens, dot-ball pressure and every few overs (can be switched off)
 *  - an end-of-over scoreboard card (score, run rate, batters, bowler figures)
 *  - match intro / toss, innings break with the target, result
 *  - never repeats a line back-to-back: each pool is drawn least-recently-used
 * All numbers are tracked from the ball events themselves, so the commentary is exact even when the match is fast-forwarded.
 */
class Commentary(
    private val langPref: String,
    private val style: Int,
    private val rnd: Random = Random.Default,
    private val expert: Boolean = true
) {
    /** Language of the lead voice and of the analyst. MIX = English lead + Hindi analyst. */
    val leadLang = when (langPref) { "HI" -> "HI"; "HG" -> "HG"; else -> "EN" }
    val expertLang = when (langPref) { "MIX" -> "HI"; else -> leadLang }
    val booth: List<Caster> = boothFor(langPref, style)

    /* ---------------- template picking (no repeats) ---------------- */
    private val lastUsed = HashMap<String, Int>()
    private var counter = 0
    private fun pick(list: List<String>): String {
        if (list.size == 1) return list[0]
        val ordered = list.sortedBy { lastUsed[it] ?: -1 }
        val pool = ordered.take(if (list.size >= 6) list.size / 3 else maxOf(1, list.size / 2))
        val t = pool.random(rnd)
        lastUsed[t] = ++counter
        return t
    }
    private fun pick(p: Pool, lang: String) = pick(p.of(lang))

    /* ---------------- per-innings tracker ---------------- */
    private class Bat { var runs = 0; var balls = 0; var fours = 0; var sixes = 0; var out = false }
    private class Bowl { var balls = 0; var runs = 0; var wk = 0; var maidens = 0; val seq = ArrayList<Boolean>() }
    private class InnT(val team: String) {
        var runs = 0; var wk = 0; var legal = 0
        val bat = LinkedHashMap<String, Bat>(); val bowl = LinkedHashMap<String, Bowl>()
        var partRuns = 0; var partBalls = 0; val crease = ArrayList<String>()
        var overRuns = 0; var overWk = 0; var dots = 0
        val wkBalls = ArrayList<Int>()   // legal-ball index of each wicket
        var freeHit = false
    }
    private val inns = ArrayList<InnT>()
    private var curInn = -1
    private var introDone = false
    private var resultDone = false
    private var seq = 0

    private fun t(): InnT = inns[curInn]
    private fun bat(pid: String) = t().bat.getOrPut(pid) { Bat() }
    private fun bowl(pid: String) = t().bowl.getOrPut(pid) { Bowl() }
    fun fig(pid: String): String { val b = t().bowl[pid] ?: return "0-0"; return "${b.balls / 6}${if (b.balls % 6 > 0) ".${b.balls % 6}" else ""}-${b.maidens}-${b.runs}-${b.wk}" }
    private fun overs(balls: Int) = "${balls / 6}${if (balls % 6 > 0) ".${balls % 6}" else ""}"

    /** Total of innings [i] according to the commentary tracker (for targets). */
    private fun total(i: Int) = inns.getOrNull(i)?.runs ?: 0

    /**
     * Feeds one ball event (events must be fed in order). When [emit] is false the event only updates the tracker
     * (used when a match is fast-forwarded). Returns the lines for this ball in chronological order.
     */
    fun process(ev: BallEv, sim: MatchSim, emit: Boolean = true): List<ComLine> {
        val out = ArrayList<ComLine>()
        val over = "${ev.over}.${ev.ball}"
        fun add(text: String, kind: String, speaker: Int, lang: String, tag: String = "", detail: String = "", runs: Int = 0) {
            if (emit && text.isNotBlank()) out.add(ComLine(ev.id, over, text.trim(), lang, kind, speaker, tag, detail, seq++, runs))
        }
        val L = leadLang; val X = expertLang

        // ---- match intro / innings change
        if (!introDone) {
            introDone = true
            if (ev.id == 0 || ev.inn == 0 && ev.over == 0) intro(sim, ev).forEach { (k, s) -> add(s, k, 0, L) }
        }
        if (ev.inn != curInn) {
            if (curInn >= 0) {
                val prev = t()
                add(fill(pick(ComText.INNINGS_END, L), sim, team = sim.club(prev.team).name, score = "${prev.runs}/${prev.wk} (${overs(prev.legal)} ov)"), "INNINGS", 0, L, "INNINGS BREAK")
                if (!sim.isTest && ev.inn == 1) add(fill(pick(ComText.TARGET, L), sim, opp = sim.club(ev.batTeam).name, n = prev.runs + 1), "INNINGS", 0, L)
            }
            while (inns.size <= ev.inn) inns.add(InnT(if (inns.size == ev.inn) ev.batTeam else ""))
            curInn = ev.inn
            if (ev.inn > 0) add(fill(pick(ComText.NEW_INNINGS, L), sim, team = sim.club(ev.batTeam).name), "INNINGS", 0, L)
        }
        val T = t()
        val legal = ev.kind != "WIDE" && ev.kind != "NB"
        val bowlerWk = ev.kind == "WICKET" && !ev.how.startsWith("run out")
        val wasFreeHit = T.freeHit
        val teamBefore = T.runs
        val b0 = bat(ev.batter).runs
        val partBefore = T.partRuns
        val partners = T.crease.toList()

        // ---- update tracker
        val bt = bat(ev.batter); val bw = bowl(ev.bowler)
        if (ev.batter !in T.crease) { T.crease.add(ev.batter); while (T.crease.size > 2) T.crease.removeAt(0) }
        T.runs += ev.runs; bw.runs += ev.runs; T.overRuns += ev.runs; T.partRuns += ev.runs
        if (ev.kind != "WIDE") { bt.balls++; T.partBalls++ }
        bt.runs += ev.bat
        if (ev.bat == 4 && (ev.kind == "FOUR" || ev.kind == "NB")) bt.fours++
        if (ev.bat == 6 && (ev.kind == "SIX" || ev.kind == "NB")) bt.sixes++
        if (legal) { T.legal++; bw.balls++; bw.seq.add(bowlerWk) }
        T.dots = if (legal && ev.runs == 0 && ev.kind != "WICKET") T.dots + 1 else 0
        if (ev.kind == "WICKET") {
            T.wk++; T.overWk++; T.wkBalls.add(T.legal)
            if (bowlerWk) bw.wk++
            val outPid = ev.outPid ?: ev.batter
            bat(outPid).out = true; T.crease.remove(outPid)
            T.partRuns = 0; T.partBalls = 0
        }
        T.freeHit = !sim.isTest && ev.kind == "NB" || (wasFreeHit && !legal)
        val overDone = legal && ev.ball == 6
        val overRuns = T.overRuns; val overWk = T.overWk
        if (overDone) { if (overRuns == 0) bw.maidens++; T.overRuns = 0; T.overWk = 0 }
        val inn = sim.innings.getOrNull(ev.inn)
        val maxBalls = if (sim.isTest) 0 else inn?.maxBalls ?: sim.limitedMaxBalls
        val target: Int? = if (!sim.isTest && ev.inn == 1) total(0) + 1 else inn?.target
        val lastEv = sim.finished && sim.events.lastOrNull()?.id == ev.id

        if (!emit) { if (lastEv) resultDone = true; return out }

        // ---- names / values for templates
        val batN = sim.pn(ev.batter); val bowlN = sim.pn(ev.bowler)
        val fldN = ev.fielderPid?.let { sim.pn(it) } ?: ""
        val region = region(ev.angle)
        val vals = HashMap<String, String>().apply {
            put("bat", batN); put("bowl", bowlN); put("fld", fldN.ifEmpty { when (L) { "HI" -> "फील्डर"; "HG" -> "fielder"; else -> "the fielder" } }); put("r", ev.bat.toString())
            put("team", sim.club(ev.batTeam).name); put("opp", sim.club(sim.other(ev.batTeam)).name)
        }
        fun f(s: String, extra: Map<String, String> = emptyMap()): String { var r = s; for ((k, v) in extra) r = r.replace("{$k}", v); for ((k, v) in vals) r = r.replace("{$k}", v); return r }

        // ---- main ball line (lead)
        var main = when (ev.kind) {
            "DOT" -> pick(ComText.DOT, L)
            "RUN" -> pick(when (ev.bat) { 1 -> ComText.ONE; 2 -> ComText.TWO; else -> ComText.THREE }, L).replace("{dir}", ComText.GROUND.of(L)[region])
            "FOUR" -> (if ((region == 3 || region == 4) && rnd.nextInt(10) < 7) pick(ComText.FOUR_EDGE, L) else pick(ComText.FOUR, L)).replace("{dir}", ComText.GROUND.of(L)[region])
            "SIX" -> pick(ComText.SIX, L).replace("{dir}", ComText.AIR.of(L)[region]) +
                (if (ev.dist >= 1.19f) pick(ComText.SIX_BIG, L).replace("{m}", (ev.dist * 78).toInt().toString()) else "")
            "WIDE" -> pick(ComText.WIDE, L)
            "NB" -> pick(ComText.NB, L) + (if (ev.bat > 0) when (L) { "HI" -> " साथ में बल्ले से ${ev.bat} रन।"; "HG" -> " Saath mein bat se ${ev.bat} run."; else -> " And ${ev.bat} off the bat." } else "") +
                (if (!sim.isTest) pick(ComText.FREE_HIT_NEXT, L) else "")
            "APPEAL" -> pick(ComText.APPEAL, L)
            "WICKET" -> pick(when {
                ev.how.startsWith("b ") -> ComText.BOWLED
                ev.how.startsWith("lbw") -> ComText.LBW
                ev.how.startsWith("run out") -> ComText.RUNOUT
                ev.how.startsWith("st ") -> ComText.STUMPED
                ev.how.startsWith("c & b") -> ComText.CAUGHT_BOWLER
                ev.how.startsWith("c †") -> ComText.CAUGHT_KEEPER
                ev.dist >= 0.7f -> ComText.CAUGHT_DEEP
                else -> ComText.CAUGHT
            }, L)
            else -> ev.text
        }
        main = f(main, if (ev.kind == "WICKET") mapOf("bat" to sim.pn(ev.outPid ?: ev.batter)) else emptyMap())
        if (ev.kind == "WICKET") {
            val op = ev.outPid ?: ev.batter
            val oc = bat(op)
            val dep = when { oc.runs == 0 && oc.balls == 1 && op == ev.batter -> pick(ComText.GOLDEN_DUCK, L); oc.runs == 0 -> pick(ComText.DUCK, L); else -> pick(ComText.DEPART, L) }
            main += " " + f(dep, mapOf("bat" to sim.pn(op), "runs" to oc.runs.toString(), "b" to oc.balls.toString()))
        }
        val big = ev.kind == "SIX" || ev.kind == "FOUR" || ev.kind == "WICKET"
        if (style == 1 && big && rnd.nextInt(10) < 6) main = pick(ComText.HYPE, L) + " " + main
        if (style == 2 && (ev.kind == "FOUR" || ev.kind == "SIX") && bt.balls >= 6) {
            val sr = bt.runs * 100 / bt.balls.coerceAtLeast(1)
            main += when (L) { "HI" -> " स्ट्राइक रेट $sr।"; "HG" -> " Strike rate ab $sr."; else -> " Strike rate now $sr." }
        }
        add(main, ev.kind, 0, L, runs = ev.bat)

        // ---- special calls (lead)
        var xDone = false
        fun expertLine(p: Pool, extra: Map<String, String> = emptyMap(), tag: String = "") {
            if (!expert || xDone) return
            xDone = true
            add(f(pick(p, X), extra), "EXPERT", 1, X, tag)
        }
        // batting milestones
        val a = bt.runs
        if (ev.bat > 0) {
            val ms = listOf(50, 100, 150, 200, 250, 300).lastOrNull { b0 < it && a >= it }
            if (ms != null) {
                val ex = mapOf("b" to bt.balls.toString(), "n" to ms.toString())
                when (ms) {
                    50 -> add(f(pick(ComText.FIFTY, L), ex), "CALL", 0, L, "FIFTY")
                    100 -> add(f(pick(ComText.HUNDRED, L), ex), "CALL", 0, L, "HUNDRED")
                    else -> add(f(pick(ComText.BIG_HUNDRED, L), ex), "CALL", 0, L, "$ms")
                }
                expertLine(ComText.X_MILESTONE, mapOf("fours" to bt.fours.toString(), "sixes" to bt.sixes.toString()))
            } else if (a in 90..99 && b0 < 90) add(f(pick(ComText.NINETIES, L), mapOf("runs" to a.toString())), "CALL", 0, L)
        }
        // team milestones (every 50; in Tests only every 100)
        val step = if (sim.isTest) 100 else 50
        if (T.runs / step > teamBefore / step && T.runs >= step && rnd.nextInt(3) > 0 && ev.kind != "WICKET") {
            add(f(pick(ComText.TEAM_MS, L), mapOf("n" to (T.runs / step * step).toString())), "CALL", 0, L)
        }
        // partnerships
        if (ev.kind != "WICKET") {
            val pm = listOf(50, 100, 150, 200).lastOrNull { partBefore < it && T.partRuns >= it }
            val other = T.crease.firstOrNull { it != ev.batter } ?: partners.firstOrNull { it != ev.batter }
            if (pm != null && other != null) add(f(pick(ComText.PART, L), mapOf("n" to pm.toString(), "nb" to sim.pn(other), "partb" to T.partBalls.toString())), "CALL", 0, L, "PARTNERSHIP")
        }
        // free hit outcomes
        if (wasFreeHit && legal) when {
            ev.kind == "FOUR" || ev.kind == "SIX" -> add(f(pick(ComText.FREE_HIT_PUNISH, L)), "CALL", 0, L, "FREE HIT")
            ev.runs == 0 && ev.kind != "WICKET" -> add(f(pick(ComText.FREE_HIT_WASTED, L)), "CALL", 0, L, "FREE HIT")
        }
        // wickets: hat-trick, bowler hauls, collapse, analyst
        if (ev.kind == "WICKET") {
            val last3 = bw.seq.takeLast(3)
            when {
                bowlerWk && last3.size == 3 && last3.all { it } -> { add(f(pick(ComText.HATTRICK, L)), "CALL", 0, L, "HAT-TRICK"); expertLine(ComText.X_HATTRICK, tag = "HAT-TRICK") }
                bowlerWk && last3.size >= 2 && last3.takeLast(2).all { it } -> add(f(pick(ComText.HATTRICK_ON, L)), "CALL", 0, L, "HAT-TRICK BALL")
            }
            if (bowlerWk && bw.wk == 5) add(f(pick(ComText.FIVE_FOR, L), mapOf("fig" to fig(ev.bowler))), "CALL", 0, L, "5-FOR")
            else if (bowlerWk && bw.wk == 3 && !sim.isTest) add(f(pick(ComText.THREE_FOR, L), mapOf("fig" to fig(ev.bowler))), "CALL", 0, L, "3-FOR")
            val recent = T.wkBalls.count { it > T.legal - 18 }
            if (recent >= 3 && T.wk >= 3) add(f(pick(ComText.COLLAPSE, L), mapOf("score" to "${T.runs}/${T.wk}")), "CALL", 0, L, "COLLAPSE")
            if (!lastEv && rnd.nextInt(10) < 7) {
                val op = ev.outPid ?: ev.batter; val oc = bat(op)
                val pool = when { oc.runs >= 25 -> ComText.X_WICKET_SET; partBefore >= 30 -> ComText.X_WICKET_PART; else -> ComText.X_WICKET }
                expertLine(pool, mapOf("part" to partBefore.toString(), "bat" to sim.pn(op), "runs" to oc.runs.toString()))
            }
        }
        // winning hit + result
        if (lastEv && !resultDone) {
            resultDone = true
            if (sim.winner == ev.batTeam) add(f(pick(ComText.WIN, L)), "CALL", 0, L, "WIN")
            val potm = sim.potm()?.let { sim.pn(it) }
            add(sim.resultText, "RESULT", 0, "EN", "RESULT", detail = potm?.let { "Player of the match: $it" } ?: "")
            return out
        }
        // chase equation (limited overs)
        if (target != null && maxBalls > 0) {
            val need = target - T.runs; val left = maxBalls - T.legal
            if (need > 0 && left > 0) {
                val tense = left <= 12 || (left <= 30 && need * 6.0 / left > 9)
                if (tense && (legal || ev.kind == "WICKET")) add(f(pick(ComText.CHASE_TIGHT, L), mapOf("need" to "$need", "left" to "$left")), "CALL", 0, L)
                else if (overDone && rnd.nextInt(3) == 0) add(f(pick(ComText.CHASE, L), mapOf("need" to "$need", "left" to "$left")), "CALL", 0, L)
                if ((ev.kind == "FOUR" || ev.kind == "SIX") && left > 6 && rnd.nextInt(3) == 0) expertLine(ComText.X_BOUNDARY_CHASE, mapOf("rrr" to "%.2f".format(need * 6.0 / left)))
            }
        }
        // dot-ball pressure
        if (if (sim.isTest) T.dots == 12 else T.dots == 5 || T.dots == 9) expertLine(ComText.X_DOTS, mapOf("dots" to T.dots.toString()))

        // ---- end of over: calls, analyst insight, scoreboard card
        if (overDone && !T.closedBy(sim, ev)) {
            val ov = T.legal / 6
            val score = "${T.runs}/${T.wk}"
            if (overRuns == 0) {
                if (overWk > 0) add(f(pick(ComText.WICKET_MAIDEN, L)), "CALL", 0, L, "WICKET MAIDEN") else add(f(pick(ComText.MAIDEN, L)), "CALL", 0, L, "MAIDEN")
                expertLine(ComText.X_MAIDEN)
            } else if (overRuns >= (if (sim.isTest) 14 else 18)) add(f(pick(ComText.BIG_OVER, L), mapOf("r" to "$overRuns")), "CALL", 0, L, "BIG OVER")
            if (maxBalls > 0) {
                val totalOv = maxBalls / 6
                val pp = when { totalOv >= 40 -> 10; totalOv >= 10 -> 6; else -> 0 }
                when {
                    pp > 0 && ov == pp -> add(f(pick(ComText.PP_END, L), mapOf("score" to score)), "CALL", 0, L, "POWERPLAY")
                    totalOv >= 20 && ov == totalOv - (if (totalOv >= 40) 10 else 4) -> add(f(pick(ComText.DEATH, L), mapOf("score" to score)), "CALL", 0, L, "DEATH OVERS")
                    ov == totalOv - 1 -> add(f(pick(ComText.LAST_OVER, L)), "CALL", 0, L)
                }
            }
            // analyst every few overs
            if (expert && !xDone && ov % 3 == 0) {
                val strikerPid = T.crease.lastOrNull() ?: ev.batter
                val sb = T.bat[strikerPid] ?: bt
                val rr = if (T.legal == 0) 0.0 else T.runs * 6.0 / T.legal
                if (target != null && maxBalls > 0 && target - T.runs > 0 && maxBalls - T.legal > 0) {
                    val need = target - T.runs; val left = maxBalls - T.legal
                    expertLine(ComText.X_OVER_CHASE, mapOf("need" to "$need", "left" to "$left", "rrr" to "%.2f".format(need * 6.0 / left)))
                } else {
                    // only say what is actually worth saying right now
                    val opts = ArrayList<Pool>()
                    if (bw.balls >= 12) opts.add(ComText.X_FIG)
                    if (T.partRuns >= 20 && T.partBalls >= 12) opts.add(ComText.X_PART)
                    if (maxBalls > 0 && T.legal >= 30) opts.add(ComText.X_RR)
                    if (sb.balls >= 10 && !sb.out) opts.add(ComText.X_BAT)
                    if (opts.isNotEmpty()) expertLine(opts.random(rnd), mapOf(
                        "fig" to fig(ev.bowler), "econ" to "%.2f".format(if (bw.balls == 0) 0.0 else bw.runs * 6.0 / bw.balls),
                        "part" to "${T.partRuns}", "partb" to "${T.partBalls}", "rr" to "%.2f".format(rr),
                        "proj" to (if (maxBalls > 0) (rr * maxBalls / 6).toInt() else T.runs).toString(),
                        "bat" to sim.pn(strikerPid), "runs" to "${sb.runs}", "b" to "${sb.balls}", "fours" to "${sb.fours}", "sixes" to "${sb.sixes}"))
                }
            }
            // scoreboard card
            val crr = if (T.legal == 0) 0.0 else T.runs * 6.0 / T.legal
            val head = when (L) {
                "HI" -> "ओवर $ov समाप्त · $overRuns रन${if (overWk > 0) " · $overWk विकेट" else ""}"
                "HG" -> "Over $ov khatam · $overRuns run${if (overWk > 0) " · $overWk wkt" else ""}"
                else -> "END OF OVER $ov · $overRuns RUN${if (overRuns == 1) "" else "S"}${if (overWk > 0) " · $overWk WKT" else ""}"
            }
            val bats = T.crease.mapNotNull { pid -> T.bat[pid]?.let { "${sim.pn(pid)} ${it.runs} (${it.balls})" } }.joinToString("  ·  ")
            var detail = "${sim.club(ev.batTeam).short} $score  ·  CRR ${"%.2f".format(crr)}"
            if (target != null && maxBalls > 0 && target - T.runs > 0 && maxBalls - T.legal > 0) detail += "  ·  RRR ${"%.2f".format((target - T.runs) * 6.0 / (maxBalls - T.legal))}"
            detail += "\n" + (if (bats.isNotEmpty()) "$bats  ·  " else "") + "$bowlN ${fig(ev.bowler)}"
            add(head, "OVER", -1, L, detail = detail)
        }
        return out
    }

    /** Over completed with the innings already over (all out / target reached): no scoreboard card for it. */
    private fun InnT.closedBy(sim: MatchSim, ev: BallEv) = sim.finished && sim.events.lastOrNull()?.id == ev.id

    /** Back-compat: the main line for one ball. */
    fun line(ev: BallEv, sim: MatchSim, live: Boolean = true): ComLine =
        process(ev, sim, true).firstOrNull { it.kind == ev.kind } ?: ComLine(ev.id, "${ev.over}.${ev.ball}", ev.text, leadLang, ev.kind)

    private fun intro(sim: MatchSim, ev: BallEv): List<Pair<String, String>> {
        val L = leadLang
        val res = ArrayList<Pair<String, String>>()
        res.add("INTRO" to fill(pick(ComText.INTRO, L), sim, team = sim.home.name, opp = sim.away.name))
        if (sim.tossWinner.isNotEmpty()) {
            val dec = when (L) { "HI" -> if (sim.decision == "BAT") "बल्लेबाज़ी" else "गेंदबाज़ी"; "HG" -> if (sim.decision == "BAT") "batting" else "bowling"; else -> if (sim.decision == "BAT") "bat" else "bowl" }
            res.add("INTRO" to fill(pick(ComText.TOSS, L), sim, team = sim.club(sim.tossWinner).name).replace("{dec}", dec)
                .replace("{weather}", weatherWord(sim.cfg.weather, L)).replace("{pitch}", pitchWord(sim.cfg.pitch, L)))
        }
        return res
    }

    private fun fill(s: String, sim: MatchSim, team: String = "", opp: String = "", score: String = "", n: Int = 0): String =
        s.replace("{team}", team).replace("{opp}", opp).replace("{score}", score).replace("{n}", n.toString())
            .replace("{venue}", sim.cfg.venue.ifBlank { sim.home.stadium.ifBlank { "the ground" } }).replace("{comp}", sim.cfg.compName.ifBlank { sim.cfg.format })

    companion object {
        /** Field region of a shot angle (0 = straight past the bowler, 90 = cover, 270 = leg side). See [ComText.GROUND]. */
        fun region(angle: Float): Int {
            val a = ((angle % 360f) + 360f) % 360f
            return when {
                a < 20f || a >= 340f -> 0
                a < 55f -> 9
                a < 100f -> 1
                a < 135f -> 2
                a < 165f -> 3
                a < 195f -> 4
                a < 225f -> 5
                a < 260f -> 6
                a < 300f -> 7
                else -> 8
            }
        }

        fun weatherWord(w: String, lang: String) = when (lang) {
            "HI" -> when (w) { "Sunny" -> "साफ़ आसमान"; "Overcast" -> "बादल छाए हुए"; "Humid" -> "उमस भरा"; "Cloudy" -> "हल्के बादल"; else -> w }
            "HG" -> when (w) { "Sunny" -> "saaf aasmaan"; "Overcast" -> "badal chhaye hue"; "Humid" -> "umas bhara"; "Cloudy" -> "halke badal"; else -> w }
            else -> when (w) { "Sunny" -> "Clear skies"; "Overcast" -> "Overcast skies"; "Humid" -> "It's humid"; "Cloudy" -> "A bit of cloud about"; else -> w }
        }
        fun pitchWord(p: String, lang: String) = when (lang) {
            "HI" -> when (p) { "DUSTY" -> "सूखी, स्पिन वाली"; "GREEN" -> "हरी घास वाली"; "FLAT" -> "सपाट"; else -> "संतुलित" }
            "HG" -> when (p) { "DUSTY" -> "sookhi, turning"; "GREEN" -> "hari, seaming"; "FLAT" -> "flat"; else -> "balanced" }
            else -> when (p) { "DUSTY" -> "dry, turning"; "GREEN" -> "green, seaming"; "FLAT" -> "flat batting"; else -> "good, balanced" }
        }

        private val EN = listOf(
            Caster("Mark Ellison", "Play-by-play", 0xFF38BDF8, 0.95f, 1.0f, false),
            Caster("Vikram Desai", "Play-by-play", 0xFFF59E0B, 1.0f, 1.12f, false),
            Caster("Priya Nair", "Analyst", 0xFFF472B6, 1.18f, 1.0f, true))
        private val HI = listOf(
            Caster("Rajesh Tiwari", "कमेंटेटर", 0xFF38BDF8, 0.95f, 1.0f, false),
            Caster("Arun Joshi", "कमेंटेटर", 0xFFF59E0B, 1.0f, 1.12f, false),
            Caster("Sunita Verma", "विशेषज्ञ", 0xFFF472B6, 1.18f, 1.0f, true))
        private val HG = listOf(
            Caster("Kabir Malhotra", "Play-by-play", 0xFF38BDF8, 0.95f, 1.02f, false),
            Caster("Dev Sharma", "Play-by-play", 0xFFF59E0B, 1.0f, 1.12f, false),
            Caster("Rhea Sethi", "Analyst", 0xFFF472B6, 1.18f, 1.0f, true))

        /** Lead + analyst for a language and style: Classic = measured lead + female analyst, Excited = energetic lead, Analytical = analyst leads. */
        fun boothFor(lang: String, style: Int): List<Caster> {
            fun of(set: List<Caster>, s: Int) = when (s) {
                1 -> listOf(set[1], set[0].copy(role = set[2].role))
                2 -> listOf(set[2].copy(role = set[0].role), set[1].copy(role = set[2].role))
                else -> listOf(set[0], set[2])
            }
            return when (lang) {
                "HI" -> of(HI, style)
                "HG" -> of(HG, style)
                "MIX" -> listOf(of(EN, style)[0], of(HI, style)[1])
                else -> of(EN, style)
            }
        }
    }
}
