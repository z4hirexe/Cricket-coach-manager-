package com.cricketcoach.manager.data

import android.content.Context
import com.google.gson.Gson
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/* ---------------- Verified dataset model (assets/cricket_data.json) ---------------- */

class Coverage(val matches: Int = 0, val first: String = "", val last: String = "")

class DsMeta(
    val source: String = "", val built: String = "", val note: String = "",
    val coverage: Map<String, Coverage> = emptyMap(),
    val activeSince: String = "", val iplSince: String = "",
    val photoSource: String = ""
)

/** Detailed stats computed from Cricsheet ball-by-ball scorecards. */
class FmtStats(
    val m: Int = 0, val inn: Int = 0, val no: Int = 0, val runs: Int = 0, val bf: Int = 0,
    val hs: Int = 0, val hsNo: Boolean = false, val f50: Int = 0, val f100: Int = 0, val f200: Int = 0,
    val fours: Int = 0, val sixes: Int = 0, val bInn: Int = 0, val balls: Int = 0, val rc: Int = 0,
    val wk: Int = 0, val bbW: Int = 0, val bbR: Int = 0, val w5: Int = 0, val w10: Int = 0,
    val ct: Int = 0, val st: Int = 0, val first: String? = null, val last: String? = null, val team: String? = null
) {
    val avg: Double? get() = if (inn - no > 0) runs.toDouble() / (inn - no) else null
    val sr: Double? get() = if (bf > 0) runs * 100.0 / bf else null
    val bowlAvg: Double? get() = if (wk > 0) rc.toDouble() / wk else null
    val econ: Double? get() = if (balls > 0) rc * 6.0 / balls else null
    val bowlSr: Double? get() = if (wk > 0) balls.toDouble() / wk else null
    val hsText: String get() = if (inn == 0) "-" else "$hs${if (hsNo) "*" else ""}"
    val bbText: String get() = if (bInn == 0) "-" else "$bbW/$bbR"
}

/** Official career summary from the player's Wikipedia infobox (ESPNcricinfo-sourced). */
class OffStats(
    val m: Double? = null, val runs: Double? = null, val avg: Double? = null, val h100: Double? = null,
    val h50: Double? = null, val hs: String? = null, val balls: Double? = null, val wk: Double? = null,
    val bavg: Double? = null, val w5: Double? = null, val w10: Double? = null, val bb: String? = null,
    val ct: Double? = null, val st: Double? = null
)

class FormEntry(val d: String = "", val f: String = "", val o: String = "", val r: Int = 0, val no: Boolean = false, val b: Int = 0)
class BFormEntry(val d: String = "", val f: String = "", val o: String = "", val w: Int = 0, val r: Int = 0)

class RealPlayer(
    val id: String = "", val name: String = "", val short: String = "", val full: String? = null,
    val country: String = "", val dob: String? = null, val role: String = "BAT",
    val bat: String = "", val bowl: String = "", val ipl: String? = null, val iplLast: String? = null,
    val off: Map<String, OffStats>? = null, val offSrc: String? = null, val offDate: String? = null,
    val afg: Boolean = false, val cInf: Boolean = false,
    val stats: Map<String, FmtStats> = emptyMap(),
    val form: List<FormEntry> = emptyList(), val bform: List<BFormEntry> = emptyList(),
    val ms: List<String> = emptyList(), val cricinfo: String? = null,
    val img: String? = null
)

class TeamRec(val fmt: String = "", val team: String = "", val opp: String = "", val runs: Int = 0, val wk: Int = 0,
              val balls: Int = 0, val date: String = "", val venue: String = "")
class TeamRecSet(val high: List<TeamRec> = emptyList(), val low: List<TeamRec> = emptyList())

class Dataset(
    val meta: DsMeta = DsMeta(),
    val players: List<RealPlayer> = emptyList(),
    val teamRecords: Map<String, TeamRecSet> = emptyMap()
)

/** Game attributes derived from real statistics (game ratings, NOT official rankings). */
data class BaseAttrs(val tech: Int, val power: Int, val bowl: Int, val field: Int, val keep: Int, val overall: Int, val bowlType: String)

object Repo {
    @Volatile private var loaded = false
    lateinit var ds: Dataset
        private set
    var byId: Map<String, RealPlayer> = emptyMap()
        private set
    var attrs: Map<String, BaseAttrs> = emptyMap()
        private set

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        ctx.assets.open("cricket_data.json").bufferedReader().use { r -> loadFrom(r) }
        try { ctx.assets.open("domestic_states.json").bufferedReader().use { loadDomestic(it) } } catch (_: Exception) {}
    }

    /** Real current Indian domestic (state) team per player id, from Wikipedia infoboxes (tools/domestic_states.py). */
    var domestic: Map<String, String> = emptyMap()
        private set

    fun loadDomestic(r: java.io.Reader) {
        val o = com.google.gson.JsonParser.parseReader(r).asJsonObject
        domestic = o.entrySet().mapNotNull { (k, v) -> v.asJsonObject["state"]?.takeIf { !it.isJsonNull }?.asString?.let { k to it } }.toMap()
    }

    /** Also used by JVM unit tests. */
    @Synchronized
    fun loadFrom(r: java.io.Reader) {
        ds = Gson().fromJson(r, Dataset::class.java)
        byId = ds.players.associateBy { it.id }
        attrs = ds.players.associate { it.id to Ratings.derive(it) }
        loaded = true
    }

    fun isLoaded() = loaded
}

object Ratings {
    private fun shrink(v: Double, w: Double, prior: Double) = prior + (v - prior) * w

    fun bowlTypeOf(style: String, role: String): String {
        val s = style.lowercase()
        return when {
            s.isBlank() -> if (role == "BOWL" || role == "AR") "PACE" else "NONE"
            listOf("spin", "break", "orthodox", "googly", "chinaman", "slow left", "off ", "leg").any { s.contains(it) } ||
                s.startsWith("off") || s.startsWith("leg") -> "SPIN"
            else -> "PACE"
        }
    }

    fun derive(p: RealPlayer): BaseAttrs {
        val s = p.stats
        val off = p.off ?: emptyMap()
        val techParts = ArrayList<Pair<Double, Double>>()
        val powParts = ArrayList<Pair<Double, Double>>()
        for ((f, base, mul) in listOf(Triple("TEST", 35.0, 1.05), Triple("ODI", 33.0, 1.0), Triple("T20I", 38.0, 1.1), Triple("IPL", 38.0, 1.1))) {
            val x = s[f]
            var avg: Double? = null; var sr: Double? = null; var inn = 0.0
            if (x != null && x.inn > 0) {
                avg = x.runs.toDouble() / max(1, x.inn - x.no); sr = x.sr; inn = x.inn.toDouble()
            } else {
                val o = off[f]
                if (o?.avg != null && o.m != null) { avg = o.avg; inn = o.m * 0.85 }
            }
            if (avg == null || inn <= 0) continue
            val w = inn / (inn + 12); val wp = inn / (inn + 25)
            techParts.add(shrink(min(97.0, base + 4 + avg * mul), w, 42.0) to inn)
            if ((f == "T20I" || f == "IPL") && sr != null) powParts.add(shrink(min(97.0, 32 + (sr - 100) * 0.85 + avg * 0.5), wp, 40.0) to inn)
            if (f == "ODI" && sr != null) powParts.add(shrink(min(97.0, 30 + (sr - 70) * 1.0 + avg * 0.4), wp, 40.0) to inn * 0.5)
        }
        val tech = if (techParts.isEmpty()) 40.0 else techParts.sumOf { it.first * it.second } / techParts.sumOf { it.second }
        val power = if (powParts.isEmpty()) tech * 0.85 else powParts.sumOf { it.first * it.second } / powParts.sumOf { it.second }
        val bw = ArrayList<Pair<Double, Double>>()
        var catches = 0.0; var matches = 0.0; var stumpings = 0.0
        for (f in listOf("TEST", "ODI", "T20I", "IPL")) {
            val x = s[f]
            var balls = 0.0; var rc = 0.0; var wk = 0.0
            if (x != null) { catches += x.ct; matches += x.m; stumpings += x.st }
            if (x != null && x.balls > 0) { balls = x.balls.toDouble(); rc = x.rc.toDouble(); wk = x.wk.toDouble() }
            else {
                val o = off[f]
                if (o?.balls != null && o.wk != null && o.bavg != null && o.wk > 0) { balls = o.balls; wk = o.wk; rc = o.bavg * o.wk }
                if (x == null && o != null) { catches += o.ct ?: 0.0; matches += o.m ?: 0.0; stumpings += o.st ?: 0.0 }
            }
            if (balls < 30) continue
            val econ = rc * 6 / balls; val bavg = rc / max(1.0, wk); val srb = balls / max(1.0, wk)
            val v = when (f) {
                "T20I", "IPL" -> 100 - (econ - 6.5) * 7 - (bavg - 18) * 0.7
                "ODI" -> 100 - (econ - 4.6) * 9 - (bavg - 23) * 0.7
                else -> 100 - (bavg - 22) * 1.1 - max(0.0, srb - 50) * 0.15
            }
            val w = balls / (balls + 900)
            bw.add(shrink(min(v, 97.0), w, 35.0) to balls)
        }
        val bowl = if (bw.isEmpty()) 30.0 else bw.sumOf { it.first * it.second } / bw.sumOf { it.second }
        val bat = 0.55 * max(tech, power) + 0.45 * min(tech, power)
        val raw = when (p.role) {
            "BOWL" -> bowl
            "AR" -> max(bat, bowl) * 0.75 + min(bat, bowl) * 0.25 + 2
            "WK" -> bat + 2 + 7
            else -> bat + 7
        }
        val overall = (44 + (min(97.0, max(40.0, raw)) - 40) * 0.98).roundToInt().coerceIn(45, 97)
        val cpm = if (matches > 0) catches / matches else 0.3
        val field = (48 + cpm * 70).roundToInt().coerceIn(40, 92)
        val keep = if (p.role == "WK") (72 + min(18.0, stumpings / max(1.0, matches) * 120)).roundToInt() else 30
        return BaseAttrs(tech.roundToInt().coerceIn(20, 97), power.roundToInt().coerceIn(20, 97), bowl.roundToInt().coerceIn(15, 97),
            field, keep, overall, bowlTypeOf(p.bowl, p.role))
    }

    /** Overall rating from (possibly developed) attributes - same scale as derive(). */
    fun overall(role: String, tech: Int, power: Int, bowl: Int): Int {
        val bat = 0.55 * max(tech, power) + 0.45 * min(tech, power)
        val raw = when (role) {
            "BOWL" -> bowl.toDouble()
            "AR" -> max(bat, bowl.toDouble()) * 0.75 + min(bat, bowl.toDouble()) * 0.25 + 2
            "WK" -> bat + 9
            else -> bat + 7
        }
        return (44 + (min(97.0, max(40.0, raw)) - 40) * 0.98).roundToInt().coerceIn(40, 99)
    }
}
