package com.cricketcoach.manager.game

import com.cricketcoach.manager.data.Repo
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

object Career {
    const val START_YEAR = 2027
    const val LEAGUE_DAYS = 14
    const val USER_ID = "C_USER"
    const val RETIRE_CUTOFF = "2025-01-01"

    /* ---------------- players ---------------- */
    fun buildPlayers(save: CareerSave, rnd: Random) {
        for (rp in Repo.ds.players) {
            val a = Repo.attrs[rp.id] ?: continue
            var by = rp.dob?.take(4)?.toIntOrNull()
            var est = false
            if (by == null) {
                val first = rp.stats.values.mapNotNull { it.first?.take(4)?.toIntOrNull() }.minOrNull() ?: 2020
                by = first - 22; est = true
            }
            val age = START_YEAR - by
            val ov = com.cricketcoach.manager.data.Ratings.overall(rp.role, a.tech, a.power, a.bowl)
            val pot = when {
                age <= 21 -> ov + rnd.nextInt(6, 19)
                age <= 24 -> ov + rnd.nextInt(3, 11)
                age <= 27 -> ov + rnd.nextInt(0, 5)
                else -> ov
            }.coerceIn(ov, 97)
            // starting form from REAL recent performances
            val batForm = if (rp.form.isNotEmpty()) 50 + (rp.form.map { it.r }.average() - 20) * 0.8 else 58.0
            val bowlForm = if (rp.bform.isNotEmpty()) 50 + rp.bform.map { it.w }.average() * 10 else 58.0
            val form = when (rp.role) { "BOWL" -> bowlForm; "AR" -> (batForm + bowlForm) / 2; else -> batForm }.roundToInt().coerceIn(35, 90)
            save.players[rp.id] = GPlayer(
                id = rp.id, name = rp.name, country = rp.country.ifBlank { "India" }, role = rp.role,
                bowlType = a.bowlType, birthYear = by, ageEst = est, tech = a.tech, power = a.power, bowl = a.bowl,
                field = a.field, keep = a.keep, potential = pot, form = form, morale = 65 + rnd.nextInt(15),
                salary = Money.value(ov, age, save.economy), contractYears = rnd.nextInt(1, 4), real = true
            )
            // Real-world inactivity: no international or IPL appearance since RETIRE_CUTOFF -> retired in career mode
            val lastPlayed = (rp.stats.values.mapNotNull { it.last } + listOfNotNull(rp.iplLast)).maxOrNull()
            if (lastPlayed != null && lastPlayed < RETIRE_CUTOFF) {
                save.players[rp.id]!!.apply { retired = true; history.add("Inactive since $lastPlayed (last recorded international/IPL match) — retired in career mode") }
            }
        }
    }

    fun makeFictional(save: CareerSave, country: String, age: Int, ovTarget: Int, pot: Int, rnd: Random): GPlayer {
        val (fn, ln) = Consts.namePool(country)
        val roleRoll = rnd.nextDouble()
        val role = when { roleRoll < 0.35 -> "BAT"; roleRoll < 0.70 -> "BOWL"; roleRoll < 0.88 -> "AR"; else -> "WK" }
        val b = (ovTarget - 44) / 0.98 + 40
        fun j() = rnd.nextInt(-3, 4)
        var tech = 30; var power = 30; var bowl = 25
        when (role) {
            "BAT" -> { tech = (b - 7).roundToInt() + j(); power = (b - 7).roundToInt() + j(); bowl = rnd.nextInt(20, 40) }
            "WK" -> { tech = (b - 9).roundToInt() + j(); power = (b - 9).roundToInt() + j(); bowl = rnd.nextInt(15, 30) }
            "BOWL" -> { bowl = b.roundToInt() + j(); tech = rnd.nextInt(25, 42); power = rnd.nextInt(25, 42) }
            else -> { bowl = (b - 4).roundToInt() + j(); tech = (b - 6).roundToInt() + j(); power = (b - 6).roundToInt() + j() }
        }
        val id = "Y${save.nextId++}"
        val p = GPlayer(
            id = id, name = "${fn.random(rnd)} ${ln.random(rnd)}", country = country, role = role,
            bowlType = if (rnd.nextBoolean()) "PACE" else "SPIN", birthYear = save.year - age,
            tech = tech.coerceIn(20, 95), power = power.coerceIn(20, 95), bowl = bowl.coerceIn(15, 95),
            field = rnd.nextInt(50, 80), keep = if (role == "WK") rnd.nextInt(60, 80) else 30,
            potential = pot.coerceIn(ovTarget, 97), fictional = true, real = false, form = 60, morale = 70,
            salary = 0.2 * save.economy, contractYears = 3
        )
        p.history.add("Season ${save.season}: Entered the game as a fictional prospect")
        save.players[id] = p
        return p
    }

    /* ---------------- new career ---------------- */
    fun newCareer(
        slot: Int, manager: String, clubName: String, short: String, country: String, primary: Long, secondary: Long,
        logo: Int, jersey: Int, setup: String, economy: Double, difficulty: Int, rnd: Random = Random.Default
    ): CareerSave {
        val save = CareerSave(slot = slot, manager = manager, userClubId = USER_ID, economy = economy, difficulty = difficulty,
            created = System.currentTimeMillis(), year = START_YEAR)
        buildPlayers(save, rnd)
        // clubs
        val clubs = mutableListOf<Club>()
        var userPlaced = false
        var colorIdx = 0
        for ((c, list) in Consts.CLUBS) {
            for ((i, nm) in list.withIndex()) {
                val (pc, sc) = Consts.CLUB_COLORS[colorIdx++ % Consts.CLUB_COLORS.size]
                if (!userPlaced && c == country && i == list.size - 1) {
                    clubs.add(Club(id = USER_ID, name = clubName, short = short.uppercase().take(4), primary = primary, secondary = secondary,
                        logo = logo, jersey = jersey, country = country, isUser = true, stadium = "$clubName Ground"))
                    userPlaced = true
                } else {
                    clubs.add(Club(id = "C_${nm.second}", name = nm.first, short = nm.second, primary = pc, secondary = sc,
                        logo = rnd.nextInt(Consts.LOGO_NAMES.size), jersey = rnd.nextInt(Consts.JERSEY_NAMES.size), country = c,
                        stadium = nm.first.substringBeforeLast(" ") + " " + listOf("Oval", "Park", "Cricket Ground", "Stadium").random(rnd)))
                }
            }
        }
        for (c in clubs) save.clubs[c.id] = c
        allocate(save, clubs, rnd)
        // divisions by strength (user forced into Division 4)
        val ai = clubs.filter { !it.isUser }.sortedByDescending { strength(save, it) }
        ai.forEachIndexed { i, c -> c.division = min(4, i / 8 + 1) }
        save.userClub().division = 4
        for (c in clubs) setupClubEconomy(save, c)
        val u = save.userClub()
        u.balance = 25.0 * economy; u.reputation = 25
        for (c in clubs) TeamSelector.autoPick(c, save.players)
        createLeague(save)
        save.sponsorOffers = genSponsors(save, rnd)
        save.addNews("Welcome, ${manager}! You take charge of ${clubName} in Division 4.")
        save.addNews("Dataset: real players from Cricsheet + Wikipedia (as of ${Repo.ds.meta.built}). Ratings are game-derived.")
        if (setup == "MEGA") {
            save.auction = Auction.start(save, "MEGA")
            save.addNews("Mega auction pending: retain up to 4 players, then build your squad.")
        }
        return save
    }

    private fun needs(save: CareerSave, c: Club): Map<String, Int> {
        val ps = c.squad.mapNotNull { save.players[it] }
        return mapOf(
            "WK" to max(0, 2 - ps.count { it.role == "WK" }),
            "BOWL" to max(0, 7 - ps.count { it.role == "BOWL" || it.role == "AR" }),
            "BAT" to max(0, 5 - ps.count { it.role == "BAT" })
        )
    }

    private fun fits(save: CareerSave, c: Club, p: GPlayer, slotsLeft: Int, maxOverseas: Int): Boolean {
        if (TeamSelector.isOverseas(p, c) && c.squad.count { save.players[it]?.let { q -> TeamSelector.isOverseas(q, c) } == true } >= maxOverseas) return false
        val n = needs(save, c)
        val deficit = n.values.sum()
        if (slotsLeft <= deficit) {
            val needed = n.filter { it.value > 0 }.keys
            val r = if (p.role == "AR") "BOWL" else p.role
            return r in needed || (p.role == "AR" && "BOWL" in needed)
        }
        return true
    }

    private fun sign(save: CareerSave, c: Club, p: GPlayer) {
        p.clubId = c.id; c.squad.add(p.id)
        p.history.add("Season ${save.season}: Joined ${c.name}")
    }

    fun allocate(save: CareerSave, clubs: List<Club>, rnd: Random) {
        val pool = save.players.values.filter { !it.retired && it.age(save.year) <= 40 }.sortedByDescending { it.overall() }.toMutableList()
        val taken = HashSet<String>()
        // Phase 1: domestic core (AI clubs first, user club takes later = weaker start)
        for ((country, _) in Consts.CLUBS) {
            val cc = clubs.filter { it.country == country }
            val aiC = cc.filter { !it.isUser }.shuffled(rnd)
            val dom = pool.filter { it.country == country }
            val target = if (cc.size == 1) 12 else 9
            var round = 0
            while (round < target) {
                val order = if (round % 2 == 0) aiC else aiC.reversed()
                for (c in order) {
                    val p = dom.firstOrNull { it.id !in taken && fits(save, c, it, 18 - c.squad.size, 8) } ?: continue
                    taken.add(p.id); sign(save, c, p)
                }
                round++
            }
            cc.firstOrNull { it.isUser }?.let { u ->
                // user gets domestic players from what remains, alternating with the free-agent pool
                val rest = dom.filter { it.id !in taken }
                var alt = false
                for (p in rest.drop(min(6, rest.size / 10))) {
                    if (u.squad.size >= 9) break
                    if (alt) { alt = false; continue }
                    if (fits(save, u, p, 18 - u.squad.size, 8)) { taken.add(p.id); sign(save, u, p); alt = true }
                }
            }
        }
        // Phase 2: fill AI squads to 18 from global pool (snake by weakest first)
        val ai = clubs.filter { !it.isUser }
        var guard = 0
        while (ai.any { it.squad.size < 18 } && guard++ < 40) {
            val order = ai.filter { it.squad.size < 18 }.sortedBy { strength(save, it) }
            for (c in order) {
                val p = pool.firstOrNull { it.id !in taken && fits(save, c, it, 18 - c.squad.size, 8) } ?: continue
                taken.add(p.id); sign(save, c, p)
            }
        }
        // Phase 3: user fills to 18 from what's left. The 4 best leftovers stay on the market; after that the
        // user and the free-agent pool alternate picks, so the user starts as a lower-half Division 4 side
        // with good free agents still available to sign.
        val u = clubs.first { it.isUser }
        val left = pool.filter { it.id !in taken }.sortedByDescending { it.overall() }
        var skip = false
        for (p in left.drop(min(4, left.size / 10))) {
            if (u.squad.size >= 18) break
            if (skip) { skip = false; continue }
            if (fits(save, u, p, 18 - u.squad.size, 8)) { taken.add(p.id); sign(save, u, p); skip = true }
        }
        for (p in left) { if (u.squad.size >= 15) break; if (p.id !in taken) { taken.add(p.id); sign(save, u, p) } }
        // salaries follow value; unassigned become free agents
        for (p in save.players.values) if (p.clubId.isEmpty()) p.contractYears = 0
    }

    fun strength(save: CareerSave, c: Club): Double {
        val top = c.squad.mapNotNull { save.players[it] }.map { it.overall() }.sortedDescending().take(11)
        return if (top.isEmpty()) 0.0 else top.average()
    }

    private fun setupClubEconomy(save: CareerSave, c: Club) {
        val d = c.division
        val e = save.economy
        c.balance = listOf(60.0, 40.0, 25.0, 20.0)[d - 1] * e
        c.capacity = listOf(40000, 28000, 18000, 12000)[d - 1]
        c.reputation = listOf(75, 60, 45, 30)[d - 1]
        c.ticketPrice = listOf(1200, 800, 600, 400)[d - 1]
        val lvl = listOf(3, 2, 1, 1)[d - 1]
        c.trainingLvl = lvl; c.academyLvl = lvl; c.medicalLvl = lvl
        c.coachBat = lvl; c.coachBowl = lvl; c.coachFit = lvl
        c.sponsor = if (c.isUser) "" else Consts.SPONSORS.random()
        c.sponsorValue = if (c.isUser) 0.0 else Money.tvFor(d, e) * 0.25
    }

    fun genSponsors(save: CareerSave, rnd: Random): MutableList<SponsorOffer> {
        val u = save.userClub()
        val base = Money.tvFor(u.division, save.economy)
        val rep = u.reputation / 100.0
        return Consts.SPONSORS.shuffled(rnd).take(3).mapIndexed { i, n ->
            val v = base * (0.15 + rep * 0.2 + i * 0.05 + rnd.nextDouble() * 0.05)
            val bonus = listOf("Bonus +25% on promotion", "Bonus +40% for a trophy", "Fixed deal, paid in full")[i]
            SponsorOffer(n, (v * 100).roundToInt() / 100.0, bonus)
        }.toMutableList()
    }

    fun createLeague(save: CareerSave) {
        save.league.clear()
        for (d in 1..4) {
            val comp = Competition(id = "L${save.season}D$d", name = "Division $d (Season ${save.season})", key = "LEAGUE_D$d",
                format = "T20", kind = "CLUB", division = d, season = save.season, knockout = if (d == 1) "IPL" else "NONE",
                allowImpact = true)
            comp.teams = save.clubs.values.filter { it.kind == "CLUB" && it.division == d }.map { it.id }.toMutableList()
            CompEngine.addLeagueFixtures(comp, true, 1)
            if (save.userClubId in comp.teams) comp.userTeam = save.userClubId
            save.league.add(comp)
        }
    }

    /* ---------------- match helpers ---------------- */
    fun pitchFor(country: String, rnd: Random): String {
        val r = rnd.nextDouble()
        return when (country) {
            "India", "Sri Lanka", "Bangladesh", "Pakistan", "Afghanistan" -> if (r < 0.35) "DUSTY" else if (r < 0.7) "BALANCED" else "FLAT"
            "England", "New Zealand", "Ireland" -> if (r < 0.4) "GREEN" else if (r < 0.8) "BALANCED" else "FLAT"
            "Australia", "South Africa" -> if (r < 0.25) "GREEN" else if (r < 0.65) "BALANCED" else "FLAT"
            else -> if (r < 0.3) "DUSTY" else if (r < 0.7) "BALANCED" else "FLAT"
        }
    }

    fun weatherFor(country: String, rnd: Random): String {
        val r = rnd.nextDouble()
        return when (country) {
            "England", "Ireland", "New Zealand" -> if (r < 0.3) "Overcast" else if (r < 0.6) "Cloudy" else "Sunny"
            "India", "Bangladesh", "Sri Lanka" -> if (r < 0.25) "Humid" else if (r < 0.35) "Overcast" else "Sunny"
            else -> if (r < 0.15) "Overcast" else if (r < 0.35) "Cloudy" else "Sunny"
        }
    }

    fun makeSim(save: CareerSave, comp: Competition, fx: Fixture, rnd: Random = Random.Default): MatchSim {
        val h = save.clubs[fx.home]!!; val a = save.clubs[fx.away]!!
        for (c in listOf(h, a)) {
            if (c.isUser && c.id == save.userClubId) TeamSelector.validate(c, save.players)
            else if (c.id == comp.userTeam) TeamSelector.validate(c, save.players)
            else TeamSelector.autoPick(c, save.players)
        }
        val overs = when (comp.format) { "T20" -> 20; "ODI" -> 50; else -> 0 }
        val neutral = comp.kind == "NATION" && !comp.series
        val venueClub = h
        val cfg = MatchCfg(comp.format, overs, pitchFor(venueClub.country, rnd), weatherFor(venueClub.country, rnd),
            if (neutral) "${venueClub.stadium} (tournament venue)" else venueClub.stadium, comp.name, comp.allowImpact)
        val sim = MatchSim(cfg, h, a, save.players, h.xi.toList(), a.xi.toList(), h.bowlers.toList(), a.bowlers.toList(), rnd)
        if (comp.format == "TEST" && Modes.fourDay(comp, fx)) sim.testBallsTotal = 4 * 90 * 6 // first-class: 4 days x 90 overs
        return sim
    }

    /** Quick-simulate an AI fixture fully and apply results. */
    fun quickSim(save: CareerSave, comp: Competition, fx: Fixture, rnd: Random = Random.Default) {
        val sim = makeSim(save, comp, fx, rnd)
        sim.simToEnd()
        finishFixture(save, comp, fx, sim)
    }

    fun finishFixture(save: CareerSave, comp: Competition, fx: Fixture, sim: MatchSim) {
        Stats.apply(save, comp, fx, sim)
        // reputation
        sim.winner?.let { w -> save.clubs[w]?.let { it.reputation = min(100, it.reputation + 1) } }
        val involvedUser = fx.home == comp.userTeam || fx.away == comp.userTeam
        if (involvedUser) {
            save.lastResult = "${fx.stage}: ${save.clubs[fx.home]?.short} ${fx.res?.hScore} vs ${save.clubs[fx.away]?.short} ${fx.res?.aScore} — ${fx.res?.text}"
            save.addNews(save.lastResult)
        }
        Social.onMatch(save, comp, fx, sim)
        if (CompEngine.progress(comp)) {
            val a = Stats.award(save, comp)
            save.addNews("🏆 ${comp.name}: ${a.champion} are champions!")
            Social.onTitle(save, comp)
            Modes.onCompDone(save, comp)
        }
    }

    /* ---------------- calendar ---------------- */
    fun fixturesOnDay(save: CareerSave, day: Int): List<Pair<Competition, Fixture>> =
        save.league.flatMap { c -> c.fixtures.filter { it.day == day && !it.played }.map { c to it } }

    fun leagueFinished(save: CareerSave) = save.league.all { it.stage == "DONE" }

    fun startSeason(save: CareerSave): String? {
        val u = save.userClub()
        if (save.auction != null && !save.auction!!.done) return "Finish the auction first."
        if (u.squad.size < 15) return "You need at least 15 players in your squad (currently ${u.squad.size}). Sign free agents in Transfers."
        if (u.sponsor.isEmpty()) {
            val best = save.sponsorOffers.maxByOrNull { it.value }
            if (best != null) { u.sponsor = best.name; u.sponsorValue = best.value }
        }
        save.auction = null
        save.phase = "LEAGUE"; save.day = 0
        save.addNews(if (save.mode == Modes.CLUB) "Season ${save.season} begins! Division ${u.division} campaign under way." else "Season ${save.season} (${save.year}) begins for ${u.name}!")
        return null
    }

    /* ---------------- weekly update ---------------- */
    fun weeklyUpdate(save: CareerSave, hosts: Set<String>, rnd: Random = Random.Default) {
        val e = save.economy
        val mk = Modes.marketKind(save)
        for (c in save.clubs.values.filter { it.kind == mk || (it.isUser && save.mode != Modes.CLUB) }) {
            val ps = c.squad.mapNotNull { save.players[it] }
            for (p in ps) {
                train(save, c, p)
                if (p.injuryDays > 0) { p.injuryDays = max(0, p.injuryDays - 7); if (p.injuryDays == 0) { if (c.isUser) save.addNews("${p.name} has recovered from ${p.injury}."); p.injury = "" } }
                p.fitness = min(100, p.fitness + listOf(12, 8, 5)[c.trainIntensity.coerceIn(0, 2)] + (c.coachFit - 1) * 2)
                if (c.isUser) {
                    if (p.id in c.xi) p.benchCount = 0 else p.benchCount++
                    if (p.benchCount > 3 && p.overall() >= 70) p.morale = max(10, p.morale - 2)
                    if (c.trainIntensity == 2) p.morale = max(10, p.morale - 1) else if (c.trainIntensity == 0) p.morale = min(100, p.morale + 1)
                }
            }
            // finances (national / state boards: central funding instead of a salary cap)
            if (c.kind == "NATION" || c.kind == "DOMESTIC") { inc(c, "Board funding", (if (c.kind == "NATION") 3.0 else 0.6) * e); exp(c, "Staff wages", (c.coachBat + c.coachBowl + c.coachFit + c.scoutLvl) * 0.12 * e); continue }
            val w = LEAGUE_DAYS.toDouble()
            inc(c, "TV & central revenue", Money.tvFor(c.division, e) / w)
            inc(c, "Sponsorship", c.sponsorValue / w)
            if (c.id in hosts) {
                val played = save.league.flatMap { it.fixtures }.filter { it.played && (it.home == c.id || it.away == c.id) }
                val winRate = if (played.isEmpty()) 0.5 else played.count { it.res?.winner == c.id }.toDouble() / played.size
                val basePrice = listOf(1200, 800, 600, 400)[(c.division - 1).coerceIn(0, 3)]
                val demand = (0.3 + c.reputation / 140.0 + winRate * 0.3 - (c.ticketPrice - basePrice) / 3000.0).coerceIn(0.1, 1.0)
                val att = (c.capacity * demand).toInt()
                c.lastAttendance = att
                inc(c, "Ticket sales", att * c.ticketPrice / 1e7)
                inc(c, "Merchandise & food", att * 150 / 1e7)
            }
            val payroll = c.squad.sumOf { save.players[it]?.salary ?: 0.0 }
            exp(c, "Player salaries", payroll / w)
            exp(c, "Staff wages", (c.coachBat + c.coachBowl + c.coachFit + c.scoutLvl) * 0.35 * e / w)
            exp(c, "Facilities upkeep", ((c.trainingLvl + c.academyLvl + c.medicalLvl) * 0.25 + c.capacity / 10000.0 * 0.3) * e / w)
            // AI squad maintenance
            if (!c.isUser && c.squad.size < 17 && rnd.nextDouble() < 0.5) signBestFreeAgent(save, c)
        }
        // offers for listed players
        val u = save.userClub()
        if (u.balance < 0) {
            u.reputation = max(5, u.reputation - 1)
            for (pid in u.squad) save.players[pid]?.let { it.morale = max(10, it.morale - 1) }
            if (save.day % 4 == 0) save.addNews("Board warning: the club is in debt (${Money.fmt(u.balance)}). Cut costs or sell players.")
        }
        for (pid in u.squad.toList()) {
            val p = save.players[pid] ?: continue
            if (!p.forSale || rnd.nextDouble() > 0.4) continue
            val buyer = save.clubs.values.filter { it.kind == mk && !it.isUser && it.squad.size < 24 }.randomOrNull(rnd) ?: continue
            val fee = (Money.value(p.overall(), p.age(save.year), save.economy) * (0.7 + rnd.nextDouble() * 0.6) * 100).roundToInt() / 100.0
            save.offers.add(TransferOffer(save.nextId++, pid, buyer.id, fee))
            save.addNews("Offer: ${buyer.name} bid ${Money.fmt(fee)} for ${p.name}")
        }
    }

    fun inc(c: Club, k: String, v: Double) { c.income[k] = (c.income[k] ?: 0.0) + v; c.balance += v }
    fun exp(c: Club, k: String, v: Double) { c.expense[k] = (c.expense[k] ?: 0.0) + v; c.balance -= v }

    fun train(save: CareerSave, c: Club, p: GPlayer) {
        val age = p.age(save.year)
        val ageF = when { age <= 21 -> 1.6; age <= 24 -> 1.2; age <= 28 -> 0.7; age <= 31 -> 0.3; else -> 0.0 }
        val inten = listOf(0.6, 1.0, 1.4)[c.trainIntensity.coerceIn(0, 2)]
        val fac = 1 + 0.1 * (c.trainingLvl - 1)
        val coach = when (c.trainFocus) { 1 -> c.coachBat; 2 -> c.coachBowl; 3 -> c.coachFit; else -> (c.coachBat + c.coachBowl) / 2 }
        val gain = 0.22 * ageF * inten * fac * (1 + 0.08 * (coach - 1)) * (if (p.overall() < p.potential) 1.0 else 0.05)
        p.trainXp += gain
        while (p.trainXp >= 1) { p.trainXp -= 1; improve(p, c.trainFocus) }
    }

    fun improve(p: GPlayer, focus: Int, rnd: Random = Random.Default) {
        fun up(v: Int) = min(99, v + 1)
        when (focus) {
            1 -> if (rnd.nextBoolean()) p.tech = up(p.tech) else p.power = up(p.power)
            2 -> if (p.role == "BOWL" || p.role == "AR") p.bowl = up(p.bowl) else p.field = up(p.field)
            3 -> { p.field = up(p.field); p.fitness = min(100, p.fitness + 5) }
            else -> when (p.role) {
                "BOWL" -> p.bowl = up(p.bowl)
                "AR" -> when (rnd.nextInt(3)) { 0 -> p.tech = up(p.tech); 1 -> p.power = up(p.power); else -> p.bowl = up(p.bowl) }
                "WK" -> if (rnd.nextInt(3) == 0) p.keep = up(p.keep) else if (rnd.nextBoolean()) p.tech = up(p.tech) else p.power = up(p.power)
                else -> if (rnd.nextBoolean()) p.tech = up(p.tech) else p.power = up(p.power)
            }
        }
    }

    /* ---------------- transfers ---------------- */
    fun payroll(save: CareerSave, c: Club) = c.squad.sumOf { save.players[it]?.salary ?: 0.0 }
    fun purse(save: CareerSave, c: Club) = Money.capFor(c.division, save.economy) - payroll(save, c)
    fun overseasCount(save: CareerSave, c: Club) = c.squad.count { save.players[it]?.let { p -> TeamSelector.isOverseas(p, c) } == true }

    fun canJoin(save: CareerSave, c: Club, p: GPlayer, salary: Double): String? {
        if (c.squad.size >= 25) return "Squad is full (25 max)."
        if (TeamSelector.isOverseas(p, c) && overseasCount(save, c) >= 8) return "Overseas limit reached (8 per squad)."
        if (purse(save, c) < salary) return "Not enough salary-cap room (${Money.fmt(purse(save, c))} left)."
        return null
    }

    fun askingPrice(save: CareerSave, p: GPlayer): Double {
        val v = Money.value(p.overall(), p.age(save.year), save.economy)
        return ((if (p.clubId.isEmpty()) 0.0 else v * 1.6) * 100).roundToInt() / 100.0
    }

    fun signFreeAgent(save: CareerSave, pid: String): String {
        val p = save.players[pid] ?: return "Unknown player"
        if (p.clubId.isNotEmpty()) return "Player is not a free agent."
        val u = save.userClub()
        val sal = max(0.2 * save.economy, Money.value(p.overall(), p.age(save.year), save.economy))
        canJoin(save, u, p, sal)?.let { return it }
        p.salary = sal; p.contractYears = 2
        sign(save, u, p)
        save.addNews("Signed free agent ${p.name} on ${Money.fmt(sal)}/season")
        Social.onSigning(save, p, "FREE", 0.0)
        return "Signed ${p.name}!"
    }

    fun bidForPlayer(save: CareerSave, pid: String, fee: Double, rnd: Random = Random.Default): String {
        val p = save.players[pid] ?: return "Unknown player"
        val seller = save.clubs[p.clubId] ?: return signFreeAgent(save, pid)
        val u = save.userClub()
        if (seller.isUser) return "Already your player."
        if (u.balance < fee) return "Not enough funds (balance ${Money.fmt(u.balance)})."
        canJoin(save, u, p, p.salary)?.let { return it }
        if (seller.squad.size <= 16) return "${seller.name} won't sell: squad too small."
        val ask = askingPrice(save, p) * (0.9 + rnd.nextDouble() * 0.25)
        if (fee < ask) return "${seller.name} rejected ${Money.fmt(fee)}. They want around ${Money.fmt(askingPrice(save, p))}."
        seller.squad.remove(pid); seller.xi.remove(pid); seller.bowlers.remove(pid)
        u.balance -= fee; seller.balance += fee
        u.expense["Transfer fees"] = (u.expense["Transfer fees"] ?: 0.0) + fee
        sign(save, u, p)
        p.contractYears = max(p.contractYears, 2)
        save.addNews("TRANSFER: ${p.name} joins ${u.name} from ${seller.name} for ${Money.fmt(fee)}")
        Social.onSigning(save, p, "TRANSFER", fee)
        return "Deal done! ${p.name} signed for ${Money.fmt(fee)}."
    }

    fun proposeSwap(save: CareerSave, myPid: String, theirPid: String): String {
        val mine = save.players[myPid] ?: return "?"
        val theirs = save.players[theirPid] ?: return "?"
        val other = save.clubs[theirs.clubId] ?: return "That player is a free agent — just sign him."
        val u = save.userClub()
        val vm = Money.value(mine.overall(), mine.age(save.year), 1.0)
        val vt = Money.value(theirs.overall(), theirs.age(save.year), 1.0)
        if (vm < vt * 1.05) return "${other.name} rejected the trade — they value ${theirs.name} more."
        if (TeamSelector.isOverseas(theirs, u) && !TeamSelector.isOverseas(mine, u) && overseasCount(save, u) >= 8) return "Overseas limit would be exceeded."
        u.squad.remove(myPid); u.xi.remove(myPid); u.bowlers.remove(myPid)
        other.squad.remove(theirPid); other.xi.remove(theirPid); other.bowlers.remove(theirPid)
        sign(save, other, mine); sign(save, u, theirs)
        save.addNews("TRADE: ${mine.name} ⇄ ${theirs.name} with ${other.name}")
        return "Trade completed!"
    }

    fun release(save: CareerSave, pid: String): String {
        val p = save.players[pid] ?: return "?"
        val u = save.userClub()
        if (u.squad.size <= 11) return "You need at least 11 players."
        val comp = p.salary * 0.5
        u.balance -= comp
        u.expense["Release compensation"] = (u.expense["Release compensation"] ?: 0.0) + comp
        u.squad.remove(pid); u.xi.remove(pid); u.bowlers.remove(pid)
        if (u.captain == pid) u.captain = ""; if (u.impact == pid) u.impact = ""
        p.clubId = ""; p.forSale = false; p.contractYears = 0
        p.history.add("Season ${save.season}: Released by ${u.name}")
        save.addNews("Released ${p.name} (compensation ${Money.fmt(comp)})")
        return "${p.name} released."
    }

    fun acceptOffer(save: CareerSave, id: Int): String {
        val o = save.offers.firstOrNull { it.id == id } ?: return "Offer expired"
        val p = save.players[o.pid] ?: return "?"
        val buyer = save.clubs[o.fromClub] ?: return "?"
        val u = save.userClub()
        save.offers.removeAll { it.pid == o.pid }
        if (p.clubId != u.id) return "Player no longer at your club."
        u.squad.remove(p.id); u.xi.remove(p.id); u.bowlers.remove(p.id)
        u.balance += o.fee; buyer.balance -= o.fee
        u.income["Transfer sales"] = (u.income["Transfer sales"] ?: 0.0) + o.fee
        p.forSale = false
        sign(save, buyer, p)
        save.addNews("SOLD: ${p.name} to ${buyer.name} for ${Money.fmt(o.fee)}")
        return "Sold ${p.name} for ${Money.fmt(o.fee)}"
    }

    /** AI squad maintenance used by IPL careers. */
    fun fillAiSquad(save: CareerSave, c: Club) {
        var g = 0
        while (c.squad.size < 18 && g++ < 10) signBestFreeAgent(save, c)
    }

    fun signBestFreeAgent(save: CareerSave, c: Club) {
        val n = needs(save, c)
        val fa = save.players.values.filter { it.clubId.isEmpty() && !it.retired && !it.academy }
            .filter { canJoin(save, c, it, max(0.2 * save.economy, Money.value(it.overall(), it.age(save.year), save.economy))) == null }
            .sortedByDescending { it.overall() + (if ((n[if (it.role == "AR") "BOWL" else it.role] ?: 0) > 0) 8 else 0) }
            .firstOrNull() ?: return
        fa.salary = max(0.2 * save.economy, Money.value(fa.overall(), fa.age(save.year), save.economy)); fa.contractYears = 2
        sign(save, c, fa)
    }

    /* ---------------- club upgrades ---------------- */
    fun upgradeCost(c: Club, what: String, economy: Double): Double = economy * when (what) {
        "training" -> c.trainingLvl * 4.0; "academy" -> c.academyLvl * 3.0; "medical" -> c.medicalLvl * 2.5
        "scouting" -> c.scoutLvl * 2.0; "coachBat" -> c.coachBat * 1.5; "coachBowl" -> c.coachBowl * 1.5
        "coachFit" -> c.coachFit * 1.5; "stadium" -> 6.0 * (c.capacity / 20000.0 + 1); else -> 0.0
    }

    fun upgrade(save: CareerSave, what: String): String {
        val c = save.userClub()
        val lvl = when (what) {
            "training" -> c.trainingLvl; "academy" -> c.academyLvl; "medical" -> c.medicalLvl; "scouting" -> c.scoutLvl
            "coachBat" -> c.coachBat; "coachBowl" -> c.coachBowl; "coachFit" -> c.coachFit; else -> 0
        }
        if (what != "stadium" && lvl >= 5) return "Already at maximum level."
        if (what == "stadium" && c.capacity >= 80000) return "Stadium already at maximum capacity."
        val cost = upgradeCost(c, what, save.economy)
        if (c.balance < cost) return "Not enough funds (${Money.fmt(cost)} needed)."
        c.balance -= cost
        c.expense["Investments"] = (c.expense["Investments"] ?: 0.0) + cost
        when (what) {
            "training" -> c.trainingLvl++; "academy" -> c.academyLvl++; "medical" -> c.medicalLvl++; "scouting" -> c.scoutLvl++
            "coachBat" -> c.coachBat++; "coachBowl" -> c.coachBowl++; "coachFit" -> c.coachFit++
            "stadium" -> c.capacity += 5000
        }
        save.addNews("Club investment: $what upgraded for ${Money.fmt(cost)}")
        return "Upgrade complete!"
    }

    fun scoutPlayer(save: CareerSave, pid: String): String {
        val p = save.players[pid] ?: return "?"
        val c = save.userClub()
        val cost = 0.1 * save.economy
        if (p.scouted) return "Already scouted."
        if (c.balance < cost) return "Not enough funds."
        c.balance -= cost; c.expense["Scouting"] = (c.expense["Scouting"] ?: 0.0) + cost
        p.scouted = true
        return "Scout report: ${p.name} potential ${p.potential}."
    }

    fun scoutingMission(save: CareerSave, country: String, rnd: Random = Random.Default): String {
        val c = save.userClub()
        val cost = 1.0 * save.economy
        if (c.balance < cost) return "Not enough funds (${Money.fmt(cost)})."
        c.balance -= cost; c.expense["Scouting"] = (c.expense["Scouting"] ?: 0.0) + cost
        val found = (1..3).map {
            val p = makeFictional(save, country, rnd.nextInt(17, 22), rnd.nextInt(50, 64) + c.scoutLvl * 2, rnd.nextInt(68, 84) + c.scoutLvl * 3, rnd)
            p.scouted = true; p.history.add("Discovered by ${c.name} scouts in $country"); p
        }
        save.addNews("Scouts found ${found.size} fictional prospects in $country — now free agents in Transfers.")
        return "Found: " + found.joinToString { "${it.name} (${it.overall()}/${it.potential})" }
    }

    fun extraTraining(save: CareerSave, pid: String): String {
        val p = save.players[pid] ?: return "?"
        val c = save.userClub()
        val cost = 0.2 * save.economy
        if (p.injured) return "Player is injured."
        if (p.fitness < 40) return "Too tired for an extra session."
        if (c.balance < cost) return "Not enough funds."
        c.balance -= cost; c.expense["Training sessions"] = (c.expense["Training sessions"] ?: 0.0) + cost
        p.form = min(99, p.form + 3); p.fitness = max(10, p.fitness - 8)
        p.trainXp += if (p.overall() < p.potential) 0.5 else 0.05
        while (p.trainXp >= 1) { p.trainXp -= 1; improve(p, c.trainFocus) }
        return "${p.name} completed an extra session (form ${p.form}, fitness ${p.fitness})."
    }

    fun promoteYouth(save: CareerSave, pid: String): String {
        val c = save.userClub(); val p = save.players[pid] ?: return "?"
        canJoin(save, c, p, 0.2 * save.economy)?.let { return it }
        c.academyList.remove(pid); p.academy = false; p.salary = 0.2 * save.economy; p.contractYears = 3
        c.squad.add(pid); p.clubId = c.id
        p.history.add("Season ${save.season}: Promoted from ${c.name} academy")
        return "${p.name} promoted to the first team!"
    }

    fun releaseYouth(save: CareerSave, pid: String): String {
        val c = save.userClub(); val p = save.players[pid] ?: return "?"
        c.academyList.remove(pid); p.academy = false; p.clubId = ""
        return "${p.name} released from the academy."
    }

    /* ---------------- season end ---------------- */
    fun endSeason(save: CareerSave, rnd: Random = Random.Default) {
        val sum = mutableListOf<String>()
        val u = save.userClub()
        val e = save.economy
        val moves = HashMap<String, Int>()
        if (save.mode != Modes.CLUB) Modes.endSeason(save, sum)
        for (comp in save.league.filter { save.mode == Modes.CLUB }) {
            val a = save.awards.firstOrNull { it.season == save.season && it.comp == comp.name } ?: Stats.award(save, comp)
            val t = CompEngine.table(comp)
            sum.add("Division ${comp.division}: Champions ${a.champion}. Top run-scorer ${save.players[a.orange]?.name ?: "-"} (${a.orangeRuns}), top wicket-taker ${save.players[a.purple]?.name ?: "-"} (${a.purpleWk}).")
            if (comp.division == 1) {
                comp.champion?.let { save.clubs[it]?.let { c -> c.balance += 25 * e; c.reputation = min(100, c.reputation + 10) } }
                comp.runnerUp?.let { save.clubs[it]?.let { c -> c.balance += 12 * e } }
            } else {
                save.clubs[t[0].team]?.let { c -> c.balance += 8 * e / comp.division; c.reputation = min(100, c.reputation + 6); c.trophies.add("Division ${comp.division} title (S${save.season})") }
            }
            if (comp.division > 1) for (r in t.take(2)) moves[r.team] = comp.division - 1
            if (comp.division < 4) for (r in t.takeLast(2)) moves[r.team] = comp.division + 1
            val pos = t.indexOfFirst { it.team == u.id }
            if (pos >= 0) sum.add(0, "${u.name} finished ${pos + 1}${suffix(pos + 1)} in Division ${comp.division}.")
        }
        for ((id, d) in moves) {
            val c = save.clubs[id] ?: continue
            val up = d < c.division
            c.division = d
            c.reputation = (c.reputation + if (up) 8 else -8).coerceIn(5, 100)
            if (up) c.balance += 5 * e
            if (c.isUser) {
                sum.add(0, if (up) "⬆ PROMOTED to Division $d!" else "⬇ Relegated to Division $d.")
                if (up && c.sponsor.isNotEmpty()) c.balance += c.sponsorValue * 0.25
            }
        }
        if (save.awards.any { it.season == save.season && it.champion == u.name } && u.sponsor.isNotEmpty()) u.balance += u.sponsorValue * 0.4
        // ageing, development, retirement, contracts
        save.year++
        val retired = mutableListOf<String>()
        for (p in save.players.values) {
            if (p.retired) continue
            val age = p.age(save.year)
            val delta: () -> Int = {
                when {
                    age <= 23 -> if (p.overall() < p.potential) rnd.nextInt(1, 5) else 0
                    age <= 29 -> if (p.overall() < p.potential) rnd.nextInt(0, 3) else 0
                    age <= 32 -> rnd.nextInt(-2, 2)
                    age <= 35 -> rnd.nextInt(-4, 1)
                    else -> rnd.nextInt(-6, 0)
                }
            }
            when (p.role) {
                "BOWL" -> p.bowl = (p.bowl + delta()).coerceIn(15, 99)
                "AR" -> { p.bowl = (p.bowl + delta()).coerceIn(15, 99); p.tech = (p.tech + delta()).coerceIn(20, 99); p.power = (p.power + delta()).coerceIn(20, 99) }
                else -> { p.tech = (p.tech + delta()).coerceIn(20, 99); p.power = (p.power + delta()).coerceIn(20, 99) }
            }
            if (age >= 28) p.potential = max(p.overall(), p.potential - 1)
            p.fitness = 100; p.injuryDays = 0; p.injury = ""; p.form = (p.form + 60) / 2; p.benchCount = 0
            val retireP = when { age >= 40 -> 1.0; age >= 35 -> (age - 34) * 0.15; age >= 32 && p.overall() < 50 -> 0.3; else -> 0.0 }
            if (rnd.nextDouble() < retireP) {
                p.retired = true
                save.clubs[p.clubId]?.let { c -> c.squad.remove(p.id); c.xi.remove(p.id); c.bowlers.remove(p.id); if (c.isUser) retired.add(p.name) }
                p.history.add("Season ${save.season}: Retired aged $age")
                p.clubId = ""
                if (p.real && p.overall() >= 75) save.addNews("RETIREMENT: ${p.name} (age $age) retires from the game.")
                continue
            }
            if (p.clubId.isNotEmpty() && !p.academy) {
                p.contractYears--
                if (p.contractYears <= 0) {
                    val c = save.clubs[p.clubId]
                    if (c != null) {
                        val keep = c.isUser || p.overall() >= (c.squad.mapNotNull { save.players[it]?.overall() }.sorted().getOrNull(c.squad.size / 3) ?: 0) || c.squad.size <= 18
                        if (keep) {
                            p.salary = max(0.2 * e, Money.value(p.overall(), age, e)); p.contractYears = rnd.nextInt(1, 4)
                            if (c.isUser) sum.add("Contract renewed: ${p.name} — ${Money.fmt(p.salary)}/season")
                        } else {
                            c.squad.remove(p.id); c.xi.remove(p.id); c.bowlers.remove(p.id); p.clubId = ""
                        }
                    }
                }
            }
        }
        if (retired.isNotEmpty()) sum.add("Retired from your squad: ${retired.joinToString()}")
        // youth intake
        val intake = 2 + u.academyLvl / 2
        val youths = if (save.mode == Modes.INTL || u.academyList.size >= 12) emptyList() else (1..intake).map {
            makeFictional(save, u.country, rnd.nextInt(16, 19), rnd.nextInt(40, 54) + u.academyLvl * 2, rnd.nextInt(62, 80) + u.academyLvl * 4, rnd).also { y ->
                y.academy = true; y.clubId = u.id; u.academyList.add(y.id)
            }
        }
        if (youths.isNotEmpty()) sum.add("Academy intake: ${youths.joinToString { "${it.name} (${it.role})" }} — fictional prospects.")
        for (c in save.clubs.values.filter { it.kind == Modes.marketKind(save) && !it.isUser }) {
            if (c.squad.size < 20) { val y = makeFictional(save, c.country, rnd.nextInt(17, 20), rnd.nextInt(48, 62), rnd.nextInt(65, 85), rnd); sign(save, c, y) }
            var g = 0
            while (c.squad.size < 17 && g++ < 6) signBestFreeAgent(save, c)
        }
        // fresh free agents to keep the market alive
        val countries = Consts.CLUBS.keys.toList()
        repeat(12) { makeFictional(save, countries.random(rnd), rnd.nextInt(18, 25), rnd.nextInt(52, 70), rnd.nextInt(60, 86), rnd) }
        // finances snapshot
        for (c in save.clubs.values) { c.lastIncome = c.income.toMutableMap(); c.lastExpense = c.expense.toMutableMap(); c.income.clear(); c.expense.clear() }
        u.sponsor = ""; u.sponsorValue = 0.0
        save.sponsorOffers = genSponsors(save, rnd)
        save.offers.clear()
        save.seasonSummary = sum.toMutableList()
        save.history.add(0, "Season ${save.season}: " + (sum.firstOrNull() ?: ""))
        save.phase = "OFFSEASON"
        save.auctionDoneThisSeason = false
    }

    fun beginNextSeason(save: CareerSave) {
        if (save.mode != Modes.CLUB) { Modes.beginNextSeason(save); return }
        save.season++
        save.day = 0
        createLeague(save)
        for (c in save.clubs.values.filter { it.kind == "CLUB" && !it.isUser }) {
            c.sponsor = Consts.SPONSORS.random(); c.sponsorValue = Money.tvFor(c.division, save.economy) * 0.25
            TeamSelector.autoPick(c, save.players)
        }
        save.phase = "PRESEASON"
        save.auction = null
        if (save.season % 3 == 1) {
            save.auction = Auction.start(save, "MEGA")
            save.addNews("MEGA AUCTION year! Retain up to 4 players before the auction.")
        } else save.addNews("Pre-season: pick a sponsor and (optionally) join the mini auction.")
    }

    fun suffix(n: Int) = when { n in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }
}
