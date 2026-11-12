import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import java.io.File
import kotlin.random.Random
fun gapOf(s: CareerSave, ids: List<String>, label: String) {
    var bat = 0.0; var bowl = 0.0; var n = 0
    for (id in ids) { val c = s.clubs[id] ?: continue; TeamSelector.autoPick(c, s.players)
        val xi = c.xi.mapNotNull { s.players[it] }
        bat += xi.sortedByDescending { it.tech * 0.6 + it.power * 0.4 }.take(7).map { it.tech * 0.6 + it.power * 0.4 }.average()
        bowl += xi.sortedByDescending { it.bowl }.take(5).map { it.bowl.toDouble() }.average(); n++ }
    println("%-10s bat %.1f bowl %.1f gap %.1f".format(label, bat / n, bowl / n, (bat - bowl) / n))
}
fun main() {
    File("../../app/src/main/assets/cricket_data.json").bufferedReader().use { Repo.loadFrom(it) }
    File("../../app/src/main/assets/domestic_states.json").bufferedReader().use { Repo.loadDomestic(it) }
    val s = Modes.newCareer(1, "C", Modes.INTL, "N_IND", 1.0, 1, Random(1))
    gapOf(s, listOf("N_IND", "N_AUS", "N_ENG", "N_SA", "N_NZ", "N_PAK", "N_SL", "N_WI"), "Intl top8")
    gapOf(s, listOf("N_BAN", "N_AFG", "N_IRE", "N_ZIM"), "Intl low4")
    val ip = Modes.newCareer(1, "C", Modes.IPL, "F_MI", 1.0, 1, Random(2)); gapOf(ip, ip.clubs.values.filter { it.kind == "FRANCHISE" }.map { it.id }, "IPL")
    val d = Modes.newCareer(1, "C", Modes.DOMESTIC, "S_BEN", 1.0, 1, Random(3)); gapOf(d, d.clubs.keys.filter { it.startsWith("S_") }, "States")
}
