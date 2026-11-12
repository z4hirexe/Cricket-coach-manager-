package com.cricketcoach.manager.game

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object TeamSelector {
    fun isOverseas(p: GPlayer, c: Club) = c.kind != "NATION" && p.country != c.country

    fun available(c: Club, players: Map<String, GPlayer>): List<GPlayer> =
        c.squad.mapNotNull { players[it] }.filter { !it.injured && !it.retired }

    /** Pick a balanced XI, batting order, bowling plan, captain, keeper and impact player. */
    fun autoPick(c: Club, players: Map<String, GPlayer>, xiOverseasLimit: Int = 4) {
        val avail = available(c, players).sortedByDescending { it.overall() }
        if (avail.isEmpty()) return
        val limit = if (c.kind == "NATION") 11 else xiOverseasLimit
        val picks = mutableListOf<GPlayer>()
        fun overseasCount() = picks.count { isOverseas(it, c) }
        fun canAdd(p: GPlayer) = p !in picks && (!isOverseas(p, c) || overseasCount() < limit)
        // keeper
        (avail.filter { it.role == "WK" }.maxByOrNull { it.batSkill() + it.keep / 4 } ?: avail.maxByOrNull { it.keep })?.let { if (canAdd(it)) picks.add(it) }
        // 4 frontline bowlers
        for (p in avail.filter { it.role == "BOWL" }.sortedByDescending { it.bowl }) { if (picks.count { it.role == "BOWL" } >= 4) break; if (canAdd(p)) picks.add(p) }
        // 2 all-rounders
        for (p in avail.filter { it.role == "AR" }.sortedByDescending { it.overall() }) { if (picks.count { it.role == "AR" } >= 2) break; if (canAdd(p)) picks.add(p) }
        // batters to fill
        for (p in avail.filter { it.role == "BAT" || it.role == "WK" }.sortedByDescending { it.batSkill() }) { if (picks.size >= 11) break; if (canAdd(p)) picks.add(p) }
        for (p in avail) { if (picks.size >= 11) break; if (canAdd(p)) picks.add(p) }
        for (p in avail) { if (picks.size >= 11) break; if (p !in picks) picks.add(p) }
        // ensure 5 bowling options
        var guard = 0
        while (picks.count { it.bowl >= 55 } < 5 && guard++ < 5) {
            val add = avail.filter { it !in picks && it.bowl >= 55 && (!isOverseas(it, c) || overseasCount() < limit) }.maxByOrNull { it.bowl } ?: break
            val drop = picks.filter { it.bowl < 55 && it.role != "WK" }.minByOrNull { it.batSkill() } ?: break
            picks.remove(drop); picks.add(add)
        }
        c.xi = orderBatting(picks).map { it.id }.toMutableList()
        c.bowlers = picks.filter { it.bowl >= 40 }.sortedByDescending { it.bowl }.take(6).map { it.id }.toMutableList()
        c.keeper = picks.filter { it.role == "WK" }.maxByOrNull { it.keep }?.id ?: picks.maxByOrNull { it.keep }!!.id
        val leaders = picks.sortedByDescending { it.overall() + it.morale / 10 }
        if (c.captain !in c.xi) c.captain = leaders.first().id
        if (c.vice !in c.xi || c.vice == c.captain) c.vice = leaders.firstOrNull { it.id != c.captain }?.id ?: ""
        c.impact = avail.filter { it !in picks }.maxByOrNull { it.overall() }?.id ?: ""
    }

    fun orderBatting(xi: List<GPlayer>): List<GPlayer> {
        val grp = { p: GPlayer -> when (p.role) { "BAT" -> 0; "WK" -> 0; "AR" -> 1; else -> 2 } }
        val sorted = xi.sortedWith(compareBy<GPlayer> { grp(it) }.thenByDescending { it.batSkill() })
        return sorted
    }

    /** Validate the user's XI; fix injured/missing players. Returns a list of notes. */
    fun validate(c: Club, players: Map<String, GPlayer>): List<String> {
        val notes = mutableListOf<String>()
        val bad = c.xi.filter { players[it] == null || players[it]!!.injured || it !in c.squad }
        if (bad.isNotEmpty()) {
            notes.add("Replaced unavailable: " + bad.joinToString { players[it]?.name ?: it })
            c.xi.removeAll(bad)
        }
        if (c.xi.size < 11) {
            val pool = available(c, players).filter { it.id !in c.xi }.sortedByDescending { it.overall() }
            for (p in pool) { if (c.xi.size >= 11) break; c.xi.add(p.id) }
        }
        if (c.xi.size > 11) c.xi = c.xi.take(11).toMutableList()
        c.bowlers.removeAll { it !in c.xi }
        if (c.bowlers.size < 5) {
            for (p in c.xi.mapNotNull { players[it] }.sortedByDescending { it.bowl }) { if (c.bowlers.size >= 5) break; if (p.id !in c.bowlers) c.bowlers.add(p.id) }
        }
        if (c.keeper !in c.xi) c.keeper = c.xi.mapNotNull { players[it] }.maxByOrNull { it.keep }?.id ?: ""
        if (c.captain !in c.xi) c.captain = c.xi.mapNotNull { players[it] }.maxByOrNull { it.overall() }?.id ?: ""
        if (c.impact in c.xi || players[c.impact]?.injured == true) c.impact = ""
        return notes
    }
}

class TableRow(val team: String) {
    var p = 0; var w = 0; var l = 0; var d = 0; var nr = 0; var pts = 0
    var rf = 0; var bf = 0; var ra = 0; var bb = 0
    val nrr: Double get() = (if (bf > 0) rf * 6.0 / bf else 0.0) - (if (bb > 0) ra * 6.0 / bb else 0.0)
}

object CompEngine {
    fun roundRobin(teams: List<String>, double: Boolean): List<List<Pair<String, String>>> {
        val t = teams.toMutableList()
        if (t.size % 2 == 1) t.add("BYE")
        val n = t.size
        val rounds = mutableListOf<List<Pair<String, String>>>()
        val arr = t.toMutableList()
        for (r in 0 until n - 1) {
            val pairs = mutableListOf<Pair<String, String>>()
            for (i in 0 until n / 2) {
                val a = arr[i]; val b = arr[n - 1 - i]
                if (a != "BYE" && b != "BYE") pairs.add(if (r % 2 == 0) a to b else b to a)
            }
            rounds.add(pairs)
            val last = arr.removeAt(n - 1); arr.add(1, last)
        }
        if (double) rounds.addAll(rounds.map { rd -> rd.map { it.second to it.first } })
        return rounds
    }

    fun addLeagueFixtures(comp: Competition, double: Boolean, startDay: Int = 1) {
        val groups = if (comp.groups.isEmpty()) listOf(comp.teams) else comp.groups
        for ((gi, g) in groups.withIndex()) {
            for ((ri, rd) in roundRobin(g, double).withIndex()) for ((h, a) in rd) {
                comp.fixtures.add(Fixture("${comp.id}-${comp.nextFixtureId++}", h, a,
                    if (groups.size > 1) "Group ${'A' + gi}" else if (comp.series) "Match" else "League", gi, startDay + ri))
            }
        }
        if (comp.series) comp.fixtures.forEachIndexed { i, f -> f.stage = "Match ${i + 1}" }
    }

    fun table(comp: Competition, group: Int? = null): List<TableRow> {
        val teams = if (group != null && comp.groups.size > group) comp.groups[group] else comp.teams
        val rows = teams.associateWith { TableRow(it) }
        for (f in comp.fixtures) {
            if (!f.played || f.res == null) continue
            if (!(f.stage.startsWith("League") || f.stage.startsWith("Group") || f.stage.startsWith("Match"))) continue
            val r = f.res!!
            val h = rows[f.home] ?: continue; val a = rows[f.away] ?: continue
            h.p++; a.p++
            when {
                r.noResult -> { h.nr++; a.nr++; h.pts += comp.pointsNR; a.pts += comp.pointsNR }
                r.draw -> { h.d++; a.d++; h.pts += comp.pointsDraw; a.pts += comp.pointsDraw }
                r.winner == f.home -> { h.w++; a.l++; h.pts += comp.pointsWin }
                else -> { a.w++; h.l++; a.pts += comp.pointsWin }
            }
            if (!r.noResult && comp.format != "TEST") {
                h.rf += r.hR; h.bf += r.hB; h.ra += r.aR; h.bb += r.aB
                a.rf += r.aR; a.bf += r.aB; a.ra += r.hR; a.bb += r.hB
            }
        }
        return rows.values.sortedWith(compareByDescending<TableRow> { it.pts }.thenByDescending { it.nrr }.thenByDescending { it.w })
    }

    private fun leagueDone(comp: Competition) = comp.fixtures.filter { !isKO(it) }.all { it.played }
    fun isKO(f: Fixture) = !(f.stage.startsWith("League") || f.stage.startsWith("Group") || f.stage.startsWith("Match"))
    private fun lastDay(comp: Competition) = comp.fixtures.maxOfOrNull { it.day } ?: 0
    private fun koWinner(comp: Competition, f: Fixture, seedOrder: List<String>): String {
        val r = f.res!!
        return r.winner ?: (if (seedOrder.indexOf(f.home).let { if (it < 0) 99 else it } <= seedOrder.indexOf(f.away).let { if (it < 0) 99 else it }) f.home else f.away)
    }
    private fun koLoser(f: Fixture, w: String) = if (f.home == w) f.away else f.home

    /** Advance the competition stage after results. Returns true if the competition just finished. */
    fun progress(comp: Competition): Boolean {
        if (comp.stage == "DONE") return false
        if (!leagueDone(comp)) return false
        val seeds = if (comp.groups.size > 1) comp.groups.indices.flatMap { g -> table(comp, g).map { it.team } } else table(comp).map { it.team }
        val ko = comp.fixtures.filter { isKO(it) }
        val fx = { stage: String -> comp.fixtures.firstOrNull { it.stage == stage } }
        fun add(stage: String, h: String, a: String) {
            comp.fixtures.add(Fixture("${comp.id}-${comp.nextFixtureId++}", h, a, stage, 0, lastDay(comp) + 1))
        }
        if (comp.series || comp.knockout == "NONE") {
            val t = table(comp)
            val top = t.first()
            comp.champion = if (comp.series && t.size > 1 && t[0].w == t[1].w) null else top.team
            comp.runnerUp = t.getOrNull(1)?.team
            comp.stage = "DONE"; return true
        }
        comp.stage = "KNOCKOUT"
        when (comp.knockout) {
            "FINAL" -> {
                val t = table(comp)
                if (ko.isEmpty()) { add("Final", t[0].team, t[1].team); return false }
                val f = fx("Final")!!
                if (f.played) { val w = koWinner(comp, f, seeds); comp.champion = w; comp.runnerUp = koLoser(f, w); comp.stage = "DONE"; return true }
            }
            "SEMIS" -> {
                if (ko.isEmpty()) {
                    if (comp.groups.size >= 2) {
                        val a = table(comp, 0); val b = table(comp, 1)
                        add("Semi-final 1", a[0].team, b[1].team); add("Semi-final 2", b[0].team, a[1].team)
                    } else {
                        val t = table(comp)
                        add("Semi-final 1", t[0].team, t[3].team); add("Semi-final 2", t[1].team, t[2].team)
                    }
                    return false
                }
                val s1 = fx("Semi-final 1")!!; val s2 = fx("Semi-final 2")!!
                val fin = fx("Final")
                if (fin == null && s1.played && s2.played) { add("Final", koWinner(comp, s1, seeds), koWinner(comp, s2, seeds)); return false }
                if (fin != null && fin.played) { val w = koWinner(comp, fin, seeds); comp.champion = w; comp.runnerUp = koLoser(fin, w); comp.stage = "DONE"; return true }
            }
            "QF" -> {
                // 4 groups: top two of each group -> quarter-finals (A1-B2, B1-A2, C1-D2, D1-C2), semis, final
                if (ko.isEmpty()) {
                    val g = comp.groups.indices.map { table(comp, it) }
                    add("Quarter-final 1", g[0][0].team, g[1][1].team); add("Quarter-final 2", g[1][0].team, g[0][1].team)
                    add("Quarter-final 3", g[2][0].team, g[3][1].team); add("Quarter-final 4", g[3][0].team, g[2][1].team)
                    return false
                }
                val q = (1..4).map { fx("Quarter-final $it")!! }
                val s1 = fx("Semi-final 1"); val fin = fx("Final")
                if (s1 == null && q.all { it.played }) {
                    val w = q.map { koWinner(comp, it, seeds) }
                    add("Semi-final 1", w[0], w[2]); add("Semi-final 2", w[1], w[3]); return false
                }
                val s2 = fx("Semi-final 2")
                if (s1 != null && s2 != null && fin == null && s1.played && s2.played) { add("Final", koWinner(comp, s1, seeds), koWinner(comp, s2, seeds)); return false }
                if (fin != null && fin.played) { val w = koWinner(comp, fin, seeds); comp.champion = w; comp.runnerUp = koLoser(fin, w); comp.stage = "DONE"; return true }
            }
            "DULEEP" -> {
                // Duleep Trophy 2025 format: QF North v East, Central v North East; SF South v QF1 winner, West v QF2 winner; Final
                val zSeeds = listOf("Z_S", "Z_W", "Z_C", "Z_N", "Z_E", "Z_NE")
                val q1 = fx("Quarter-final 1") ?: return false; val q2 = fx("Quarter-final 2") ?: return false
                val s1 = fx("Semi-final 1"); val fin = fx("Final")
                if (s1 == null && q1.played && q2.played) { add("Semi-final 1", "Z_S", koWinner(comp, q1, zSeeds)); add("Semi-final 2", "Z_W", koWinner(comp, q2, zSeeds)); return false }
                val s2 = fx("Semi-final 2")
                if (s1 != null && s2 != null && fin == null && s1.played && s2.played) { add("Final", koWinner(comp, s1, zSeeds), koWinner(comp, s2, zSeeds)); return false }
                if (fin != null && fin.played) { val w = koWinner(comp, fin, zSeeds); comp.champion = w; comp.runnerUp = koLoser(fin, w); comp.stage = "DONE"; return true }
            }
            "IPL" -> {
                val t = table(comp)
                if (ko.isEmpty()) { add("Qualifier 1", t[0].team, t[1].team); add("Eliminator", t[2].team, t[3].team); return false }
                val q1 = fx("Qualifier 1")!!; val el = fx("Eliminator")!!
                val q2 = fx("Qualifier 2"); val fin = fx("Final")
                if (q2 == null && q1.played && el.played) {
                    val q1w = koWinner(comp, q1, seeds)
                    add("Qualifier 2", koLoser(q1, q1w), koWinner(comp, el, seeds)); return false
                }
                if (q2 != null && q2.played && fin == null) { add("Final", koWinner(comp, q1, seeds), koWinner(comp, q2, seeds)); return false }
                if (fin != null && fin.played) { val w = koWinner(comp, fin, seeds); comp.champion = w; comp.runnerUp = koLoser(fin, w); comp.stage = "DONE"; return true }
            }
        }
        return false
    }

    fun nextUnplayed(comp: Competition): Fixture? = comp.fixtures.filter { !it.played }.minByOrNull { it.day }
}

object Stats {
    private fun book(save: CareerSave, key: String, label: String, format: String): RecordBook =
        save.records.getOrPut("$key|$format") { RecordBook(label, format) }

    fun compLabel(comp: Competition) = when {
        comp.key.startsWith("LEAGUE") -> "Club League Division ${comp.division}"
        else -> comp.name.replace(Regex(" \\(Season \\d+\\)"), "")
    }

    /** Apply a finished match to competition stats, records, player form/fitness/morale and injuries. */
    fun apply(save: CareerSave, comp: Competition, fx: Fixture, sim: MatchSim, rnd: Random = Random.Default) {
        fx.played = true
        fx.res = sim.summary()
        val books = listOf(book(save, comp.key, compLabel(comp), comp.format), book(save, "ALL", "All competitions", comp.format))
        val label = compLabel(comp)
        val appeared = HashSet<String>()
        for (inn in sim.innings) {
            val oppTeam = sim.club(inn.bowlTeam).short
            for (c in inn.bc) {
                if (!c.batted) continue
                appeared.add(c.pid)
                for (bk in listOf(comp.bat) + books.map { it.bat }) {
                    val a = bk.getOrPut(c.pid) { BatAgg(team = sim.club(inn.batTeam).short) }
                    a.inn++; a.runs += c.runs; a.balls += c.balls; a.fours += c.fours; a.sixes += c.sixes
                    if (!c.out) a.no++
                    if (c.runs > a.hs || (c.runs == a.hs && !c.out)) { a.hs = c.runs; a.hsNo = !c.out }
                    if (c.runs >= 100) a.f100++ else if (c.runs >= 50) a.f50++
                }
                for (bk in books) addTop(bk.highScores, RecEntry(c.pid, sim.club(inn.batTeam).short, oppTeam, c.runs, c.balls, !c.out, save.season, label)) { it.a * 1000 - it.b }
                save.players[c.pid]?.let { it.careerRuns += c.runs }
            }
            for (b in inn.bw.values) {
                appeared.add(b.pid)
                for (bk in listOf(comp.bowlS) + books.map { it.bowl }) {
                    val a = bk.getOrPut(b.pid) { BowlAgg(team = sim.club(inn.bowlTeam).short) }
                    a.balls += b.balls; a.runs += b.runs; a.wk += b.wk
                    if (b.wk > a.bbW || (b.wk == a.bbW && b.runs < a.bbR)) { a.bbW = b.wk; a.bbR = b.runs }
                    if (b.wk >= 5) a.w5++
                }
                if (b.wk > 0) for (bk in books) addTop(bk.bestBowl, RecEntry(b.pid, sim.club(inn.bowlTeam).short, sim.club(inn.batTeam).short, b.wk, b.runs, false, save.season, label)) { it.a * 1000 - it.b }
                save.players[b.pid]?.let { it.careerWk += b.wk }
            }
            val te = RecEntry(sim.club(inn.batTeam).name, sim.club(inn.batTeam).short, oppTeam, inn.runs, inn.wk, inn.allOut, save.season, label)
            for (bk in books) {
                addTop(bk.teamHigh, te) { it.a }
                if (inn.allOut) addTop(bk.teamLow, te) { -it.a }
            }
        }
        for ((pid, n) in sim.catches) {
            comp.bowlS.getOrPut(pid) { BowlAgg() }.ct += n
            for (bk in books) bk.bowl.getOrPut(pid) { BowlAgg() }.ct += n
        }
        val xiAll = sim.xiOf(sim.home.id) + sim.xiOf(sim.away.id)
        for (pid in xiAll) {
            comp.bat.getOrPut(pid) { BatAgg() }.m++
            comp.bowlS.getOrPut(pid) { BowlAgg() }.m++
            for (bk in books) { bk.bat.getOrPut(pid) { BatAgg() }.m++; bk.bowl.getOrPut(pid) { BowlAgg() }.m++ }
        }
        updatePlayers(save, comp, sim, xiAll, rnd)
    }

    private fun addTop(list: MutableList<RecEntry>, e: RecEntry, key: (RecEntry) -> Int) {
        list.add(e); list.sortByDescending(key); while (list.size > 15) list.removeAt(list.size - 1)
    }

    private fun updatePlayers(save: CareerSave, comp: Competition, sim: MatchSim, xiAll: List<String>, rnd: Random) {
        val expRuns = when (comp.format) { "T20" -> 24.0; "ODI" -> 32.0; else -> 38.0 }
        val parEcon = when (comp.format) { "T20" -> 8.3; "ODI" -> 5.6; else -> 3.2 }
        val potm = sim.potm()
        for (pid in xiAll) {
            val p = save.players[pid] ?: continue
            p.careerM++
            var delta = 0.0
            for (inn in sim.innings) {
                inn.bc.firstOrNull { it.pid == pid && it.batted }?.let { delta += ((it.runs - expRuns) / 4).coerceIn(-6.0, 10.0) }
                inn.bw[pid]?.let { if (it.balls >= 6) delta += it.wk * 4 - (it.econ - parEcon) * 1.5 }
            }
            p.form = (p.form * 0.85 + 60 * 0.15 + delta).toInt().coerceIn(20, 99)
            val bowled = sim.innings.sumOf { it.bw[pid]?.balls ?: 0 }
            p.fitness = (p.fitness - 4 - bowled / (if (comp.format == "TEST") 60 else 12)).coerceIn(10, 100)
            val won = sim.winner != null && pid in sim.xiOf(sim.winner!!)
            p.morale = (p.morale + (if (won) 4 else if (sim.winner == null) 0 else -3) + (if (pid == potm) 6 else 0)).coerceIn(10, 100)
            // Injuries (career club matches only)
            val club = save.clubs[p.clubId]
            if (comp.kind == "CLUB" && club != null) {
                val mult = listOf(0.7, 1.0, 1.5)[club.trainIntensity.coerceIn(0, 2)] * (if (p.fitness < 60) 2.5 else 1.0)
                if (rnd.nextDouble() < 0.012 * mult) {
                    val inj = listOf("Hamstring strain" to 14, "Side strain" to 21, "Ankle sprain" to 10, "Back spasm" to 7, "Finger fracture" to 28, "Shoulder injury" to 35).random(rnd)
                    p.injuryDays = (inj.second * (1 - (club.medicalLvl - 1) * 0.1)).toInt().coerceAtLeast(5)
                    p.injury = inj.first
                    if (club.isUser) save.addNews("INJURY: ${p.name} — ${inj.first}, out ~${p.injuryDays} days")
                }
            }
        }
    }

    /** Season awards for a finished competition. */
    fun award(save: CareerSave, comp: Competition): Award {
        val orange = comp.bat.maxByOrNull { it.value.runs }
        val purple = comp.bowlS.maxByOrNull { it.value.wk }
        val mvp = (comp.bat.keys + comp.bowlS.keys).maxByOrNull { k ->
            (comp.bat[k]?.runs ?: 0) + (comp.bat[k]?.sixes ?: 0) * 2 + (comp.bowlS[k]?.wk ?: 0) * 25 + (comp.bowlS[k]?.ct ?: 0) * 8
        }
        val mvpPts = mvp?.let { k -> (comp.bat[k]?.runs ?: 0) + (comp.bat[k]?.sixes ?: 0) * 2 + (comp.bowlS[k]?.wk ?: 0) * 25 + (comp.bowlS[k]?.ct ?: 0) * 8 } ?: 0
        val a = Award(save.season, comp.name, comp.champion?.let { save.clubs[it]?.name ?: it } ?: "Shared / drawn",
            comp.runnerUp?.let { save.clubs[it]?.name ?: it } ?: "-",
            orange?.key ?: "", orange?.value?.runs ?: 0, purple?.key ?: "", purple?.value?.wk ?: 0, mvp ?: "", mvpPts)
        save.awards.add(0, a)
        comp.champion?.let { save.clubs[it]?.trophies?.add("${comp.name}") }
        return a
    }
}

object Money {
    fun value(overall: Int, age: Int, economy: Double): Double {
        var v = 0.2 + max(0, overall - 62).let { it * it } * 0.02
        if (age <= 23) v *= 1.15 else if (age >= 34) v *= 0.75
        return (v * economy * 100).toInt() / 100.0
    }

    fun basePrice(overall: Int, economy: Double): Double {
        val b = when {
            overall >= 85 -> 2.0; overall >= 80 -> 1.5; overall >= 75 -> 1.0; overall >= 70 -> 0.75
            overall >= 65 -> 0.5; overall >= 60 -> 0.3; else -> 0.2
        }
        return (b * economy * 100).toInt() / 100.0
    }

    fun increment(cur: Double): Double = when { cur < 1 -> 0.05; cur < 2 -> 0.1; cur < 5 -> 0.2; cur < 10 -> 0.25; else -> 0.5 }

    fun capFor(division: Int, economy: Double) = listOf(150.0, 90.0, 55.0, 35.0)[(division - 1).coerceIn(0, 3)] * economy
    fun tvFor(division: Int, economy: Double) = listOf(120.0, 60.0, 30.0, 15.0)[(division - 1).coerceIn(0, 3)] * economy

    fun fmt(cr: Double): String = if (kotlin.math.abs(cr) < 1) "₹${"%.0f".format(cr * 100)} L" else "₹${"%.2f".format(cr)} Cr"
    fun min2(a: Double, b: Double) = min(a, b)
}
