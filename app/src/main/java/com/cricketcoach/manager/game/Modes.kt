package com.cricketcoach.manager.game

import com.cricketcoach.manager.data.Repo
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** An Indian domestic (state) side. Venues are the teams' real home grounds; colours/badges are original (no official crests). */
data class StateTeam(val id: String, val name: String, val short: String, val zone: String, val primary: Long, val secondary: Long, val venue: String)

data class CareerTypeDef(val id: String, val title: String, val sub: String, val desc: String)

/**
 * Career types beyond the original club career:
 *  - INTL: coach of a national team (home/away bilateral series + the ICC event of that year)
 *  - DOMESTIC: an Indian state team (Duleep Trophy, Ranji Trophy, Syed Mushtaq Ali Trophy, Vijay Hazare Trophy, Irani Cup)
 *  - IPL: one of the ten real IPL franchises (70-match league in the real 2-group format + playoffs, auctions)
 */
object Modes {
    const val CLUB = "CLUB"; const val INTL = "INTL"; const val DOMESTIC = "DOMESTIC"; const val IPL = "IPL"

    val TYPES = listOf(
        CareerTypeDef(INTL, "International", "Coach a national team", "Pick your country's squad, play home & away Test, ODI and T20I series and the ICC event of each year (2027 ODI World Cup, 2028 T20 World Cup, 2029 Champions Trophy...)."),
        CareerTypeDef(DOMESTIC, "Domestic (India)", "Manage a state team", "Bengal, Mumbai, Karnataka, Tamil Nadu, Delhi and 27 more. Full Indian domestic season: Duleep Trophy, Ranji Trophy, Syed Mushtaq Ali Trophy, Vijay Hazare Trophy and Irani Cup."),
        CareerTypeDef(IPL, "IPL Franchise", "Run a T20 franchise", "KKR, CSK, MI, RCB, SRH, RR, DC, PBKS, GT or LSG with their real latest rosters. 70-match league + playoffs, purse, mini & mega auctions."),
        CareerTypeDef(CLUB, "Own Club (New Team)", "Create your own team", "Design a brand-new club - name, badge, colours, kit and home ground - start in Division 4 and build it into a champion.")
    )

    val ZONES = listOf("North", "South", "East", "West", "Central", "North East")

    val STATES = listOf(
        StateTeam("S_AND", "Andhra", "AND", "South", 0xFF0D47A1, 0xFFFFC107, "ACA-VDCA Stadium, Visakhapatnam"),
        StateTeam("S_ASM", "Assam", "ASM", "East", 0xFF1B5E20, 0xFFFFEB3B, "Barsapara Cricket Stadium, Guwahati"),
        StateTeam("S_BRD", "Baroda", "BRD", "West", 0xFF4A148C, 0xFFFFD54F, "Kotambi Stadium, Vadodara"),
        StateTeam("S_BEN", "Bengal", "BEN", "East", 0xFF1A237E, 0xFFFFB300, "Eden Gardens, Kolkata"),
        StateTeam("S_BIH", "Bihar", "BIH", "East", 0xFF00695C, 0xFFFFFFFF, "Moin-ul-Haq Stadium, Patna"),
        StateTeam("S_CHD", "Chandigarh", "CHD", "North", 0xFF263238, 0xFF4FC3F7, "Sector 16 Stadium, Chandigarh"),
        StateTeam("S_CHH", "Chhattisgarh", "CHH", "Central", 0xFF2E7D32, 0xFFFF7043, "Shaheed Veer Narayan Singh Stadium, Raipur"),
        StateTeam("S_DEL", "Delhi", "DEL", "North", 0xFF0D2C6B, 0xFFE53935, "Arun Jaitley Stadium, Delhi"),
        StateTeam("S_GOA", "Goa", "GOA", "South", 0xFF006064, 0xFFFFF176, "GCA Academy Ground, Porvorim"),
        StateTeam("S_GUJ", "Gujarat", "GUJ", "West", 0xFFB71C1C, 0xFFFFE082, "Narendra Modi Stadium, Ahmedabad"),
        StateTeam("S_HAR", "Haryana", "HAR", "North", 0xFF1565C0, 0xFFFFFFFF, "Chaudhary Bansi Lal Stadium, Lahli"),
        StateTeam("S_HP", "Himachal Pradesh", "HP", "North", 0xFF33691E, 0xFFE1F5FE, "HPCA Stadium, Dharamsala"),
        StateTeam("S_HYD", "Hyderabad", "HYD", "South", 0xFF3E2723, 0xFFFF9800, "Rajiv Gandhi Intl Stadium, Hyderabad"),
        StateTeam("S_JK", "Jammu and Kashmir", "J&K", "North", 0xFF01579B, 0xFFFFFFFF, "Sher-i-Kashmir Stadium, Srinagar"),
        StateTeam("S_JHA", "Jharkhand", "JHA", "East", 0xFF1B5E20, 0xFFFFC107, "JSCA International Stadium, Ranchi"),
        StateTeam("S_KAR", "Karnataka", "KAR", "South", 0xFFB71C1C, 0xFFFFD600, "M. Chinnaswamy Stadium, Bengaluru"),
        StateTeam("S_KER", "Kerala", "KER", "South", 0xFF004D40, 0xFFFFCA28, "Greenfield Stadium, Thiruvananthapuram"),
        StateTeam("S_MP", "Madhya Pradesh", "MP", "Central", 0xFF6A1B9A, 0xFFFFFFFF, "Holkar Stadium, Indore"),
        StateTeam("S_MAH", "Maharashtra", "MAH", "West", 0xFFE65100, 0xFF0D47A1, "MCA Stadium, Pune"),
        StateTeam("S_MUM", "Mumbai", "MUM", "West", 0xFF0D47A1, 0xFFFFD54F, "Wankhede Stadium, Mumbai"),
        StateTeam("S_ODI", "Odisha", "ODI", "East", 0xFFAD1457, 0xFFFFFFFF, "Barabati Stadium, Cuttack"),
        StateTeam("S_PUD", "Puducherry", "PUD", "South", 0xFF00838F, 0xFFFFFFFF, "CAP Siechem Ground, Puducherry"),
        StateTeam("S_PUN", "Punjab", "PUN", "North", 0xFFC62828, 0xFFFFD54F, "Maharaja Yadavindra Singh Stadium, Mullanpur"),
        StateTeam("S_RLY", "Railways", "RLY", "Central", 0xFF8D6E63, 0xFFFFF8E1, "Karnail Singh Stadium, Delhi"),
        StateTeam("S_RAJ", "Rajasthan", "RAJ", "Central", 0xFFF57F17, 0xFF1A237E, "Sawai Mansingh Stadium, Jaipur"),
        StateTeam("S_SAU", "Saurashtra", "SAU", "West", 0xFF311B92, 0xFFFFAB00, "Niranjan Shah Stadium, Rajkot"),
        StateTeam("S_SER", "Services", "SER", "North", 0xFF37474F, 0xFFFF5252, "Palam A Ground, Delhi"),
        StateTeam("S_TN", "Tamil Nadu", "TN", "South", 0xFF1A237E, 0xFFFFEB3B, "M. A. Chidambaram Stadium, Chennai"),
        StateTeam("S_TRI", "Tripura", "TRI", "East", 0xFF2E7D32, 0xFFFFFFFF, "MBB Stadium, Agartala"),
        StateTeam("S_UP", "Uttar Pradesh", "UP", "Central", 0xFF0277BD, 0xFFFFA000, "Green Park, Kanpur"),
        StateTeam("S_UK", "Uttarakhand", "UK", "Central", 0xFF4E342E, 0xFF80DEEA, "Rajiv Gandhi Intl Stadium, Dehradun"),
        StateTeam("S_VID", "Vidarbha", "VID", "Central", 0xFFBF360C, 0xFFFFF59D, "VCA Stadium, Nagpur")
    )
    private val ZONE_IDS = mapOf("North" to "Z_N", "South" to "Z_S", "East" to "Z_E", "West" to "Z_W", "Central" to "Z_C", "North East" to "Z_NE")
    const val ROI = "D_ROI"

    /** Competitions played as 4-day first-class games (Irani Cup and knockout finals are 5 days). */
    fun fourDay(comp: Competition, fx: Fixture) = (comp.key == "RANJI" || comp.key == "DULEEP") && fx.stage != "Final"

    fun marketKind(save: CareerSave) = if (save.mode == IPL) "FRANCHISE" else "CLUB"
    fun hasAuction(save: CareerSave) = save.mode == CLUB || save.mode == IPL
    fun hasTransfers(save: CareerSave) = save.mode == CLUB || save.mode == IPL
    fun label(mode: String) = when (mode) { INTL -> "International"; DOMESTIC -> "Domestic"; IPL -> "IPL"; else -> "Club" }

    fun teamsFor(mode: String): List<Triple<String, String, String>> = when (mode) {
        INTL -> Consts.NATIONS.map { Triple(it.id, it.name, it.short) }
        DOMESTIC -> STATES.map { Triple(it.id, it.name, it.short) }
        IPL -> Consts.IPL.map { Triple(it.id, it.name, it.short) }
        else -> emptyList()
    }

    fun stateOf(id: String) = STATES.firstOrNull { it.id == id }

    /* ======================= new career ======================= */
    fun newCareer(slot: Int, manager: String, mode: String, teamId: String, economy: Double, difficulty: Int, rnd: Random = Random.Default): CareerSave {
        val save = CareerSave(slot = slot, manager = manager, userClubId = teamId, economy = economy, difficulty = difficulty,
            created = System.currentTimeMillis(), year = Career.START_YEAR, mode = mode)
        Career.buildPlayers(save, rnd)
        when (mode) {
            INTL -> buildNations(save)
            DOMESTIC -> buildDomestic(save, rnd)
            IPL -> buildIpl(save, rnd)
            else -> error("unknown mode $mode")
        }
        val u = save.clubs[teamId] ?: error("unknown team $teamId")
        u.isUser = true
        u.balance = when (mode) { IPL -> 40.0; INTL -> 30.0; else -> 8.0 } * economy
        u.reputation = when (mode) { INTL -> 70; IPL -> 65; else -> 45 }
        TeamSelector.autoPick(u, save.players)
        createSeason(save)
        save.sponsorOffers = Career.genSponsors(save, rnd)
        save.followers = when (mode) { INTL -> 250_000; IPL -> 400_000; else -> 30_000 } + (u.reputation * 1000)
        save.addNews("Welcome, $manager! You are the new head coach of ${u.name}.")
        save.addNews("Dataset: real players from Cricsheet + Wikipedia (as of ${Repo.ds.meta.built}). Fictional players are always marked FICTIONAL.")
        Social.post(save, Social.OFFICIAL, "${u.name} appoint $manager as head coach. A new era begins! #${u.short}", "NEWS", 900 + rnd.nextInt(3000), rnd)
        return save
    }

    private fun buildNations(save: CareerSave) {
        for (n in Consts.NATIONS) save.clubs.getOrPut(n.id) {
            Club(id = n.id, name = n.name, short = n.short, primary = n.primary, secondary = n.secondary, kind = "NATION", country = n.country,
                stadium = NATION_VENUES[n.id] ?: n.name, division = 1)
        }
        refreshNations(save, includeUser = true)
    }

    val NATION_VENUES = mapOf(
        "N_IND" to "Eden Gardens, Kolkata", "N_AUS" to "Melbourne Cricket Ground", "N_ENG" to "Lord's, London",
        "N_PAK" to "Gaddafi Stadium, Lahore", "N_SA" to "Newlands, Cape Town", "N_NZ" to "Basin Reserve, Wellington",
        "N_BAN" to "Shere Bangla Stadium, Dhaka", "N_SL" to "R. Premadasa Stadium, Colombo", "N_AFG" to "Sharjah Cricket Stadium",
        "N_WI" to "Kensington Oval, Barbados", "N_IRE" to "Malahide, Dublin", "N_ZIM" to "Harare Sports Club"
    )

    /** National selection: best available players of that country (AI). The user's squad is only cleaned (retired/academy removed). */
    fun refreshNations(save: CareerSave, includeUser: Boolean = false) {
        for (n in Consts.NATIONS) {
            val c = save.clubs[n.id] ?: continue
            if (c.isUser && !includeUser) {
                c.squad.removeAll { save.players[it]?.retired != false }
                if (c.squad.size < 15) for (p in nationPool(save, c).filter { it.id !in c.squad }) { if (c.squad.size >= 15) break; c.squad.add(p.id) }
                continue
            }
            val pool = nationPool(save, c)
            val pick = LinkedHashSet<String>()
            pool.filter { it.role == "WK" }.take(2).forEach { pick.add(it.id) }
            pool.filter { it.role == "BOWL" || it.role == "AR" }.sortedByDescending { it.bowl }.take(7).forEach { pick.add(it.id) }
            for (p in pool) { if (pick.size >= 17) break; pick.add(p.id) }
            c.squad = pick.toMutableList(); c.xi.clear(); c.bowlers.clear()
            TeamSelector.autoPick(c, save.players)
        }
    }

    fun nationPool(save: CareerSave, c: Club) = save.players.values.filter { it.country == c.country && !it.retired && (!it.academy || it.clubId == c.id) }.sortedByDescending { it.overall() }

    private fun buildDomestic(save: CareerSave, rnd: Random) {
        for (st in STATES) save.clubs[st.id] = Club(id = st.id, name = st.name, short = st.short, primary = st.primary, secondary = st.secondary,
            kind = "DOMESTIC", country = "India", stadium = st.venue, division = 2, capacity = 25000)
        // real players at their real current state side (Wikipedia infobox), everyone else stays unattached
        for ((pid, state) in Repo.domestic) {
            val p = save.players[pid] ?: continue
            if (p.retired || p.country != "India") continue
            val st = STATES.firstOrNull { it.name.equals(state, true) } ?: continue
            val c = save.clubs[st.id]!!
            p.clubId = c.id; c.squad.add(pid)
        }
        // top up each side with clearly-labelled fictional domestic players
        for (st in STATES) fillDomestic(save, save.clubs[st.id]!!, 16, rnd)
        // North East zone has no Elite side in this game -> fictional NE players only
        for ((zone, id) in ZONE_IDS) save.clubs[id] = Club(id = id, name = "$zone Zone", short = id.removePrefix("Z_") + "Z", kind = "DOMESTIC", country = "India",
            primary = 0xFF1A237E, secondary = 0xFFE9B949, stadium = "BCCI Centre of Excellence, Bengaluru", division = 2)
        val ne = save.clubs["Z_NE"]!!
        repeat(16) { ne.squad.add(Career.makeFictional(save, "India", rnd.nextInt(20, 31), rnd.nextInt(46, 58), rnd.nextInt(55, 72), rnd).also { it.clubId = ne.id }.id) }
        save.clubs[ROI] = Club(id = ROI, name = "Rest of India", short = "ROI", kind = "DOMESTIC", country = "India", primary = 0xFF0D47A1, secondary = 0xFFFFFFFF,
            stadium = "Ekana Cricket Stadium, Lucknow", division = 1)
        for (c in save.clubs.values) if (c.kind == "DOMESTIC") TeamSelector.autoPick(c, save.players)
    }

    fun fillDomestic(save: CareerSave, c: Club, target: Int, rnd: Random) {
        val need = mutableListOf<String>()
        val roles = c.squad.mapNotNull { save.players[it]?.role }
        if (roles.count { it == "WK" } < 2) need.add("WK")
        while (c.squad.size + need.size < target) need.add("")
        for (r in need) {
            val p = Career.makeFictional(save, "India", rnd.nextInt(19, 33), rnd.nextInt(50, 64), rnd.nextInt(58, 80), rnd)
            if (r == "WK" && p.role != "WK") { p.role = "WK"; p.keep = rnd.nextInt(62, 80); p.bowl = rnd.nextInt(15, 30) }
            p.clubId = c.id; p.salary = 0.1 * save.economy; c.squad.add(p.id)
            p.history.add("Season ${save.season}: Fictional domestic player, ${c.name}")
        }
    }

    private fun buildIpl(save: CareerSave, rnd: Random) {
        val latest = Repo.ds.players.mapNotNull { it.iplLast?.take(4) }.maxOrNull() ?: "2026"
        for (f in Consts.IPL) {
            val c = Club(id = f.id, name = f.name, short = f.short, primary = f.primary, secondary = f.secondary, kind = "FRANCHISE", country = "India",
                stadium = Tournaments.venueOf(f.id) ?: f.name, division = 1, capacity = 40000, ticketPrice = 1500)
            save.clubs[f.id] = c
            val roster = Repo.ds.players.filter { it.ipl == f.name && it.iplLast?.startsWith(latest) == true }
                .mapNotNull { save.players[it.id] }.filter { !it.retired && it.clubId.isEmpty() }.sortedByDescending { it.overall() }.take(25)
            for (p in roster) { p.clubId = c.id; c.squad.add(p.id); p.contractYears = rnd.nextInt(1, 4) }
            c.trainingLvl = 3; c.academyLvl = 2; c.medicalLvl = 3; c.coachBat = 3; c.coachBowl = 3; c.coachFit = 3; c.reputation = 75
            c.sponsor = Consts.SPONSORS.random(rnd); c.sponsorValue = Money.tvFor(1, save.economy) * 0.25; c.balance = 40.0 * save.economy
        }
        for (c in save.clubs.values) {
            var g = 0
            while (c.squad.size < 18 && g++ < 30) {
                val p = save.players.values.filter { it.clubId.isEmpty() && !it.retired && !it.academy && (it.country == "India" || Career.overseasCount(save, c) < 8) }
                    .maxByOrNull { it.overall() } ?: break
                p.clubId = c.id; c.squad.add(p.id)
            }
        }
    }

    /* ======================= season structure ======================= */
    fun createSeason(save: CareerSave, rnd: Random = Random.Default) {
        save.league.clear()
        val u = save.userClubId
        fun add(c: Competition, startDay: Int, double: Boolean = false) {
            if (c.fixtures.isEmpty()) CompEngine.addLeagueFixtures(c, double, startDay)
            if (u in c.teams || c.groups.any { u in it }) c.userTeam = u
            save.league.add(c)
        }
        val s = save.season
        when (save.mode) {
            INTL -> {
                refreshNations(save)
                val others = Consts.NATIONS.map { it.id }.filter { it != u }.shuffled(rnd)
                val tests = series(save, "INTL_TEST", "Test series", "TEST", u, others[0], 3, home = true, startDay = 1)
                val odis = series(save, "INTL_ODI", "ODI series", "ODI", others[1], u, 3, home = false, startDay = 61)
                val t20s = series(save, "INTL_T20", "T20I series", "T20", u, others[2], 3, home = true, startDay = 121)
                for (c in listOf(tests, odis, t20s)) { c.userTeam = u; save.league.add(c) }
                val ev = iccEventFor(save.year)
                val ec = Competition(id = "S${s}ICC", key = ev, kind = "NATION", season = s)
                when (ev) {
                    "ODI_WC" -> { ec.name = "ICC Cricket World Cup ${save.year}"; ec.format = "ODI"; ec.teams = withUser(listOf("IND", "AUS", "ENG", "PAK", "SA", "NZ", "BAN", "SL", "AFG", "WI").map { "N_$it" }, u, save); ec.knockout = "SEMIS" }
                    "CT" -> {
                        ec.name = "ICC Champions Trophy ${save.year}"; ec.format = "ODI"
                        val t = withUser(listOf("IND", "AUS", "ENG", "PAK", "SA", "NZ", "BAN", "AFG").map { "N_$it" }, u, save)
                        ec.teams = t.toMutableList(); ec.groups = mutableListOf(t.filterIndexed { i, _ -> i % 2 == 0 }.toMutableList(), t.filterIndexed { i, _ -> i % 2 == 1 }.toMutableList()); ec.knockout = "SEMIS"
                    }
                    else -> {
                        ec.name = "ICC T20 World Cup ${save.year}"; ec.format = "T20"
                        ec.teams = Consts.NATIONS.map { it.id }.toMutableList()
                        val seeded = ec.teams.sortedByDescending { Career.strength(save, save.clubs[it]!!) }
                        val a = mutableListOf<String>(); val b = mutableListOf<String>()
                        seeded.forEachIndexed { i, id -> if (i % 4 == 0 || i % 4 == 3) a.add(id) else b.add(id) }
                        ec.groups = mutableListOf(a, b); ec.knockout = "SEMIS"
                    }
                }
                add(ec, 181)
            }
            DOMESTIC -> {
                refreshDomestic(save, rnd)
                // 1. Duleep Trophy (zonal, first-class, 2025 knockout format)
                val dul = Competition(id = "S${s}DUL", name = "Duleep Trophy ${save.year}", key = "DULEEP", format = "TEST", kind = "DOMESTIC", season = s, knockout = "DULEEP")
                dul.teams = ZONE_IDS.values.toMutableList()
                dul.fixtures.add(Fixture("${dul.id}-${dul.nextFixtureId++}", "Z_N", "Z_E", "Quarter-final 1", 0, 1))
                dul.fixtures.add(Fixture("${dul.id}-${dul.nextFixtureId++}", "Z_C", "Z_NE", "Quarter-final 2", 0, 1))
                pickZones(save); save.league.add(dul)
                // 2-4. Ranji (4-day), SMAT (T20), Vijay Hazare (50 overs): 32 teams, 4 groups of 8, QF/SF/Final
                fun groups(c: Competition) {
                    val seeded = STATES.map { it.id }.sortedByDescending { Career.strength(save, save.clubs[it]!!) }
                    val g = List(4) { mutableListOf<String>() }
                    seeded.forEachIndexed { i, id -> val r = i / 4; g[if (r % 2 == 0) i % 4 else 3 - i % 4].add(id) }
                    c.teams = seeded.toMutableList(); c.groups = g.toMutableList()
                }
                val ranji = Competition(id = "S${s}RAN", name = "Ranji Trophy ${save.year}-${(save.year + 1) % 100}", key = "RANJI", format = "TEST", kind = "DOMESTIC", season = s,
                    knockout = "QF", pointsWin = 6, pointsDraw = 1, pointsNR = 1)
                groups(ranji); add(ranji, 21)
                val smat = Competition(id = "S${s}SMA", name = "Syed Mushtaq Ali Trophy ${save.year}", key = "SMAT", format = "T20", kind = "DOMESTIC", season = s, knockout = "QF", pointsWin = 4, pointsDraw = 2, pointsNR = 2)
                groups(smat); add(smat, 61)
                val vh = Competition(id = "S${s}VHT", name = "Vijay Hazare Trophy ${save.year}", key = "VHT", format = "ODI", kind = "DOMESTIC", season = s, knockout = "QF", pointsWin = 4, pointsDraw = 2, pointsNR = 2)
                groups(vh); add(vh, 101)
                // 5. Irani Cup: Ranji champions v Rest of India (fixture added when the Ranji final is decided)
                val ir = Competition(id = "S${s}IRA", name = "Irani Cup ${save.year + 1}", key = "IRANI", format = "TEST", kind = "DOMESTIC", season = s, knockout = "NONE", series = true, pointsWin = 1, pointsDraw = 0)
                ir.teams = mutableListOf(ROI)
                save.league.add(ir)
            }
            IPL -> {
                for (c in save.clubs.values.filter { it.kind == "FRANCHISE" && !it.isUser }) { Career.fillAiSquad(save, c); TeamSelector.autoPick(c, save.players) }
                val ipl = Competition(id = "S${s}IPL", name = "Indian Premier League ${save.year}", key = "IPL", format = "T20", kind = "FRANCHISE", season = s, knockout = "IPL", allowImpact = true)
                ipl.teams = Consts.IPL.map { it.id }.toMutableList()
                iplSchedule(ipl, rnd)
                add(ipl, 1)
            }
        }
    }

    private fun withUser(list: List<String>, u: String, save: CareerSave): MutableList<String> {
        if (u in list) return list.toMutableList()
        val weakest = list.minByOrNull { Career.strength(save, save.clubs[it]!!) }!!
        save.addNews("${save.clubs[u]?.name} have qualified for the ICC event this year.")
        return list.map { if (it == weakest) u else it }.toMutableList()
    }

    /** Real ICC calendar: 2027 ODI WC, 2028 T20 WC, 2029 Champions Trophy, 2030 T20 WC, then repeating. */
    fun iccEventFor(year: Int) = listOf("ODI_WC", "T20_WC", "CT", "T20_WC")[((year - 2027) % 4 + 4) % 4]

    @Suppress("UNUSED_PARAMETER")
    private fun series(save: CareerSave, key: String, label: String, fmt: String, host: String, guest: String, n: Int, home: Boolean, startDay: Int): Competition =
        seriesImpl(save, key, label, fmt, host, guest, n, startDay)

    private fun seriesImpl(save: CareerSave, key: String, label: String, fmt: String, host: String, guest: String, n: Int, startDay: Int): Competition {
        val h = save.clubs[host]!!; val a = save.clubs[guest]!!
        val c = Competition(id = "S${save.season}$key", key = key, format = fmt, kind = "NATION", season = save.season, series = true, knockout = "NONE")
        c.name = "${h.name} v ${a.name} - $label ${save.year} (in ${h.country})"
        c.teams = mutableListOf(host, guest)
        if (fmt == "TEST") { c.pointsWin = 12; c.pointsDraw = 4 }
        for (i in 0 until n) c.fixtures.add(Fixture("${c.id}-${c.nextFixtureId++}", host, guest, "Match ${i + 1}", 0, startDay + i))
        return c
    }

    /** Real IPL format since 2022: 2 groups of 5; play own group twice, the other group once, plus one 'row partner' twice = 14 games, 70 league matches. */
    fun iplSchedule(c: Competition, rnd: Random) {
        val t = c.teams.shuffled(rnd)
        val a = t.take(5); val b = t.drop(5)
        val pairs = mutableListOf<Pair<String, String>>()
        for (g in listOf(a, b)) for (i in g.indices) for (j in i + 1 until g.size) { pairs.add(g[i] to g[j]); pairs.add(g[j] to g[i]) }
        for (i in a.indices) for (j in b.indices) pairs.add(if ((i + j) % 2 == 0) a[i] to b[j] else b[j] to a[i])
        for (i in a.indices) pairs.add(if (i % 2 == 0) b[i] to a[i] else a[i] to b[i])
        pairs.shuffle(rnd)
        val busy = HashMap<Int, MutableSet<String>>()
        for ((h, w) in pairs) {
            var d = 1
            while (true) { val s = busy.getOrPut(d) { mutableSetOf() }; if (h !in s && w !in s && s.size < 4) { s.add(h); s.add(w); break }; d++ }
            c.fixtures.add(Fixture("${c.id}-${c.nextFixtureId++}", h, w, "League", 0, d))
        }
    }

    /** Zonal squads for the Duleep Trophy: best 16 available players of each zone's state sides. */
    fun pickZones(save: CareerSave) {
        for ((zone, id) in ZONE_IDS) {
            if (zone == "North East") continue
            val z = save.clubs[id] ?: continue
            val pool = STATES.filter { it.zone == zone }.flatMap { save.clubs[it.id]?.squad ?: emptyList() }.mapNotNull { save.players[it] }
                .filter { !it.retired && !it.academy }.sortedByDescending { it.overall() }
            val pick = LinkedHashSet<String>()
            pool.filter { it.role == "WK" }.take(2).forEach { pick.add(it.id) }
            pool.filter { it.role == "BOWL" || it.role == "AR" }.take(7).forEach { pick.add(it.id) }
            for (p in pool) { if (pick.size >= 16) break; pick.add(p.id) }
            z.squad = pick.toMutableList(); z.xi.clear(); z.bowlers.clear()
            TeamSelector.autoPick(z, save.players)
        }
    }

    fun refreshDomestic(save: CareerSave, rnd: Random) {
        for (st in STATES) {
            val c = save.clubs[st.id] ?: continue
            c.squad.removeAll { save.players[it]?.retired != false }
            if (!c.isUser) { if (c.squad.size < 16) fillDomestic(save, c, 16, rnd); c.xi.clear(); c.bowlers.clear(); TeamSelector.autoPick(c, save.players) }
        }
    }

    /** Called whenever a competition finishes. */
    fun onCompDone(save: CareerSave, comp: Competition) {
        if (comp.key == "RANJI") {
            val champ = comp.champion ?: return
            val ir = save.league.firstOrNull { it.key == "IRANI" && it.season == comp.season } ?: return
            val roi = save.clubs[ROI] ?: return
            val champSquad = save.clubs[champ]?.squad?.toSet() ?: emptySet()
            val pool = save.players.values.filter { it.country == "India" && !it.retired && !it.academy && it.id !in champSquad && save.clubs[it.clubId]?.kind == "DOMESTIC" }
                .sortedByDescending { it.overall() }
            val pick = LinkedHashSet<String>()
            pool.filter { it.role == "WK" }.take(2).forEach { pick.add(it.id) }
            for (p in pool) { if (pick.size >= 16) break; pick.add(p.id) }
            roi.squad = pick.toMutableList(); roi.xi.clear(); roi.bowlers.clear(); TeamSelector.autoPick(roi, save.players)
            ir.teams = mutableListOf(champ, ROI)
            if (champ == save.userClubId) ir.userTeam = champ
            val day = (save.league.flatMap { it.fixtures }.maxOfOrNull { it.day } ?: 150).coerceAtLeast(150) + 10
            ir.fixtures.add(Fixture("${ir.id}-${ir.nextFixtureId++}", champ, ROI, "Match 1", 0, day))
            save.addNews("Irani Cup set: Ranji champions ${save.clubs[champ]?.name} v Rest of India.")
        }
    }

    /** Mode-specific part of the end-of-season review (club careers use promotion/relegation instead). */
    fun endSeason(save: CareerSave, sum: MutableList<String>) {
        val u = save.userClub(); val e = save.economy
        for (comp in save.league) {
            if (comp.fixtures.isEmpty()) continue
            val a = save.awards.firstOrNull { it.season == save.season && it.comp == comp.name } ?: Stats.award(save, comp)
            val champ = comp.champion?.let { save.clubs[it]?.name } ?: a.champion.ifBlank { "-" }
            sum.add("${comp.name}: ${if (comp.series) "Series result - " else "Champions "}$champ. Top run-scorer ${save.players[a.orange]?.name ?: "-"} (${a.orangeRuns}), top wicket-taker ${save.players[a.purple]?.name ?: "-"} (${a.purpleWk}).")
            if (comp.userTeam == u.id) {
                if (comp.champion == u.id) {
                    u.trophies.add("${comp.name}"); u.reputation = min(100, u.reputation + 8); u.balance += (if (save.mode == IPL) 20.0 else 5.0) * e
                    sum.add(0, "🏆 ${u.name} won the ${comp.name}!")
                } else if (!comp.series) {
                    val t = if (comp.groups.size > 1) comp.groups.indices.flatMap { CompEngine.table(comp, it) } else CompEngine.table(comp)
                    val pos = t.indexOfFirst { it.team == u.id }
                    if (pos >= 0) sum.add("${u.name}: ${if (comp.groups.size > 1) "group stage" else "league"} position ${pos % (comp.groups.firstOrNull()?.size ?: 99) + 1}${if (comp.runnerUp == u.id) " - runners-up" else ""} in the ${comp.name}.")
                }
            }
        }
        val wins = save.league.flatMap { it.fixtures }.count { it.played && it.res?.winner == u.id }
        val played = save.league.flatMap { it.fixtures }.count { it.played && (it.home == u.id || it.away == u.id) }
        sum.add(0, "${u.name}: $wins wins from $played matches this season.")
        save.boardRating = (save.boardRating + (if (played == 0) 0 else (wins * 100 / played - 45) / 3)).coerceIn(0, 100)
        if (save.boardRating < 15) sum.add(0, "⚠ The board is unhappy with results (confidence ${save.boardRating}%).")
    }

    fun beginNextSeason(save: CareerSave, rnd: Random = Random.Default) {
        save.season++
        save.day = 0
        maintainSquads(save, rnd)
        createSeason(save, rnd)
        save.phase = "PRESEASON"
        save.auction = null
        if (save.mode == IPL) {
            if (save.season % 3 == 1) { save.auction = Auction.start(save, "MEGA"); save.addNews("IPL MEGA AUCTION year! Retain up to 4 players.") }
            else save.addNews("Pre-season: the IPL mini auction is open in the Auction room.")
        } else save.addNews("Season ${save.season} (${save.year}): review your squad and selection before the first match.")
    }

    /** Keeps every national / state squad playable after retirements (min 16): real unattached players first, then labelled fictional ones. */
    fun maintainSquads(save: CareerSave, rnd: Random = Random.Default) {
        val target = 16
        when (save.mode) {
            INTL -> for (c in save.clubs.values.filter { it.kind == "NATION" }) {
                val added = mutableListOf<String>()
                if (c.squad.size >= target) continue
                val taken = save.clubs.values.filter { it.kind == "NATION" }.flatMap { it.squad }.toSet()
                for (p in nationPool(save, c).filter { it.id !in taken && !it.academy }) {
                    if (c.squad.size >= target) break
                    c.squad.add(p.id); added.add(p.name)
                }
                if (c.isUser && added.isNotEmpty()) save.addNews("Selectors called up ${added.joinToString()} to replace retired players.")
            }
            DOMESTIC -> for (st in STATES) {
                val c = save.clubs[st.id] ?: continue
                if (c.squad.size >= target) continue
                val before = c.squad.size
                val pool = save.players.values.filter { it.clubId.isEmpty() && !it.retired && !it.academy && it.country == "India" && Repo.domestic[it.id]?.equals(c.name, true) == true }
                for (p in pool.sortedByDescending { it.overall() }) { if (c.squad.size >= target) break; p.clubId = c.id; c.squad.add(p.id) }
                fillDomestic(save, c, target, rnd)
                if (c.isUser) save.addNews("${c.name} squad topped up with ${c.squad.size - before} new player(s) for the new season.")
            }
            else -> {}
        }
    }

    /** Players the user may add to their squad (international call-ups / domestic signings). */
    fun selectionPool(save: CareerSave): List<GPlayer> {
        val u = save.userClub()
        return when (save.mode) {
            INTL -> nationPool(save, u).filter { it.id !in u.squad }
            DOMESTIC -> save.players.values.filter { it.country == "India" && !it.retired && !it.academy && it.id !in u.squad &&
                (it.clubId.isEmpty() || save.clubs[it.clubId]?.kind == "DOMESTIC" && !it.clubId.startsWith("Z_") && it.clubId != ROI) }.sortedByDescending { it.overall() }
            else -> emptyList()
        }
    }

    fun maxSquad(save: CareerSave) = 25   // v2.5 Phase E: tournament squads are 18-25 (national / state pools may hold 25)

    /** Adds a player to the user's national/state squad. Domestic: unattached players join freely, players of other states count as 'guest professionals' (max 3). */
    fun callUp(save: CareerSave, pid: String): String {
        val u = save.userClub(); val p = save.players[pid] ?: return "Unknown player"
        if (u.squad.size >= maxSquad(save)) return "Squad is full (${maxSquad(save)} max). Release someone first."
        if (pid in u.squad) return "${p.name} is already in the squad."
        if (save.mode == DOMESTIC) {
            if (p.clubId.isNotEmpty() && p.clubId != u.id) {
                if (save.guestPros >= 3) return "You already used all 3 guest-professional signings."
                save.clubs[p.clubId]?.let { o -> o.squad.remove(pid); o.xi.remove(pid); o.bowlers.remove(pid); if (o.squad.size < 15) fillDomestic(save, o, 15, Random.Default) }
                save.guestPros++
                Social.post(save, Social.OFFICIAL, "TRANSFER: ${p.name} joins ${u.name} as a guest professional. #${u.short}", "TRANSFER", 300 + p.overall() * 20, Random.Default)
            }
            p.clubId = u.id
            p.history.add("Season ${save.season}: Joined ${u.name}")
        } else Social.post(save, Social.OFFICIAL, "SQUAD: ${p.name} receives a call-up to the ${u.name} squad! #${u.short}", "TRANSFER", 500 + p.overall() * 30, Random.Default)
        u.squad.add(pid)
        save.addNews("${p.name} added to the ${u.name} squad.")
        return "${p.name} added to the squad."
    }

    fun drop(save: CareerSave, pid: String): String {
        val u = save.userClub(); val p = save.players[pid] ?: return "Unknown player"
        if (u.squad.size <= 13) return "A squad needs at least 13 players."
        u.squad.remove(pid); u.xi.remove(pid); u.bowlers.remove(pid)
        if (u.captain == pid) u.captain = ""; if (u.vice == pid) u.vice = ""; if (u.keeper == pid) u.keeper = ""
        if (save.mode == DOMESTIC && p.clubId == u.id) p.clubId = ""
        save.addNews("${p.name} left out of the ${u.name} squad.")
        return "${p.name} released from the squad."
    }

    @Suppress("unused") private fun clamp(v: Int) = max(0, v).let { min(100, it) }
    @Suppress("unused") private fun r2(v: Double) = (v * 100).roundToInt() / 100.0
}
