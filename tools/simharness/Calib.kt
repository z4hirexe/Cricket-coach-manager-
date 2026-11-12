import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import java.io.File
import kotlin.random.Random

/** Score calibration: many simulated matches per format -> averages vs real-world reference ranges. */
class Agg(val name: String) {
    var n = 0; var first = 0L; var second = 0L; var wk1 = 0L; var allOut = 0; var t200 = 0; var t250 = 0; var t300 = 0; var t350 = 0; var hundreds = 0; var fifties = 0; var chaseWins = 0; var draws = 0
    var balls1 = 0L; var sixes = 0L; var fours = 0L; var extras = 0L; var maxScore = 0; var topBat = 0
    fun add(m: MatchSim) {
        n++
        val i1 = m.innings[0]; first += i1.runs; wk1 += i1.wk; balls1 += i1.balls; extras += i1.extras
        if (i1.wk >= 10) allOut++
        if (i1.runs >= 200) t200++; if (i1.runs >= 250) t250++; if (i1.runs >= 300) t300++; if (i1.runs >= 350) t350++
        maxScore = maxOf(maxScore, m.innings.maxOf { it.runs })
        if (m.innings.size > 1) second += m.innings[1].runs
        for (inn in m.innings) for (c in inn.bc) { if (c.runs >= 100) hundreds++ else if (c.runs >= 50) fifties++; sixes += c.sixes; fours += c.fours; topBat = maxOf(topBat, c.runs) }
        if (m.draw) draws++
        if (!m.isTest && m.winner != null && m.innings.size > 1 && m.winner == m.innings[1].batTeam) chaseWins++
    }
    fun report() = "%-14s n=%3d 1st=%5.1f (wk %.1f, ov %.1f, allout %2d%%) 2nd=%5.1f | 200+ %2d%% 250+ %2d%% 300+ %2d%% 350+ %2d%% | 100s/m %.2f 50s/m %.2f | 6s/inn %.1f 4s/inn %.1f | max %d top %d chase %2d%% draw %2d%%".format(
        name, n, first.toDouble() / n, wk1.toDouble() / n, balls1 / 6.0 / n, allOut * 100 / n, second.toDouble() / n, t200 * 100 / n, t250 * 100 / n, t300 * 100 / n, t350 * 100 / n,
        hundreds.toDouble() / n, fifties.toDouble() / n, sixes.toDouble() / n / 2, fours.toDouble() / n / 2, maxScore, topBat, chaseWins * 100 / n, draws * 100 / n)
}

fun main(args: Array<String>) {
    val root = args.getOrElse(0) { "../../app/src/main/assets" }
    val N = args.getOrElse(1) { "60" }.toInt()
    File("$root/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
    File("$root/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
    System.getProperty("bias")?.let { MatchSim.DIFF_BIAS = it.toDouble() }
    System.getProperty("cap")?.let { MatchSim.DIFF_CAP = it.toDouble() }
    System.getProperty("fcwk")?.let { MatchSim.FC_WK = it.toDouble() }
    System.getProperty("fcdot")?.let { MatchSim.FC_DOT = it.toDouble() }
    System.getProperty("lf")?.let { MatchSim.LEVEL_F = it.toDouble() }
    System.getProperty("sd")?.let { MatchSim.SD_SCALE = it.toDouble() }
    val rnd = Random(2026)
    // internationals between the top 8 nations
    val s = Modes.newCareer(1, "C", Modes.INTL, "N_IND", 1.0, 1, Random(1))
    val top = listOf("N_IND", "N_AUS", "N_ENG", "N_SA", "N_NZ", "N_PAK", "N_SL", "N_WI")
    for (fmt in listOf("T20", "ODI", "TEST")) {
        val ag = Agg("Intl $fmt"); val nn = if (fmt == "TEST") N / 3 else N
        val comp = Competition(id = "C$fmt", name = "Calib", key = "C$fmt", format = fmt, kind = "NATION", series = true)
        repeat(nn) { i ->
            val h = top[rnd.nextInt(top.size)]; var a = top[rnd.nextInt(top.size)]; while (a == h) a = top[rnd.nextInt(top.size)]
            val m = Career.makeSim(s, comp, Fixture("c$i", h, a, "Match", 0, 1), Random(rnd.nextLong()))
            m.autoAll = true; m.simToEnd(); ag.add(m)
        }
        println(ag.report())
    }
    // IPL
    val ip = Modes.newCareer(1, "C", Modes.IPL, "F_MI", 1.0, 1, Random(2)); ip.auction = null; Career.startSeason(ip)
    val ipl = ip.league.first()
    val ag = Agg("IPL")
    for (f in ipl.fixtures.take(N)) { val m = Career.makeSim(ip, ipl, f, Random(rnd.nextLong())); m.autoAll = true; m.simToEnd(); ag.add(m) }
    println(ag.report())
    // domestic
    val d = Modes.newCareer(1, "C", Modes.DOMESTIC, "S_BEN", 1.0, 1, Random(3)); Career.startSeason(d)
    for (key in listOf("SMAT", "VHT", "RANJI")) {
        val c = d.league.firstOrNull { it.key == key } ?: continue
        val a2 = Agg(key)
        c.fixtures.firstOrNull()?.let { f -> val mm = Career.makeSim(d, c, f, Random(1)); println("  ${f.home} v ${f.away} kinds ${mm.home.kind}/${mm.away.kind} levelAdj %.2f xi ${mm.xiOf(f.home).size}".format(mm.levelAdj)) }
        for (f in c.fixtures.filter { it.home.startsWith("S_") && it.away.startsWith("S_") }.take(if (key == "RANJI") N / 3 else N)) { val m = Career.makeSim(d, c, f, Random(rnd.nextLong())); m.autoAll = true; m.simToEnd(); a2.add(m) }
        println(a2.report())
    }
}
