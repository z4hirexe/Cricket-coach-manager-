package com.cricketcoach.manager.game

/**
 * Structured scorecard rebuilt from the stored scorecard lines of a played fixture (v2.6 Match Report).
 * Works for every save (the lines format has not changed since v1), so old results get the new report too.
 */
object Scorecard {
    class Bat(val name: String, val how: String, val runs: Int, val balls: Int, val fours: Int, val sixes: Int) {
        val notOut get() = how.equals("not out", true)
        val sr get() = if (balls > 0) runs * 100.0 / balls else 0.0
    }
    class Bowl(val name: String, val overs: String, val maidens: Int, val runs: Int, val wk: Int) {
        val econ: Double get() { val p = overs.split('.'); val b = (p[0].toIntOrNull() ?: 0) * 6 + (p.getOrNull(1)?.toIntOrNull() ?: 0); return if (b > 0) runs * 6.0 / b else 0.0 }
    }
    class Inn(val team: String, val label: String, val score: String, val overs: String) {
        val bat = mutableListOf<Bat>(); val bowl = mutableListOf<Bowl>(); var extras = 0
    }

    private val HEAD = Regex("""^(.+?) — Innings (\d+): (.+?) \((.+?) ov\)$""")
    private val BAT = Regex("""^ {2}(.+?) {2}(.+) {2}(\d+) \((\d+)\) 4s:(\d+) 6s:(\d+)$""")
    private val BOWL = Regex("""^ {2}(.+?) {2}(\d+(?:\.\d)?)-(\d+)-(\d+)-(\d+)$""")
    private val EXTRAS = Regex("""^ {2}Extras (\d+)$""")

    fun parse(lines: List<String>): List<Inn> {
        val out = mutableListOf<Inn>()
        for (l in lines) {
            HEAD.matchEntire(l)?.let { m -> out.add(Inn(m.groupValues[1], "Innings ${m.groupValues[2]}", m.groupValues[3], m.groupValues[4])); return@let }
                ?: run {
                    val cur = out.lastOrNull() ?: return@run
                    EXTRAS.matchEntire(l)?.let { cur.extras = it.groupValues[1].toInt(); return@run }
                    BAT.matchEntire(l)?.let { m -> cur.bat.add(Bat(m.groupValues[1], m.groupValues[2].trim(), m.groupValues[3].toInt(), m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toInt())); return@run }
                    BOWL.matchEntire(l)?.let { m -> cur.bowl.add(Bowl(m.groupValues[1], m.groupValues[2], m.groupValues[3].toInt(), m.groupValues[4].toInt(), m.groupValues[5].toInt())) }
                }
        }
        return out
    }

    /** Top performers of a match for the result card: best batters and bowlers across innings. */
    fun topBat(inns: List<Inn>, n: Int = 3) = inns.flatMap { i -> i.bat.map { i.team to it } }.sortedByDescending { it.second.runs * 1000 - it.second.balls }.take(n)
    fun topBowl(inns: List<Inn>, n: Int = 3) = inns.flatMap { i -> i.bowl.map { i.team to it } }.sortedByDescending { it.second.wk * 1000 - it.second.runs }.take(n)
}
