package com.cricketcoach.manager.game

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class MatchCfg(
    val format: String,          // T20 / ODI / TEST
    val overs: Int,              // 20 / 50 / 0 (Test)
    val pitch: String,
    val weather: String,
    val venue: String,
    val compName: String,
    val allowImpact: Boolean = false
)

class BatCard(val pid: String) {
    var runs = 0; var balls = 0; var fours = 0; var sixes = 0
    var out = false; var how = "not out"; var batted = false
}

class BowlCard(val pid: String) {
    var balls = 0; var runs = 0; var wk = 0; var maidens = 0; var overRuns = 0
    val oversText: String get() = "${balls / 6}.${balls % 6}"
    val econ: Double get() = if (balls == 0) 0.0 else runs * 6.0 / balls
}

class Inns(val batTeam: String, val bowlTeam: String, order: List<String>, var maxBalls: Int, val maxWk: Int = 10) {
    val bc: MutableList<BatCard> = order.map { BatCard(it) }.toMutableList()
    val bw = LinkedHashMap<String, BowlCard>()
    var runs = 0; var wk = 0; var balls = 0; var extras = 0
    var striker = 0; var nonStriker = 1; var nextIdx = 2
    var bowler = ""; var lastBowler = ""; var overStarted = false
    var target: Int? = null
    var closed = false; var declared = false; var followOn = false; var freeHit = false
    var partRuns = 0; var partBalls = 0
    val fow = mutableListOf<String>(); val parts = mutableListOf<String>()
    var reviews = 2
    var fieldReviews = 2
    val recent = ArrayDeque<String>()
    val spell = HashMap<String, Int>(); val lastOverBy = HashMap<String, Int>()
    var ppOvers = 6
    val allOut: Boolean get() = wk >= maxWk || wk >= bc.size - 1
    fun oversText() = "${balls / 6}.${balls % 6}"
    fun crr(): Double = if (balls == 0) 0.0 else runs * 6.0 / balls
    fun scoreText() = if (declared) "$runs/$wk d" else if (wk >= 10) "$runs" else "$runs/$wk"
}

/** One recorded delivery, used by the 3D view, highlights, wagon wheel, Manhattan and worm charts. */
class BallEv(
    val id: Int, val inn: Int, val over: Int, val ball: Int, val batTeam: String, val batter: String, val bowler: String,
    val runs: Int,            // total runs added by this delivery (incl. extras)
    val bat: Int,             // runs off the bat
    val kind: String,         // DOT, RUN, FOUR, SIX, WIDE, NB, WICKET, APPEAL
    val how: String,          // dismissal text for WICKET
    val angle: Float,         // shot direction in degrees (0 = straight back past bowler, 90 = off side/cover, 270 = leg side)
    val dist: Float,          // 0..1.25 fraction of boundary radius
    val text: String,
    val milestone: Boolean = false,
    /** WICKET: the batter who was out (may be the non-striker on a run-out); null in old saves */
    val outPid: String? = null,
    /** WICKET: catcher / run-out fielder / stumping keeper; null if none */
    val fielderPid: String? = null
) { val highlight: Boolean get() = kind == "FOUR" || kind == "SIX" || kind == "WICKET" || milestone }

class MatchSim(
    val cfg: MatchCfg,
    val home: Club,
    val away: Club,
    val players: Map<String, GPlayer>,
    homeXI: List<String>,
    awayXI: List<String>,
    homeBowl: List<String>,
    awayBowl: List<String>,
    private val rnd: Random = Random.Default
) {
    private val xi = hashMapOf(home.id to homeXI.toMutableList(), away.id to awayXI.toMutableList())
    private val bowlOpts = hashMapOf(home.id to buildBowlOpts(homeXI, homeBowl), away.id to buildBowlOpts(awayXI, awayBowl))
    private val impactUsed = hashSetOf<String>()

    /* Match-day variance: team and individual "on the day" form, so upsets happen (more in T20). */
    private fun gauss(): Double { val u1 = rnd.nextDouble().coerceAtLeast(1e-9); val u2 = rnd.nextDouble(); return kotlin.math.sqrt(-2 * kotlin.math.ln(u1)) * kotlin.math.cos(2 * Math.PI * u2) }
    private val sdTeam = when (cfg.format) { "T20" -> 7.0; "ODI" -> 5.5; else -> 4.5 }
    private val sdPlayer = when (cfg.format) { "T20" -> 8.0; "ODI" -> 6.5; else -> 5.5 }
    private val teamDay = hashMapOf(home.id to gauss() * sdTeam, away.id to gauss() * sdTeam)
    private val playerDay = HashMap<String, Double>()
    private fun pDay(pid: String) = playerDay.getOrPut(pid) { gauss() * sdPlayer }

    var tossWinner = ""; var decision = ""
    var userTeam = ""
    val innings = mutableListOf<Inns>()
    var finished = false; var winner: String? = null; var draw = false; var noResult = false
    var resultText = ""; var superOverText = ""
    val com = ArrayList<String>()
    val catches = HashMap<String, Int>()
    val keyEvents = ArrayList<String>()
    val events = ArrayList<BallEv>()
    var lastEv: BallEv? = null
    private var lastHow = ""
    private var lastOutPid: String? = null
    private var lastFielder: String? = null
    private val vrnd = kotlin.random.Random(System.nanoTime())

    // Test match clock: 5 days x 90 overs
    var testBallsTotal = 2700; var testBallsUsed = 0
    /** When true the AI also makes the user's declaration decisions (quick-sim / "sim rest of match"). */
    var autoAll = false
    // Rain
    var rainNote = ""
    private var rainAtBall = -1; private var rainLoseBalls = 0
    val limitedMaxBalls: Int = cfg.overs * 6

    init {
        planRain()
    }

    val isTest get() = cfg.format == "TEST"
    fun club(id: String) = if (id == home.id) home else away
    fun other(id: String) = if (id == home.id) away.id else home.id
    fun name(id: String) = club(id).short
    fun pn(pid: String): String = players[pid]?.short() ?: pid
    fun xiOf(id: String): List<String> = xi[id] ?: emptyList()
    val cur: Inns? get() = innings.lastOrNull()
    val started: Boolean get() = innings.isNotEmpty()

    private fun buildBowlOpts(xiList: List<String>, plan: List<String>): MutableList<String> {
        val opts = plan.filter { it in xiList }.toMutableList()
        val extra = xiList.filter { it !in opts }.sortedByDescending { players[it]?.bowl ?: 0 }
        for (e in extra) {
            if (opts.size >= 6) break
            if ((players[e]?.bowl ?: 0) >= 45 || opts.size < 5) opts.add(e)
        }
        return opts
    }

    private fun planRain() {
        val chance = when (cfg.weather) { "Overcast" -> 0.28; "Cloudy" -> 0.10; "Humid" -> 0.08; else -> 0.02 }
        if (rnd.nextDouble() >= chance) return
        if (isTest) {
            val days = if (rnd.nextDouble() < 0.7) 1 else 2
            testBallsTotal -= days * 540
            rainNote = "Rain: ${days * 90} overs lost to weather across the match."
            keyEvents.add(rainNote)
        } else if (rnd.nextDouble() < 0.4) {
            val cut = (cfg.overs * (0.15 + rnd.nextDouble() * 0.3)).toInt().coerceAtLeast(2)
            rainLoseBalls = cut * 6
            rainNote = "Rain delayed the start: match reduced to ${cfg.overs - cut} overs per side."
            keyEvents.add(rainNote)
        } else {
            rainAtBall = (limitedMaxBalls * (0.3 + rnd.nextDouble() * 0.5)).toInt()
        }
    }

    /* ---------------- toss ---------------- */
    fun autoTossDecision(winnerId: String): String {
        val c = club(winnerId)
        if (isTest) return if (cfg.pitch == "GREEN" || cfg.weather == "Overcast") "BOWL" else "BAT"
        if (cfg.pitch == "GREEN" || cfg.weather == "Humid") return "BOWL"
        if (cfg.pitch == "DUSTY") return "BAT"
        return if (c.aggression >= 2 || rnd.nextDouble() < 0.55) "BOWL" else "BAT"
    }

    fun doToss(winnerId: String, dec: String) {
        tossWinner = winnerId; decision = dec
        val first = if (dec == "BAT") winnerId else other(winnerId)
        com.add(0, "${club(winnerId).name} won the toss and chose to ${if (dec == "BAT") "bat" else "bowl"} first.")
        com.add(0, "Pitch: ${pitchText()} • Weather: ${cfg.weather} • ${cfg.venue}")
        if (rainNote.isNotEmpty()) com.add(0, rainNote)
        startInnings(first, other(first), null)
    }

    fun autoToss() {
        val w = if (rnd.nextBoolean()) home.id else away.id
        doToss(w, autoTossDecision(w))
    }

    fun pitchText() = when (cfg.pitch) {
        "GREEN" -> "Green seamer (helps pace)"; "DUSTY" -> "Dry & dusty (helps spin)"
        "FLAT" -> "Flat batting deck"; else -> "Balanced surface"
    }

    private fun startInnings(bat: String, bowl: String, target: Int?) {
        var order = xiOf(bat).toMutableList()
        if (cfg.allowImpact && cfg.format == "T20") applyImpact(bat, bowl, order)
        order = xiOf(bat).toMutableList()
        val maxB = if (isTest) Int.MAX_VALUE else limitedMaxBalls - rainLoseBalls
        val inn = Inns(bat, bowl, order, maxB)
        inn.reviews = if (isTest) 3 else 2
        inn.fieldReviews = inn.reviews
        inn.ppOvers = if (cfg.format == "ODI") max(1, maxB / 6 / 5) else max(1, ceil(maxB / 6 * 0.3).toInt())
        inn.target = target
        inn.bc.getOrNull(0)?.batted = true; inn.bc.getOrNull(1)?.batted = true
        innings.add(inn)
        val label = when (innings.size) { 1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; else -> "4th" }
        com.add(0, "— ${club(bat).name} begin the $label innings" + (target?.let { " needing $it to win" } ?: "") + " —")
    }

    private fun applyImpact(bat: String, bowl: String, @Suppress("UNUSED_PARAMETER") order: MutableList<String>) {
        for (team in listOf(bat, bowl)) {
            if (team in impactUsed) continue
            val c = club(team)
            val imp = c.impact
            val list = xi[team] ?: continue
            val ip = players[imp] ?: continue
            if (imp in list || ip.injured) continue
            val bowledAlready = innings.any { it.bowlTeam == team }
            val battedAlready = innings.any { it.batTeam == team }
            var outIdx = -1
            if (team == bat && bowledAlready) {
                // bring in an impact batter for a lower-order player who has not batted
                outIdx = (6 until list.size).minByOrNull { players[list[it]]?.batSkill() ?: 0 } ?: -1
                if (outIdx >= 0 && ip.batSkill() < (players[list[outIdx]]?.batSkill() ?: 0) + 5) outIdx = -1
            } else if (team == bowl && battedAlready && ip.bowl >= 55) {
                // bring in an impact bowler for a batter who has finished batting
                outIdx = list.indices.filter { (players[list[it]]?.bowl ?: 0) < 45 }.minByOrNull { players[list[it]]?.bowl ?: 0 } ?: -1
            }
            if (outIdx < 0) continue
            val outPid = list[outIdx]
            list[outIdx] = imp
            bowlOpts[team]?.let { ops -> ops.remove(outPid); if (ip.bowl >= 50 && imp !in ops) ops.add(imp) }
            impactUsed.add(team)
            val msg = "IMPACT PLAYER: ${ip.name} replaces ${pn(outPid)} for ${c.short}."
            com.add(0, msg); keyEvents.add(msg)
        }
    }

    /* ---------------- bowling selection ---------------- */
    private fun oversBy(inn: Inns, pid: String) = (inn.bw[pid]?.balls ?: 0) / 6

    private fun effBowl(pid: String, inn: Inns): Double {
        val p = players[pid] ?: return 30.0
        var w = p.bowl + (p.form - 60) / 6.0
        when (cfg.pitch) {
            "GREEN" -> w += if (p.bowlType == "PACE") 6 else -4
            "DUSTY" -> w += if (p.bowlType == "SPIN") 7 else -3
            "FLAT" -> w -= 5
        }
        if ((cfg.weather == "Cloudy" || cfg.weather == "Overcast") && p.bowlType == "PACE") w += 3
        if (cfg.weather == "Humid" && innings.size >= 2 && p.bowlType == "SPIN") w -= 4
        if (isTest && testBallsUsed > 1620 && p.bowlType == "SPIN") w += 4
        val sp = inn.spell[pid] ?: 0
        if (isTest && sp > 5) w -= (sp - 5) * 1.5
        if (p.fitness < 50) w -= (50 - p.fitness) / 6.0
        return w
    }

    private fun chooseBowler(inn: Inns) {
        val overIdx = inn.balls / 6
        val opts = bowlOpts[inn.bowlTeam] ?: mutableListOf()
        val quota = if (isTest) 999 else max(1, ceil(inn.maxBalls / 6 / 5.0).toInt())
        var cands = opts.filter { it != inn.lastBowler && oversBy(inn, it) < quota }
        if (cands.isEmpty()) cands = xiOf(inn.bowlTeam).filter { it != inn.lastBowler && oversBy(inn, it) < quota }
        if (cands.isEmpty()) cands = xiOf(inn.bowlTeam).filter { it != inn.lastBowler }
        val totalOvers = inn.maxBalls / 6
        val death = !isTest && overIdx >= totalOvers - (if (cfg.format == "ODI") 10 else 4)
        val pp = !isTest && overIdx < inn.ppOvers
        val best = cands.maxByOrNull { c ->
            val p = players[c]
            var s = effBowl(c, inn)
            if (!isTest) {
                val rem = quota - oversBy(inn, c)
                val oversLeft = totalOvers - overIdx
                s += rem * 2.5
                if (death) s += (p?.bowl ?: 0) * 0.25 - (if (p?.bowlType == "SPIN") 6 else 0)
                if (pp && p?.bowlType == "SPIN") s -= 4
                if (!death && rem >= oversLeft / 2) s += 6
            } else {
                val sp = inn.spell[c] ?: 0
                s -= sp * 4.5
                val ballAge = overIdx % 80
                if (ballAge < 12 && p?.bowlType == "PACE") s += 6
                if (ballAge > 35 && p?.bowlType == "SPIN") s += 4
            }
            s + rnd.nextDouble() * 4
        } ?: xiOf(inn.bowlTeam).first()
        // spell tracking
        inn.spell[best] = if (inn.lastOverBy[best] == overIdx - 2) (inn.spell[best] ?: 0) + 1 else 1
        inn.lastOverBy[best] = overIdx
        inn.bowler = best
        inn.overStarted = true
        inn.bw.getOrPut(best) { BowlCard(best) }.overRuns = 0
    }

    /* ---------------- ball model ---------------- */
    private fun aggression(inn: Inns): Double {
        val c = club(inn.batTeam)
        var a = (c.aggression - 1) * 0.5
        if (!isTest) {
            val over = inn.balls / 6
            val totalOvers = inn.maxBalls / 6
            val death = over >= totalOvers - (if (cfg.format == "ODI") 10 else 4)
            val ballsLeft = inn.maxBalls - inn.balls
            if (inn.target == null) {
                if (death) a += 0.4
                if (inn.wk >= 7 && !death) a -= 0.3
            } else {
                val need = inn.target!! - inn.runs
                val rrr = if (ballsLeft > 0) need * 6.0 / ballsLeft else 36.0
                val par = if (cfg.format == "ODI") 5.6 else 8.3
                a += ((rrr - par) / 4).coerceIn(-0.4, 1.0)
                if (inn.wk >= 8 && ballsLeft > 12) a -= 0.3
            }
        } else {
            a -= 0.35
            val idx = innings.size - 1
            if (idx == 3 && inn.target != null) {
                val left = testBallsTotal - testBallsUsed
                val need = inn.target!! - inn.runs
                val rrr = if (left > 0) need * 6.0 / left else 99.0
                if (rrr > 4.5) a -= 0.5 else if (rrr < 3.2) a += 0.1
            }
            if (idx == 2 && !inn.followOn && lead(inn) > 200) a += 0.45
        }
        return a.coerceIn(-1.0, 1.2)
    }

    private fun lead(inn: Inns): Int {
        val mine = innings.filter { it.batTeam == inn.batTeam }.sumOf { it.runs }
        val theirs = innings.filter { it.batTeam != inn.batTeam }.sumOf { it.runs }
        return mine - theirs
    }

    private fun effBat(pid: String, card: BatCard): Double {
        val p = players[pid] ?: return 40.0
        val wTech = when (cfg.format) { "T20" -> 0.4; "ODI" -> 0.6; else -> 0.85 }
        var b = p.tech * wTech + p.power * (1 - wTech)
        b += (p.form - 60) / 6.0 + (p.morale - 60) / 12.0
        if (p.fitness < 50) b -= (50 - p.fitness) / 5.0
        if (card.balls < 6) b -= 5.0 else if (card.balls > 25) b += 4.0
        return b
    }

    /** Returns probabilities: 0 dot,1,2,3,4,6(index 5),wicket(index 6). */
    private fun probs(inn: Inns, batPid: String, card: BatCard, bowlPid: String): DoubleArray {
        val p = when (cfg.format) {
            "T20" -> doubleArrayOf(0.33, 0.35, 0.08, 0.006, 0.12, 0.06, 0.054)
            "ODI" -> doubleArrayOf(0.47, 0.33, 0.07, 0.006, 0.075, 0.018, 0.027)
            else -> doubleArrayOf(0.72, 0.16, 0.04, 0.008, 0.05, 0.004, 0.0165)
        }
        val diff = (effBat(batPid, card) + pDay(batPid) + (teamDay[inn.batTeam] ?: 0.0) -
            effBowl(bowlPid, inn) - pDay(bowlPid) - (teamDay[inn.bowlTeam] ?: 0.0) + 12) / 100.0
        p[6] *= exp(-diff * 2.4)
        p[4] *= exp(diff * 2.0); p[5] *= exp(diff * 2.2)
        p[0] *= exp(-diff * 1.0)
        val a = aggression(inn)
        p[4] *= (1 + 0.35 * a); p[5] *= (1 + 0.6 * a); p[6] *= (1 + 0.5 * a); p[0] *= (1 - 0.25 * a); p[1] *= (1 - 0.1 * a)
        if (!isTest) {
            val over = inn.balls / 6
            val total = inn.maxBalls / 6
            val deathOvers = if (cfg.format == "ODI") 10 else 4
            when {
                over < inn.ppOvers -> { p[4] *= 1.25; p[5] *= 0.9; p[6] *= 0.95 }
                over >= total - deathOvers -> { p[4] *= 1.2; p[5] *= 1.6; p[6] *= 1.35; p[0] *= 0.85 }
                else -> { p[1] *= 1.1; p[4] *= 0.85; p[5] *= 0.9 }
            }
        }
        if (inn.freeHit) p[6] = 0.0
        val sum = p.sum()
        for (i in p.indices) p[i] /= sum
        return p
    }

    private fun pick(p: DoubleArray): Int {
        var r = rnd.nextDouble(); for (i in p.indices) { r -= p[i]; if (r <= 0) return i }; return 0
    }

    /* ---------------- public stepping API ---------------- */
    fun nextBall() {
        if (finished) return
        val inn = cur ?: return
        if (inn.closed) return
        val comBefore = com.size; val runsBefore = inn.runs; val wkBefore = inn.wk; val ballsBefore = inn.balls
        val batter = inn.bc.getOrNull(inn.striker)?.pid ?: ""
        val innIdx = innings.size - 1; val keyBefore = keyEvents.size
        val recBefore = inn.recent.size; val lastRecentBefore = inn.recent.lastOrNull()
        lastHow = ""; lastOutPid = null; lastFielder = null
        nextBallInner()
        val added = com.size - comBefore
        if (added <= 0) return
        val changed = inn.runs != runsBefore || inn.wk != wkBefore || inn.balls != ballsBefore || inn.recent.size != recBefore || inn.recent.lastOrNull() != lastRecentBefore
        if (!changed) return
        val text = com.getOrNull(added - 1) ?: ""
        val runs = inn.runs - runsBefore
        val rec = inn.recent.lastOrNull() ?: ""
        val kind = when {
            inn.wk > wkBefore -> "WICKET"
            rec == "wd" -> "WIDE"
            rec == "nb" -> "NB"
            runs >= 6 && rec == "6" -> "SIX"
            rec == "4" -> "FOUR"
            runs > 0 -> "RUN"
            text.contains("LBW", true) || text.contains("appeal", true) -> "APPEAL"
            else -> "DOT"
        }
        val bat = when (kind) { "WIDE" -> 0; "NB" -> runs - 1; "WICKET" -> 0; else -> runs }
        // shot direction: right-handers favour off side slightly; sixes straighter/leg side
        val angle = when (kind) {
            "SIX" -> listOf(0f, 20f, 340f, 300f, 60f, 270f, 320f).random(vrnd) + vrnd.nextFloat() * 30f - 15f
            "FOUR" -> vrnd.nextFloat() * 360f
            "DOT", "APPEAL", "WIDE" -> 160f + vrnd.nextFloat() * 40f
            else -> vrnd.nextFloat() * 360f
        }.let { (it + 360f) % 360f }
        val dist = when (kind) {
            "SIX" -> 1.12f + vrnd.nextFloat() * 0.12f; "FOUR" -> 1.0f
            "RUN", "NB" -> when (bat) { 1 -> 0.3f + vrnd.nextFloat() * 0.3f; 2 -> 0.6f + vrnd.nextFloat() * 0.2f; 3 -> 0.82f + vrnd.nextFloat() * 0.1f; 4 -> 1.0f; 6 -> 1.15f; else -> 0.2f }
            "WICKET" -> if (lastHow.startsWith("c ")) 0.35f + vrnd.nextFloat() * 0.5f else 0.05f
            else -> 0.12f + vrnd.nextFloat() * 0.1f
        }
        val ms = keyEvents.drop(keyBefore).any { it.startsWith("MILESTONE") }
        val ev = BallEv(events.size, innIdx, ballsBefore / 6, ballsBefore % 6 + 1, inn.batTeam, batter, inn.bowler, runs, bat, kind, lastHow, angle, dist, text, ms, lastOutPid, lastFielder)
        events.add(ev); lastEv = ev
    }

    private fun nextBallInner() {
        if (finished) return
        val inn = cur ?: return
        if (inn.closed) return
        if (!inn.overStarted) chooseBowler(inn)
        // rain interruption in 2nd innings of limited-overs match
        if (!isTest && innings.size == 2 && rainAtBall >= 0 && inn.balls >= rainAtBall) { applyRainInterruption(inn); if (finished || inn.closed) return }
        val bowler = inn.bowler
        val bwc = inn.bw.getOrPut(bowler) { BowlCard(bowler) }
        val sCard = inn.bc[inn.striker]
        val batter = sCard.pid
        val overNo = inn.balls / 6; val ballNo = inn.balls % 6 + 1
        val tag = "$overNo.$ballNo ${pn(bowler)} to ${pn(batter)}"
        // extras
        val wideP = when (cfg.format) { "T20" -> 0.035; "ODI" -> 0.028; else -> 0.006 }
        val nbP = when (cfg.format) { "TEST" -> 0.01; else -> 0.006 }
        val x = rnd.nextDouble()
        if (x < wideP) {
            inn.runs += 1; inn.extras += 1; bwc.runs += 1; bwc.overRuns += 1; inn.partRuns += 1
            pushRecent(inn, "wd"); com.add(0, "$tag, wide ball.")
            afterBall(inn); return
        }
        if (x < wideP + nbP) {
            inn.runs += 1; inn.extras += 1; bwc.runs += 1; bwc.overRuns += 1; inn.partRuns += 1
            val r = listOf(0, 0, 1, 1, 2, 4, 6).random(rnd)
            sCard.runs += r; sCard.balls += 1; inn.runs += r; bwc.runs += r; bwc.overRuns += r; inn.partRuns += r
            if (r == 4) sCard.fours++; if (r == 6) sCard.sixes++
            if (!isTest) inn.freeHit = true
            pushRecent(inn, "nb"); com.add(0, "$tag, NO BALL${if (r > 0) " and $r off the bat" else ""}!${if (!isTest) " Free hit next." else ""}")
            if (r % 2 == 1) swap(inn)
            afterBall(inn); return
        }
        val wasFreeHit = inn.freeHit
        inn.freeHit = false
        val o = pick(probs(inn, batter, sCard, bowler))
        inn.balls++; bwc.balls++; sCard.balls++; inn.partBalls++
        if (isTest) testBallsUsed++
        if (o == 6) {
            handleWicket(inn, tag, bwc, sCard)
        } else {
            val r = when (o) { 4 -> 4; 5 -> 6; else -> o }
            // DRS: occasional LBW shouts on dot balls
            if (r == 0 && !wasFreeHit && rnd.nextDouble() < (if (isTest) 0.05 else 0.03)) {
                if (falseAppeal(inn, tag, bwc, sCard)) { afterBall(inn); return }
            } else {
                com.add(0, "$tag, ${runText(r)}")
            }
            sCard.runs += r; inn.runs += r; bwc.runs += r; bwc.overRuns += r; inn.partRuns += r
            if (r == 4) sCard.fours++; if (r == 6) sCard.sixes++
            pushRecent(inn, if (r == 0) "•" else "$r")
            if (r % 2 == 1) swap(inn)
            milestoneCheck(inn, sCard, r)
        }
        afterBall(inn)
    }

    private fun milestoneCheck(inn: Inns, c: BatCard, r: Int) {
        if (r <= 0) return
        val before = c.runs - r
        for (m in listOf(50, 100, 150, 200, 250, 300)) if (before < m && c.runs >= m) {
            val msg = "MILESTONE: ${players[c.pid]?.name} reaches $m off ${c.balls} balls!"
            com.add(0, msg); keyEvents.add(msg)
        }
    }

    private fun runText(r: Int): String = when (r) {
        0 -> listOf("no run, defended solidly.", "beaten outside off!", "dot ball, good length.", "straight to the fielder, no run.", "left alone outside off.", "played and missed!").random(rnd)
        1 -> listOf("pushed to mid-on for a single.", "worked off the pads for one.", "quick single taken.", "dabbed to third man, one run.", "nudged into the leg side for one.").random(rnd)
        2 -> listOf("driven into the gap, they come back for two.", "clipped through midwicket, two runs.", "good running, two taken.").random(rnd)
        3 -> listOf("into the deep, they run three!", "superb running, three runs.").random(rnd)
        4 -> listOf("FOUR! Cracking cover drive!", "FOUR! Pulled powerfully to the fence.", "FOUR! Cut away square.", "FOUR! Edged past slip, runs away.", "FOUR! Glorious straight drive.").random(rnd)
        else -> listOf("SIX! Launched over long-on!", "SIX! Massive hit into the stands!", "SIX! Swept over deep square leg!", "SIX! Picked the slower ball and smashed it!").random(rnd)
    }

    private fun falseAppeal(inn: Inns, tag: String, bwc: BowlCard, sCard: BatCard): Boolean {
        if (rnd.nextDouble() < 0.12) {
            // umpire wrongly gives it out
            if (inn.reviews > 0) {
                com.add(0, "$tag, big LBW shout — given OUT! ${pn(sCard.pid)} reviews... DRS: pitched outside leg. NOT OUT, decision overturned!")
                pushRecent(inn, "•")
                return true
            }
            com.add(0, "$tag, LBW given OUT! No reviews left — replays suggest it was missing, but he has to go.")
            dismiss(inn, bwc, sCard, "lbw b ${pn(bwc.pid)}", bowlerCredit = true, fielder = null)
            return true
        }
        if (inn.fieldReviews > 0 && rnd.nextDouble() < 0.2) {
            inn.fieldReviews--
            com.add(0, "$tag, LBW appeal turned down. ${club(inn.bowlTeam).short} review! DRS: missing leg stump — NOT OUT. Review lost (${inn.fieldReviews} left).")
        } else {
            com.add(0, "$tag, loud appeal for LBW... not out.")
        }
        pushRecent(inn, "•")
        return true
    }

    private fun handleWicket(inn: Inns, tag: String, bwc: BowlCard, sCard: BatCard) {
        val bp = players[bwc.pid]
        val r = rnd.nextDouble()
        val keeperId = club(inn.bowlTeam).keeper.takeIf { it in xiOf(inn.bowlTeam) } ?: xiOf(inn.bowlTeam).firstOrNull { players[it]?.role == "WK" }
        val kind = when {
            r < 0.17 -> "bowled"
            r < 0.31 -> "lbw"
            r < 0.35 && bp?.bowlType == "SPIN" && keeperId != null -> "stumped"
            r < 0.40 -> "runout"
            else -> "caught"
        }
        when (kind) {
            "bowled" -> { com.add(0, "$tag, OUT! BOWLED HIM! Stumps rattled."); dismiss(inn, bwc, sCard, "b ${pn(bwc.pid)}", true, null) }
            "lbw" -> {
                if (rnd.nextDouble() < 0.18 && inn.fieldReviews == 0) {
                    com.add(0, "$tag, huge LBW shout, NOT OUT says the umpire! No reviews left for ${club(inn.bowlTeam).short} — replays show it was hitting. Lucky escape!")
                    pushRecent(inn, "•")
                } else if (rnd.nextDouble() < 0.18) {
                    // umpire says not out
                    com.add(0, "$tag, huge LBW shout, NOT OUT says the umpire!")
                    com.add(0, "DRS: ${club(inn.bowlTeam).short} review... three reds — OUT! Decision overturned.")
                    dismiss(inn, bwc, sCard, "lbw b ${pn(bwc.pid)}", true, null)
                } else if (inn.reviews > 0 && rnd.nextDouble() < 0.3) {
                    val umpiresCall = rnd.nextDouble() < 0.4
                    if (!umpiresCall) inn.reviews--
                    com.add(0, "$tag, LBW given OUT. ${pn(sCard.pid)} reviews... ${if (umpiresCall) "umpire's call on wickets — OUT, review retained." else "hitting the stumps — OUT. Review lost."}")
                    dismiss(inn, bwc, sCard, "lbw b ${pn(bwc.pid)}", true, null)
                } else {
                    com.add(0, "$tag, OUT! Trapped LBW, plumb in front.")
                    dismiss(inn, bwc, sCard, "lbw b ${pn(bwc.pid)}", true, null)
                }
            }
            "stumped" -> { com.add(0, "$tag, OUT! Stumped! Dragged out of his crease."); dismiss(inn, bwc, sCard, "st †${pn(keeperId!!)} b ${pn(bwc.pid)}", true, keeperId) }
            "runout" -> {
                val fielder = pickFielder(inn, bwc.pid)
                val nonStrikerOut = rnd.nextDouble() < 0.4
                val outCard = if (nonStrikerOut) inn.bc[inn.nonStriker] else sCard
                com.add(0, "$tag, RUN OUT! Direct hit from ${pn(fielder)}, ${pn(outCard.pid)} is short of his ground.")
                dismiss(inn, bwc, outCard, "run out (${pn(fielder)})", false, fielder)
            }
            else -> {
                val z = rnd.nextDouble()
                if (z < 0.06) {
                    com.add(0, "$tag, OUT! Caught and bowled, sharp reflexes!")
                    catches[bwc.pid] = (catches[bwc.pid] ?: 0) + 1
                    dismiss(inn, bwc, sCard, "c & b ${pn(bwc.pid)}", true, bwc.pid)
                } else if (z < 0.32 && bp?.bowlType == "PACE" && keeperId != null) {
                    com.add(0, "$tag, OUT! Edged and taken behind by ${pn(keeperId)}.")
                    catches[keeperId] = (catches[keeperId] ?: 0) + 1
                    dismiss(inn, bwc, sCard, "c †${pn(keeperId)} b ${pn(bwc.pid)}", true, keeperId)
                } else {
                    val f = pickFielder(inn, bwc.pid)
                    com.add(0, "$tag, OUT! ${listOf("Skied and taken by", "Holed out to", "Brilliant catch by", "Straight down the throat of").random(rnd)} ${pn(f)}.")
                    catches[f] = (catches[f] ?: 0) + 1
                    dismiss(inn, bwc, sCard, if (f == keeperId) "c †${pn(f)} b ${pn(bwc.pid)}" else "c ${pn(f)} b ${pn(bwc.pid)}", true, f)
                }
            }
        }
    }

    private fun pickFielder(inn: Inns, bowler: String): String {
        val pool = xiOf(inn.bowlTeam).filter { it != bowler }
        if (pool.isEmpty()) return bowler
        val weights = pool.map { (players[it]?.field ?: 60).toDouble().pow(1.5) }
        var r = rnd.nextDouble() * weights.sum()
        for (i in pool.indices) { r -= weights[i]; if (r <= 0) return pool[i] }
        return pool.last()
    }

    private fun dismiss(inn: Inns, bwc: BowlCard, card: BatCard, how: String, bowlerCredit: Boolean, fielder: String?) {
        card.out = true; card.how = how; lastHow = how; lastOutPid = card.pid; lastFielder = fielder
        if (bowlerCredit) bwc.wk++
        fielder?.let { /* stumping credited to keeper */ }
        inn.wk++
        pushRecent(inn, "W")
        inn.fow.add("${inn.runs}-${inn.wk} (${pn(card.pid)}, ${inn.oversText()})")
        val a = inn.bc[inn.striker].pid; val b = inn.bc[inn.nonStriker].pid
        inn.parts.add("${ordinal(inn.wk)} wkt: ${inn.partRuns} (${inn.partBalls}) ${pn(a)} & ${pn(b)}")
        inn.partRuns = 0; inn.partBalls = 0
        keyEvents.add("W: ${players[card.pid]?.name} ${card.runs}(${card.balls}) $how")
        if (inn.allOut || inn.nextIdx >= inn.bc.size) return
        val idx = inn.bc.indexOf(card)
        val nb = inn.nextIdx++
        inn.bc[nb].batted = true
        if (idx == inn.striker) inn.striker = nb else inn.nonStriker = nb
    }

    private fun ordinal(n: Int) = n.toString() + when { n in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }

    private fun swap(inn: Inns) { val t = inn.striker; inn.striker = inn.nonStriker; inn.nonStriker = t }

    private fun pushRecent(inn: Inns, s: String) { inn.recent.addLast(s); while (inn.recent.size > 12) inn.recent.removeFirst() }

    private fun applyRainInterruption(inn: Inns) {
        rainAtBall = -1
        val oldMax = inn.maxBalls
        val remaining = oldMax - inn.balls
        val lose = ((remaining * (0.25 + rnd.nextDouble() * 0.35)) / 6).toInt() * 6
        val minBalls = if (cfg.format == "ODI") 120 else 30
        val newMax = oldMax - lose
        if (newMax < minBalls && inn.balls < minBalls) {
            noResult = true
            rainNote = "Rain: persistent rain, match abandoned. NO RESULT."
            keyEvents.add(rainNote); com.add(0, rainNote)
            inn.closed = true; finish(null, "No result (rain)")
            return
        }
        // Simplified DLS-style resource method
        fun res(ballsLeft: Int, wk: Int): Double = (ballsLeft.toDouble() / oldMax).pow(0.8) * (1 - wk / 12.0)
        val r2 = 1 - res(remaining, inn.wk) + res(newMax - inn.balls, inn.wk)
        val first = innings[0].runs
        val newTarget = ceil(first * r2).toInt() + 1
        inn.maxBalls = newMax
        inn.target = newTarget
        rainNote = "RAIN STOPS PLAY at ${inn.oversText()} overs. ${lose / 6} overs lost. Revised target: $newTarget from ${newMax / 6} overs (simplified DLS-style method)."
        keyEvents.add(rainNote); com.add(0, rainNote)
    }

    private fun afterBall(inn: Inns) {
        // over end
        if (inn.balls > 0 && inn.balls % 6 == 0 && inn.overStarted && lastBallLegal(inn)) {
            endOver(inn)
        }
        checkInningsEnd(inn)
    }

    private fun lastBallLegal(inn: Inns): Boolean { val l = inn.recent.lastOrNull(); return l != "wd" && l != "nb" }

    private fun endOver(inn: Inns) {
        val bwc = inn.bw[inn.bowler]
        if (bwc != null && bwc.overRuns == 0) bwc.maidens++
        inn.lastBowler = inn.bowler
        inn.overStarted = false
        swap(inn)
        val ov = inn.balls / 6
        val extra = inn.target?.let { t ->
            val need = t - inn.runs
            if (!isTest) " • Need $need from ${inn.maxBalls - inn.balls} balls (RRR ${"%.2f".format(need * 6.0 / max(1, inn.maxBalls - inn.balls))})" else " • Need $need"
        } ?: ""
        com.add(0, "End of over $ov: ${name(inn.batTeam)} ${inn.runs}/${inn.wk} (CRR ${"%.2f".format(inn.crr())})$extra")
        if (isTest) testDeclarationCheck(inn)
    }

    private fun testDeclarationCheck(inn: Inns) {
        val idx = innings.size - 1
        val left = testBallsTotal - testBallsUsed
        val userBatting = club(inn.batTeam).isUser
        if (userBatting && !autoAll) return
        if (idx == 0 && (inn.runs >= 600 || (inn.runs >= 500 && rnd.nextDouble() < 0.25) || (inn.runs >= 420 && left < testBallsTotal * 0.55))) declare()
        else if (idx == 1 && lead(inn) >= 250 && left < testBallsTotal * 0.45) declare()
        else if (idx == 2 && !inn.followOn) {
            val ld = lead(inn)
            if (ld >= 450 || (ld >= 280 && left < 700) || (ld >= 200 && left < 400)) declare()
        }
    }

    fun canDeclare(): Boolean {
        val inn = cur ?: return false
        return isTest && !finished && !inn.closed && inn.target == null && (inn.batTeam == userTeam || club(inn.batTeam).isUser)
    }

    fun declare() {
        val inn = cur ?: return
        if (inn.closed) return
        inn.declared = true
        com.add(0, "DECLARATION: ${club(inn.batTeam).name} declare at ${inn.runs}/${inn.wk}.")
        keyEvents.add("${club(inn.batTeam).short} declared at ${inn.runs}/${inn.wk}")
        inn.closed = true
        nextPhase()
    }

    private fun checkInningsEnd(inn: Inns) {
        if (inn.closed || finished) return
        val t = inn.target
        when {
            t != null && inn.runs >= t -> inn.closed = true
            inn.allOut -> inn.closed = true
            !isTest && inn.balls >= inn.maxBalls -> inn.closed = true
            isTest && testBallsUsed >= testBallsTotal -> { inn.closed = true; com.add(0, "STUMPS on the final day — time has run out."); finish(null, "Match drawn"); return }
        }
        if (inn.closed) nextPhase()
    }

    private fun nextPhase() {
        val inn = cur ?: return
        // record unbroken partnership
        if (!inn.allOut && inn.partBalls > 0) {
            inn.parts.add("Unbroken: ${inn.partRuns} (${inn.partBalls}) ${pn(inn.bc[inn.striker].pid)} & ${pn(inn.bc[inn.nonStriker].pid)}")
        }
        com.add(0, "Innings closed: ${club(inn.batTeam).name} ${inn.scoreText()} (${inn.oversText()} ov)")
        if (!isTest) {
            if (innings.size == 1) {
                startInnings(inn.bowlTeam, inn.batTeam, inn.runs + 1)
            } else {
                val first = innings[0]; val second = inn
                val t = second.target ?: (first.runs + 1)
                when {
                    second.runs >= t -> {
                        val w = second.maxWk - second.wk
                        finish(second.batTeam, "${club(second.batTeam).name} won by $w wicket${if (w == 1) "" else "s"} (${second.maxBalls - second.balls} balls left)")
                    }
                    second.runs == t - 1 -> superOver()
                    else -> {
                        val r = t - 1 - second.runs
                        finish(first.batTeam, "${club(first.batTeam).name} won by $r run${if (r == 1) "" else "s"}${if (second.target != first.runs + 1) " (DLS-style)" else ""}")
                    }
                }
            }
            return
        }
        // Test match transitions
        val a = innings[0].batTeam; val b = innings[0].bowlTeam
        val runsOf = { team: String -> innings.filter { it.batTeam == team }.sumOf { it.runs } }
        when (innings.size) {
            1 -> startInnings(b, a, null)
            2 -> {
                val ld = innings[0].runs - innings[1].runs
                if (ld >= 200 && testBallsTotal - testBallsUsed > 600) {
                    com.add(0, "${club(a).name} enforce the FOLLOW-ON (lead $ld).")
                    keyEvents.add("${club(a).short} enforced the follow-on")
                    startInnings(b, a, null); cur!!.followOn = true
                } else startInnings(a, b, null)
            }
            3 -> {
                val third = innings[2]
                if (third.followOn) {
                    val bTotal = runsOf(b); val aTotal = runsOf(a)
                    if (bTotal < aTotal) finish(a, "${club(a).name} won by an innings and ${aTotal - bTotal} runs")
                    else startInnings(a, b, bTotal - aTotal + 1)
                } else {
                    val aTotal = runsOf(a); val bTotal = runsOf(b)
                    if (aTotal < bTotal) finish(b, "${club(b).name} won by an innings and ${bTotal - aTotal} runs")
                    else startInnings(b, a, aTotal - bTotal + 1)
                }
            }
            else -> {
                val fourth = innings[3]
                val t = fourth.target ?: 0
                when {
                    fourth.runs >= t -> finish(fourth.batTeam, "${club(fourth.batTeam).name} won by ${10 - fourth.wk} wickets")
                    fourth.allOut -> finish(fourth.bowlTeam, "${club(fourth.bowlTeam).name} won by ${t - 1 - fourth.runs} runs")
                    else -> finish(null, "Match drawn")
                }
            }
        }
    }

    private fun superOver() {
        com.add(0, "SCORES LEVEL! It's a SUPER OVER!")
        val t1 = innings[1].batTeam; val t2 = innings[0].batTeam
        val r1 = soInnings(t1, t2, null)
        val r2 = soInnings(t2, t1, r1 + 1)
        val w = when {
            r2 > r1 -> t2; r1 > r2 -> t1
            else -> if (rnd.nextBoolean()) t1 else t2
        }
        superOverText = "Super Over: ${name(t1)} $r1, ${name(t2)} $r2"
        keyEvents.add(superOverText)
        finish(w, "Match tied — ${club(w).name} won the Super Over ($superOverText)")
    }

    private fun soInnings(bat: String, bowl: String, target: Int?): Int {
        val batters = xiOf(bat).sortedByDescending { players[it]?.power ?: 0 }.take(3)
        val bowler = (bowlOpts[bowl] ?: xiOf(bowl)).maxByOrNull { players[it]?.bowl ?: 0 } ?: xiOf(bowl).first()
        val inn = Inns(bat, bowl, batters, 6, 2)
        var runs = 0; var wk = 0; var s = 0
        for (i in 0 until 6) {
            val card = BatCard(batters[min(s, batters.size - 1)]).apply { balls = 10 }
            val p = probs(inn, card.pid, card, bowler)
            p[4] *= 1.3; p[5] *= 1.8; p[6] *= 1.4
            val sum = p.sum(); for (k in p.indices) p[k] /= sum
            when (val o = pick(p)) {
                6 -> { wk++; s++; if (wk >= 2) break }
                4 -> runs += 4
                5 -> runs += 6
                else -> runs += o
            }
            if (target != null && runs >= target) break
        }
        com.add(0, "Super Over: ${club(bat).short} score $runs/$wk")
        return runs
    }

    private fun finish(w: String?, text: String) {
        if (finished) return
        finished = true; winner = w
        if (w == null && !noResult) draw = true
        resultText = text
        cur?.closed = true
        com.add(0, "RESULT: $text")
    }

    fun nextOver() {
        val inn = cur ?: return
        val n = innings.size
        val targetBalls = (inn.balls / 6 + 1) * 6
        var guard = 0
        while (!finished && innings.size == n && inn.balls < targetBalls && guard < 80) { nextBall(); guard++ }
    }

    fun endInnings() {
        val n = innings.size
        var guard = 0
        while (!finished && innings.size == n && guard < 5000) { nextBall(); guard++ }
    }

    fun simToEnd() {
        autoAll = true
        if (!started) autoToss()
        var guard = 0
        while (!finished && guard < 20000) { nextBall(); guard++ }
        if (!finished) finish(null, "Match drawn")
    }

    /* ---------------- presentation helpers ---------------- */
    fun winProbability(): Int? {
        val inn = cur ?: return null
        val t = inn.target ?: return null
        if (finished) return null
        val need = t - inn.runs
        if (isTest) return null
        val left = inn.maxBalls - inn.balls
        if (left <= 0) return 0
        val rrr = need * 6.0 / left
        val par = if (cfg.format == "ODI") 5.8 else 8.5
        val wkLeft = 10 - inn.wk
        val score = 50 - (rrr - par) * 9 + (wkLeft - 5) * 6
        return score.toInt().coerceIn(1, 99)
    }

    fun phaseLabel(): String {
        val inn = cur ?: return ""
        if (isTest) {
            val day = testBallsUsed / 540 + 1
            return "Day ${min(day, 5)} • Session ${(testBallsUsed % 540) / 180 + 1}"
        }
        val over = inn.balls / 6
        val total = inn.maxBalls / 6
        return when {
            over < inn.ppOvers -> "Powerplay"
            over >= total - (if (cfg.format == "ODI") 10 else 4) -> "Death overs"
            else -> "Middle overs"
        }
    }

    fun potm(): String? {
        val pts = HashMap<String, Double>()
        for (inn in innings) {
            for (c in inn.bc) if (c.batted) pts[c.pid] = (pts[c.pid] ?: 0.0) + c.runs + c.fours + c.sixes * 2 + (if (c.runs >= 50) 10 else 0)
            for (b in inn.bw.values) pts[b.pid] = (pts[b.pid] ?: 0.0) + b.wk * 25 + (if (!isTest) max(0.0, (8.0 - b.econ) * b.balls / 12) else 0.0)
        }
        for ((k, v) in catches) pts[k] = (pts[k] ?: 0.0) + v * 8
        val w = winner
        if (w != null) for (k in pts.keys.toList()) if (k in xiOf(w)) pts[k] = pts[k]!! * 1.15
        return pts.maxByOrNull { it.value }?.key
    }

    fun scorecardLines(): MutableList<String> {
        val out = mutableListOf<String>()
        for ((i, inn) in innings.withIndex()) {
            out.add("${club(inn.batTeam).name} — Innings ${i + 1}: ${inn.scoreText()} (${inn.oversText()} ov)")
            for (c in inn.bc.filter { it.batted }) out.add("  ${players[c.pid]?.name}  ${c.how}  ${c.runs} (${c.balls}) 4s:${c.fours} 6s:${c.sixes}")
            out.add("  Extras ${inn.extras}")
            for (b in inn.bw.values) out.add("  ${players[b.pid]?.name}  ${b.oversText}-${b.maidens}-${b.runs}-${b.wk}")
        }
        return out
    }

    fun summary(): MatchSummary {
        val s = MatchSummary(winner = winner, text = resultText, draw = draw, noResult = noResult)
        val hInns = innings.filter { it.batTeam == home.id }
        val aInns = innings.filter { it.batTeam == away.id }
        s.hR = hInns.sumOf { it.runs }; s.hW = hInns.sumOf { it.wk }
        s.aR = aInns.sumOf { it.runs }; s.aW = aInns.sumOf { it.wk }
        // NRR rule: all-out counts as full allocation of overs
        s.hB = hInns.sumOf { if (it.allOut && !isTest) it.maxBalls else it.balls }
        s.aB = aInns.sumOf { if (it.allOut && !isTest) it.maxBalls else it.balls }
        s.hMaxB = hInns.firstOrNull()?.maxBalls ?: limitedMaxBalls
        s.aMaxB = aInns.firstOrNull()?.maxBalls ?: limitedMaxBalls
        s.hScore = hInns.joinToString(" & ") { it.scoreText() + if (!isTest) " (${it.oversText()})" else "" }
        s.aScore = aInns.joinToString(" & ") { it.scoreText() + if (!isTest) " (${it.oversText()})" else "" }
        s.potm = potm() ?: ""
        s.card = scorecardLines()
        return s
    }
}
