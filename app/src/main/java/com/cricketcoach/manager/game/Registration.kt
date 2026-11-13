package com.cricketcoach.manager.game

import kotlin.math.max
import kotlin.math.min

/**
 * v2.5 Phase E: mandatory tournament squad registration + pre-match team sheet rules.
 *
 *  - Before the user's first match in the IPL, Ranji Trophy, Vijay Hazare, Syed Mushtaq Ali, an international
 *    tour (bilateral series) or an ICC event, the user must register a tournament squad (15-16 players; national series use a 50-70 player selection pool).
 *  - Stage 2: the selection window opens 5 days before the first match and is shown on the calendar.
 *  - The registered squad is stored on the [Competition] (separate from the club squad and from the XI).
 *  - It locks when the user's first match in that competition has been played; after that only permitted
 *    replacements are allowed (registered player injured, retired or no longer at the club), with a limit.
 *  - The match-day XI may only use registered players.
 */
object Registration {

    /** bowling rating that counts as a genuine bowling option (same threshold the match engine uses) */
    const val BOWL_OPTION = 45

    class Rule(val min: Int, val max: Int, val overseasMax: Int, val minKeepers: Int, val minBowlers: Int, val replacements: Int, val label: String) {
        fun sizeText() = if (min == max) "$min players" else "$min-$max players"
    }

    private val DOMESTIC_KEYS = setOf("RANJI", "VHT", "SMAT")
    private val ICC_KEYS = setOf("ODI_WC", "T20_WC", "CT")

    /** Registration rule for the user team in [comp], or null when no registration is required. */
    fun rule(save: CareerSave, comp: Competition): Rule? {
        if (comp.userTeam == null || !save.isManaged(comp.userTeam!!)) return null
        if (save.tournament?.id == comp.id) return null
        return when {
            comp.key == "IPL" -> Rule(15, 16, 8, 1, 5, 4, "IPL squad")
            comp.key in DOMESTIC_KEYS -> Rule(15, 16, 99, 1, 5, 3, "${short(comp)} squad")
            comp.key in ICC_KEYS -> Rule(15, 16, 99, 1, 5, 2, "ICC event squad")
            comp.kind == "NATION" && comp.series -> Rule(15, 16, 99, 1, 5, 3, "Touring squad")
            else -> null
        }
    }

    fun short(comp: Competition) = when (comp.key) {
        "RANJI" -> "Ranji Trophy"; "VHT" -> "Vijay Hazare"; "SMAT" -> "Syed Mushtaq Ali"; "IPL" -> "IPL"
        "INTL_TEST" -> "Test series"; "INTL_ODI" -> "ODI series"; "INTL_T20" -> "T20I series"; else -> comp.name
    }

    fun required(save: CareerSave, comp: Competition) = rule(save, comp) != null
    fun registered(save: CareerSave, comp: Competition) = rule(save, comp) == null || (comp.regDone && comp.reg.isNotEmpty())
    /** The squad locks once the user's first match of the competition has been played. */
    fun locked(save: CareerSave, comp: Competition): Boolean {
        val u = comp.userTeam ?: return false
        return comp.regDone && comp.fixtures.any { it.played && (it.home == u || it.away == u) }
    }

    /** Players who may be registered. National series use a 50-70 player selection pool; other teams use their club squad. */
    fun pool(save: CareerSave, comp: Competition): List<GPlayer> {
        val c = save.clubs[comp.userTeam ?: return emptyList()] ?: return emptyList()
        if (c.kind == "NATION") {
            val wide = Modes.nationPool(save, c)
            val base = if (wide.size >= 50) wide else (wide + c.squad.mapNotNull { save.players[it] }.filter { !it.retired }).distinctBy { it.id }
            return base.sortedByDescending { it.overall() }.take(70)
        }
        return c.squad.mapNotNull { save.players[it] }.filter { !it.retired }
    }

    /** XI pool for a match: the registered squad (still at the club) when registration applies, else null (= whole squad). */
    fun xiPool(save: CareerSave, comp: Competition): Set<String>? {
        if (rule(save, comp) == null || !comp.regDone) return null
        val c = save.clubs[comp.userTeam ?: return null] ?: return null
        return comp.reg.filter { it in c.squad }.toSet()
    }

    /** Next competition (in season order) that still needs the user's registration. */
    fun nextPending(save: CareerSave): Competition? {
        val ids = if (save.isMultiTeam) save.managedTeamIds.toSet() else setOf(save.userClubId)
        return save.league.filter { required(save, it) && !registered(save, it) && it.fixtures.any { f -> !f.played && (f.home in ids || f.away in ids) } }
            .minByOrNull { c -> c.fixtures.filter { !it.played }.minOfOrNull { it.day } ?: 9999 }
    }

    /** Problems that block registering [ids] for [comp] (empty = valid). */
    fun problems(save: CareerSave, comp: Competition, ids: Collection<String>): List<String> {
        val r = rule(save, comp) ?: return emptyList()
        val c = save.clubs[comp.userTeam!!] ?: return listOf("Unknown team")
        val ps = ids.mapNotNull { save.players[it] }
        val out = ArrayList<String>()
        if (ids.size != ids.distinct().size || ps.size != ids.size) out += "Select distinct, valid players"
        if (ps.size < r.min) out += "Select at least ${r.min} players (${ps.size} selected)"
        if (ps.size > r.max) out += "At most ${r.max} players (${ps.size} selected)"
        // role minimums are capped by what the club squad can actually offer
        val all = pool(save, comp)
        val needWk = min(r.minKeepers, all.count { it.role == "WK" })
        val needBowl = min(r.minBowlers, all.count { it.bowl >= BOWL_OPTION })
        if (ps.count { it.role == "WK" } < needWk) out += "Register at least $needWk wicket-keeper"
        if (ps.count { it.bowl >= BOWL_OPTION } < needBowl) out += "Register at least $needBowl bowling options (bowling $BOWL_OPTION+)"
        val os = ps.count { TeamSelector.isOverseas(it, c) }
        if (os > r.overseasMax) out += "Too many overseas players ($os/${r.overseasMax})"
        val available = pool(save, comp).map { it.id }.toSet()
        val notHere = ps.filter { it.id !in available || it.retired }
        if (notHere.isNotEmpty()) out += "Not available to register: " + notHere.joinToString { it.name }
        return out
    }

    /** A balanced, valid default selection (used by the Auto-select button and by auto-simulated matches). */
    fun autoSelect(save: CareerSave, comp: Competition): List<String> {
        val r = rule(save, comp) ?: return emptyList()
        val c = save.clubs[comp.userTeam!!] ?: return emptyList()
        val pool = pool(save, comp).sortedByDescending { it.overall() + (if (it.injured) -30 else 0) + it.form / 10 }
        val target = min(r.max, max(r.min, min(pool.size, r.min + 3)))
        val pick = LinkedHashSet<GPlayer>()
        fun os() = pick.count { TeamSelector.isOverseas(it, c) }
        fun ok(p: GPlayer) = p !in pick && (!TeamSelector.isOverseas(p, c) || os() < r.overseasMax)
        pool.filter { it.role == "WK" }.take(2).forEach { if (ok(it)) pick += it }
        pool.filter { it.bowl >= BOWL_OPTION }.sortedByDescending { it.bowl + (if (it.injured) -30 else 0) }.forEach { if (pick.count { p -> p.bowl >= BOWL_OPTION } < r.minBowlers + 2 && ok(it)) pick += it }
        pool.filter { it.role == "BAT" }.take(5).forEach { if (pick.size < target && ok(it)) pick += it }
        for (p in pool) { if (pick.size >= target) break; if (ok(p)) pick += p }
        return pick.take(r.max).map { it.id }
    }

    /** Register (or, before the lock, re-register) the squad. Returns an error or null. */
    fun register(save: CareerSave, comp: Competition, ids: List<String>, auto: Boolean = false): String? {
        if (rule(save, comp) == null) return "No registration needed for ${comp.name}."
        if (locked(save, comp)) return "The ${short(comp)} squad is locked - only permitted replacements are allowed."
        val p = problems(save, comp, ids)
        // a simulated match must never stall: the default squad is registered even if the club can't meet every rule
        if (p.isNotEmpty() && !(auto && ids.isNotEmpty())) return p.first()
        comp.reg = ids.distinct().toMutableList(); comp.regDone = true; comp.regAuto = auto
        val c = save.clubs[comp.userTeam!!]!!
        if (c.kind == "NATION") {
            val keep = (comp.reg + c.squad.filter { it !in comp.reg }).distinct()
            c.squad = keep.take(Modes.maxSquad(save)).toMutableList()
        }
        save.addNews(if (auto) "${c.name}'s ${short(comp)} squad (${comp.reg.size}) was registered automatically for a simulated match."
            else "${c.name} registered their ${short(comp)} squad: ${comp.reg.size} players. It locks after the first match.")
        return null
    }

    /** Registered players that may be replaced after the lock (injured, retired, or no longer at the club). */
    fun replaceable(save: CareerSave, comp: Competition): List<String> {
        val c = save.clubs[comp.userTeam ?: return emptyList()] ?: return emptyList()
        return comp.reg.filter { val p = save.players[it]; p == null || p.injured || p.retired || it !in c.squad }
    }

    fun replacementsLeft(save: CareerSave, comp: Competition) = (rule(save, comp)?.replacements ?: 0) - comp.regRepl

    /** Candidates to come in as a replacement: club players not registered. */
    fun replacementPool(save: CareerSave, comp: Competition): List<GPlayer> =
        pool(save, comp).filter { it.id !in comp.reg && !it.injured }.sortedByDescending { it.overall() }

    /** Permitted replacement after the lock. Returns an error or null. */
    fun replace(save: CareerSave, comp: Competition, outPid: String, inPid: String): String? {
        val r = rule(save, comp) ?: return "No registration for this competition."
        if (!comp.regDone) return "Register a squad first."
        if (!locked(save, comp)) {   // before the lock: free swap
            if (outPid !in comp.reg) return "Not in the registered squad."
            val after = comp.reg.map { if (it == outPid) inPid else it }
            return register(save, comp, after)
        }
        if (replacementsLeft(save, comp) <= 0) return "No replacements left (${r.replacements} used)."
        if (outPid !in replaceable(save, comp)) return "${save.players[outPid]?.name ?: "This player"} is fit and available - replacements are only for injured or departed players."
        val c = save.clubs[comp.userTeam!!]!!
        val pin = save.players[inPid] ?: return "Unknown player"
        if (inPid in comp.reg) return "${pin.name} is already registered."
        if (pin.retired || inPid !in pool(save, comp).map { it.id }) return "${pin.name} is not in the selection pool."
        if (pin.injured) return "${pin.name} is injured."
        val after = comp.reg.map { if (it == outPid) inPid else it }
        if (after.mapNotNull { save.players[it] }.count { TeamSelector.isOverseas(it, c) } > r.overseasMax) return "Overseas limit (${r.overseasMax}) would be exceeded."
        if (inPid !in c.squad) {
            c.squad = (after + c.squad.filter { it !in after }).distinct().take(Modes.maxSquad(save)).toMutableList()
        }
        comp.reg = after.toMutableList(); comp.regRepl++
        val pout = save.players[outPid]
        val why = when { pout == null -> "unavailable"; pout.retired -> "retired"; outPid !in c.squad -> "left the club"; else -> pout.injury.ifBlank { "injury" } }
        comp.regLog.add("S${save.season} D${save.day}: ${pin.name} replaces ${pout?.name ?: outPid} ($why)")
        c.xi.remove(outPid); c.bowlers.remove(outPid)
        save.addNews("${short(comp)}: ${pin.name} replaces ${pout?.name ?: "a player"} ($why) in the ${c.short} squad.")
        return null
    }

    /** Auto-simulated user matches (sim to season end, calendar jumps) register a default squad if the user hasn't. */
    fun ensureForAutoSim(save: CareerSave, comp: Competition) {
        val r = rule(save, comp) ?: return
        if (comp.regDone) return
        topUpPool(save, comp, r.min)
        register(save, comp, autoSelect(save, comp), auto = true)
    }

    /** National / state pools smaller than the minimum get free call-ups (unattached players only, never guest professionals). */
    fun topUpPool(save: CareerSave, comp: Competition, min: Int) {
        val c = save.clubs[comp.userTeam ?: return] ?: return
        if (c.kind != "NATION" && c.kind != "DOMESTIC") return
        var g = 0
        while (pool(save, comp).size < min && g++ < 30) {
            val p = Modes.selectionPool(save, c.id).firstOrNull { c.kind == "NATION" || it.clubId.isEmpty() || (save.isMultiTeam && save.clubs[it.clubId]?.kind == "FRANCHISE") } ?: break
            Modes.callUp(save, p.id, c.id)
        }
        // no unattached players left: the state side adds clearly-labelled fictional domestic players
        if (c.kind == "DOMESTIC" && pool(save, comp).size < min) Modes.fillDomestic(save, c, min, kotlin.random.Random.Default)
    }

    /** Season start: remind the user which squads must be registered. */
    fun onSeasonStart(save: CareerSave) {
        val need = save.league.filter { required(save, it) && !registered(save, it) }
        if (need.isNotEmpty()) save.addNews("Squad registration required before your first match: " + need.joinToString { "${short(it)} (${rule(save, it)!!.sizeText()})" } + ".")
    }

    /* ===================================================================== match-day team sheet */

    /** Blocking problems with the user's XI for a match (empty = the team sheet can be confirmed). */
    fun sheetErrors(save: CareerSave, comp: Competition, c: Club): List<String> {
        val out = ArrayList<String>()
        val pool = xiPool(save, comp)
        val xi = c.xi.mapNotNull { save.players[it] }
        if (c.xi.size != 11) out += "Pick exactly 11 players (${c.xi.size}/11)"
        if (c.captain !in c.xi) out += "Choose a captain from the XI"
        if (c.vice !in c.xi || c.vice == c.captain) out += "Choose a vice-captain from the XI"
        if (c.keeper !in c.xi) out += "Choose a wicket-keeper from the XI"
        if (xi.any { it.injured }) out += "Injured: " + xi.filter { it.injured }.joinToString { it.name }
        if (pool != null && c.xi.any { it !in pool }) out += "Not registered: " + c.xi.filter { it !in pool }.mapNotNull { save.players[it]?.name }.joinToString()
        if (c.xi.any { it !in c.squad }) out += "No longer at the club: " + c.xi.filter { it !in c.squad }.mapNotNull { save.players[it]?.name }.joinToString()
        val os = xi.count { TeamSelector.isOverseas(it, c) }
        if (c.kind != "NATION" && os > 4) out += "Max 4 overseas players in the XI ($os)"
        val bowlOpts = c.bowlers.count { it in c.xi }
        if (bowlOpts < 5) out += "Bowling plan needs at least 5 bowlers from the XI ($bowlOpts)"
        return out
    }

    /** Non-blocking notes (tired / out-of-form players, keeper not a specialist, etc.). */
    fun sheetWarnings(save: CareerSave, comp: Competition, c: Club): List<String> {
        val out = ArrayList<String>()
        val xi = c.xi.mapNotNull { save.players[it] }
        xi.filter { it.fitness < 60 }.takeIf { it.isNotEmpty() }?.let { out += "Tired: " + it.joinToString { p -> "${p.short()} ${p.fitness}%" } }
        xi.filter { it.form < 40 }.takeIf { it.isNotEmpty() }?.let { out += "Out of form: " + it.joinToString { p -> p.short() } }
        save.players[c.keeper]?.let { if (it.role != "WK") out += "${it.short()} is not a specialist keeper" }
        if (xi.count { it.bowl >= 50 } < 5) out += "Fewer than 5 genuine bowlers (bowling 50+)"
        val roles = c.bowlRoles.filterKeys { it in c.bowlers && it in c.xi }
        if (comp.format != "TEST" && roles.isNotEmpty() && roles.values.none { it == "DEATH" }) out += "No death-overs specialist set"
        return out
    }

    /** Availability label for a player in the Match Centre ("" = available). */
    fun availability(save: CareerSave, comp: Competition, c: Club, p: GPlayer): String {
        val pool = xiPool(save, comp)
        return when {
            p.injured -> "INJURED ${p.injuryDays}d"
            p.id !in c.squad -> "LEFT CLUB"
            pool != null && p.id !in pool -> "NOT REGISTERED"
            p.fitness < 60 -> "TIRED"
            else -> ""
        }
    }

    fun roleChip(r: String, test: Boolean) = when (r) { "NEW" -> "New"; "MID" -> if (test) "Stock" else "Mid"; "DEATH" -> if (test) "Strike" else "Death"; "PART" -> "Part"; else -> "Auto" }
    val BOWL_ROLES = listOf("AUTO", "NEW", "MID", "DEATH", "PART")
    fun roleLabel(r: String, test: Boolean) = when (r) {
        "NEW" -> "New ball"; "MID" -> if (test) "Stock" else "Middle"; "DEATH" -> if (test) "Strike" else "Death"; "PART" -> "Part-time"; else -> "Auto"
    }
}
