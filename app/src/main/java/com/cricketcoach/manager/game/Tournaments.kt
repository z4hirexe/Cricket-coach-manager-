package com.cricketcoach.manager.game

import com.cricketcoach.manager.data.Repo

data class TourType(val id: String, val name: String, val desc: String, val fixedFormat: String?, val kind: String)

object Tournaments {
    val TYPES = listOf(
        TourType("IPL", "Indian Premier League", "10 franchises with their real 2026 rosters • league + Qualifier/Eliminator playoffs • Impact Player rule", "T20", "FRANCHISE"),
        TourType("ODI_WC", "ICC Cricket World Cup", "10 nations • round robin • semi-finals • final (ODI)", "ODI", "NATION"),
        TourType("T20_WC", "ICC T20 World Cup", "12 nations • 2 groups of 6 • semi-finals • final", "T20", "NATION"),
        TourType("CT", "ICC Champions Trophy", "8 nations • 2 groups of 4 • semi-finals • final (ODI)", "ODI", "NATION"),
        TourType("WTC", "World Test Championship", "9 nations • league of Test matches (12 pts win / 4 draw) • final", "TEST", "NATION"),
        TourType("ASIA", "Asia Cup", "India, Pakistan, Sri Lanka, Bangladesh, Afghanistan • league + final", null, "NATION"),
        TourType("BILAT", "Bilateral Series", "Any two nations • 3 or 5 matches • Test, ODI or T20", null, "NATION"),
        TourType("CUSTOM", "Custom Tournament", "Pick teams, format and structure", null, "ANY")
    )

    private val VENUES = mapOf(
        "N_IND" to "Eden Gardens, Kolkata", "N_AUS" to "Melbourne Cricket Ground", "N_ENG" to "Lord's, London",
        "N_PAK" to "Gaddafi Stadium, Lahore", "N_SA" to "Newlands, Cape Town", "N_NZ" to "Basin Reserve, Wellington",
        "N_BAN" to "Shere Bangla Stadium, Dhaka", "N_SL" to "R. Premadasa Stadium, Colombo", "N_AFG" to "Sharjah Cricket Stadium",
        "N_WI" to "Kensington Oval, Barbados", "N_IRE" to "Malahide, Dublin", "N_ZIM" to "Harare Sports Club",
        "F_CSK" to "M. A. Chidambaram Stadium, Chennai", "F_MI" to "Wankhede Stadium, Mumbai", "F_RCB" to "M. Chinnaswamy Stadium, Bengaluru",
        "F_KKR" to "Eden Gardens, Kolkata", "F_DC" to "Arun Jaitley Stadium, Delhi", "F_RR" to "Sawai Mansingh Stadium, Jaipur",
        "F_PBKS" to "Maharaja Yadavindra Singh Stadium, Mullanpur", "F_SRH" to "Rajiv Gandhi Intl Stadium, Hyderabad",
        "F_LSG" to "Ekana Cricket Stadium, Lucknow", "F_GT" to "Narendra Modi Stadium, Ahmedabad"
    )

    fun venueOf(id: String): String? = VENUES[id]

    /** Teams that take part in a tournament type (user picks one of these to manage). */
    fun teamsFor(type: String): List<String> = when (type) {
        "IPL" -> Consts.IPL.map { it.id }
        "ODI_WC" -> listOf("IND", "AUS", "ENG", "PAK", "SA", "NZ", "BAN", "SL", "AFG", "WI").map { "N_$it" }
        "CT" -> listOf("IND", "AUS", "ENG", "PAK", "SA", "NZ", "BAN", "AFG").map { "N_$it" }
        "WTC" -> listOf("IND", "AUS", "ENG", "PAK", "SA", "NZ", "BAN", "SL", "WI").map { "N_$it" }
        "ASIA" -> listOf("IND", "PAK", "SL", "BAN", "AFG").map { "N_$it" }
        "CUSTOM" -> Consts.NATIONS.map { it.id } + Consts.IPL.map { it.id }
        else -> Consts.NATIONS.map { it.id }
    }

    /** Build/refresh national sides from the current (developed) player pool. */
    fun ensureNations(save: CareerSave) {
        for (n in Consts.NATIONS) {
            val c = save.clubs.getOrPut(n.id) { Club(id = n.id, name = n.name, short = n.short, primary = n.primary, secondary = n.secondary, kind = "NATION", country = n.country, stadium = VENUES[n.id] ?: n.name) }
            val pool = save.players.values.filter { it.country == n.country && !it.retired && !it.academy }.sortedByDescending { it.overall() }
            val pick = LinkedHashSet<String>()
            pool.filter { it.role == "WK" }.take(2).forEach { pick.add(it.id) }
            pool.filter { it.role == "BOWL" || it.role == "AR" }.sortedByDescending { it.bowl }.take(7).forEach { pick.add(it.id) }
            for (p in pool) { if (pick.size >= 17) break; pick.add(p.id) }
            c.squad = pick.toMutableList()
            if (!c.isUser || c.xi.isEmpty()) TeamSelector.autoPick(c, save.players)
        }
    }

    /** IPL franchises use their real latest-season rosters from the Cricsheet IPL dataset. */
    fun ensureIpl(save: CareerSave) {
        val latest = Repo.ds.players.mapNotNull { it.iplLast?.take(4) }.maxOrNull() ?: "2026"
        for (f in Consts.IPL) {
            val c = save.clubs.getOrPut(f.id) { Club(id = f.id, name = f.name, short = f.short, primary = f.primary, secondary = f.secondary, kind = "FRANCHISE", country = "India", stadium = VENUES[f.id] ?: f.name) }
            c.squad = Repo.ds.players.filter { it.ipl == f.name && it.iplLast?.startsWith(latest) == true }
                .mapNotNull { save.players[it.id] }.filter { !it.retired }.sortedByDescending { it.overall() }.take(25).map { it.id }.toMutableList()
            if (c.squad.size < 12) {
                val extra = save.players.values.filter { it.country == "India" && !it.retired && it.id !in c.squad }.sortedByDescending { it.overall() }
                for (p in extra) { if (c.squad.size >= 15) break; if (save.clubs.values.none { o -> o.kind == "FRANCHISE" && p.id in o.squad }) c.squad.add(p.id) }
            }
            TeamSelector.autoPick(c, save.players)
        }
    }

    fun create(
        save: CareerSave, type: String, userTeam: String, format: String?, teams: List<String> = emptyList(),
        matches: Int = 3, double: Boolean = false, knockout: String = "SEMIS"
    ): Competition {
        // career-mode teams keep their own squads (the user's national side / IPL franchise must not be reset)
        if (save.mode != Modes.INTL) ensureNations(save)
        if (save.mode != Modes.IPL) ensureIpl(save)
        val t = TYPES.first { it.id == type }
        val fmt = t.fixedFormat ?: format ?: "T20"
        val n = { ids: List<String> -> ids.map { "N_$it" } }
        val comp = Competition(id = "T${save.nextId++}", key = type, format = fmt, kind = t.kind, season = save.season, userTeam = userTeam)
        comp.name = "${t.name} (Season ${save.season})"
        when (type) {
            "IPL" -> { comp.teams = Consts.IPL.map { it.id }.toMutableList(); comp.knockout = "IPL"; comp.allowImpact = true; CompEngine.addLeagueFixtures(comp, false) }
            "ODI_WC" -> { comp.teams = n(listOf("IND", "AUS", "ENG", "PAK", "SA", "NZ", "BAN", "SL", "AFG", "WI")).toMutableList(); comp.knockout = "SEMIS"; CompEngine.addLeagueFixtures(comp, false) }
            "T20_WC" -> {
                comp.teams = Consts.NATIONS.map { it.id }.toMutableList()
                val seeded = comp.teams.sortedByDescending { Career.strength(save, save.clubs[it]!!) }
                val a = mutableListOf<String>(); val b = mutableListOf<String>()
                seeded.forEachIndexed { i, id -> if (i % 4 == 0 || i % 4 == 3) a.add(id) else b.add(id) }
                comp.groups = mutableListOf(a, b); comp.knockout = "SEMIS"; CompEngine.addLeagueFixtures(comp, false)
            }
            "CT" -> {
                comp.teams = n(listOf("IND", "AUS", "ENG", "PAK", "SA", "NZ", "BAN", "AFG")).toMutableList()
                comp.groups = mutableListOf(n(listOf("IND", "PAK", "NZ", "BAN")).toMutableList(), n(listOf("AUS", "ENG", "SA", "AFG")).toMutableList())
                comp.knockout = "SEMIS"; CompEngine.addLeagueFixtures(comp, false)
            }
            "WTC" -> {
                comp.teams = n(listOf("IND", "AUS", "ENG", "PAK", "SA", "NZ", "BAN", "SL", "WI")).toMutableList()
                comp.knockout = "FINAL"; comp.pointsWin = 12; comp.pointsDraw = 4; comp.pointsNR = 4
                CompEngine.addLeagueFixtures(comp, false)
            }
            "ASIA" -> { comp.teams = n(listOf("IND", "PAK", "SL", "BAN", "AFG")).toMutableList(); comp.knockout = "FINAL"; CompEngine.addLeagueFixtures(comp, false) }
            "BILAT" -> {
                comp.teams = teams.take(2).toMutableList(); comp.series = true; comp.knockout = "NONE"
                comp.name = "${save.clubs[teams[0]]?.name} v ${save.clubs[teams[1]]?.name} ${fmt} series (Season ${save.season})"
                for (i in 0 until matches) comp.fixtures.add(Fixture("${comp.id}-${comp.nextFixtureId++}", if (i % 2 == 0) teams[0] else teams[1], if (i % 2 == 0) teams[1] else teams[0], "Match ${i + 1}", 0, i + 1))
                if (fmt == "TEST") { comp.pointsWin = 12; comp.pointsDraw = 4 }
            }
            else -> {
                comp.teams = teams.toMutableList(); comp.knockout = if (teams.size < 4 && knockout == "SEMIS") "FINAL" else knockout
                comp.kind = if (teams.all { it.startsWith("F_") }) "FRANCHISE" else "NATION"
                comp.allowImpact = fmt == "T20" && comp.kind == "FRANCHISE"
                if (fmt == "TEST") { comp.pointsWin = 12; comp.pointsDraw = 4 }
                CompEngine.addLeagueFixtures(comp, double)
            }
        }
        save.clubs[userTeam]?.let { TeamSelector.autoPick(it, save.players) }
        save.tournament = comp
        save.addNews("New tournament: ${comp.name}. You manage ${save.clubs[userTeam]?.name}.")
        return comp
    }
}
