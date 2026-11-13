package com.cricketcoach.manager.game

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** IPL-style auction: retention (mega), live lot-by-lot bidding vs AI clubs, purse & overseas limits, unsold players. */
object Auction {
    private val DIV_SCALE = listOf(1.0, 0.6, 0.4, 0.25)

    /** The franchise bidding in this auction. Multi-team careers always auction for the IPL franchise, even if the active team is the national or state side. */
    fun team(save: CareerSave, st: AuctionState): Club = save.clubs[st.teamId.ifEmpty { save.marketClub().id }] ?: save.marketClub()

    fun start(save: CareerSave, type: String): AuctionState {
        val st = AuctionState(type = type, teamId = save.marketClub().id)
        if (type == "MEGA") {
            st.phase = "RETENTION"
            st.log.add("MEGA AUCTION: every club may retain up to 4 players. Everyone else enters the auction pool.")
        } else {
            st.lots = save.players.values.filter { it.clubId.isEmpty() && !it.retired && !it.academy }
                .sortedByDescending { it.overall() }.take(60).map { it.id }.toMutableList()
            st.phase = "LIVE"
            st.log.add("MINI AUCTION: ${st.lots.size} players in the pool.")
            openLot(save, st)
        }
        return st
    }

    fun retentionCost(save: CareerSave, index: Int, division: Int): Double =
        ((listOf(18.0, 14.0, 11.0, 8.0)[index.coerceIn(0, 3)] * DIV_SCALE[(division - 1).coerceIn(0, 3)] * save.economy) * 100).roundToInt() / 100.0

    fun confirmRetention(save: CareerSave, st: AuctionState, userRetain: List<String>) {
        for (c in save.clubs.values.filter { it.kind == Modes.marketKind(save) }) {
            val keep = if (c.isUser) userRetain.filter { it in c.squad }.take(4)
            else c.squad.mapNotNull { save.players[it] }.filter { it.age(save.year) <= 33 }.sortedByDescending { it.overall() }.take(4).map { it.id }
            keep.forEachIndexed { i, pid -> save.players[pid]?.let { it.salary = retentionCost(save, i, c.division); it.contractYears = 3; st.retained.add(AuctionSale(pid, c.id, it.salary)) } }
            for (pid in c.squad.filter { it !in keep }) save.players[pid]?.let { it.clubId = ""; it.forSale = false }
            c.squad = keep.toMutableList(); c.xi.clear(); c.bowlers.clear(); c.captain = ""; c.vice = ""; c.impact = ""; c.keeper = ""
        }
        val u = team(save, st)
        st.log.add("Retained by you: " + (u.squad.mapNotNull { save.players[it]?.name }.joinToString().ifEmpty { "none" }))
        st.lots = save.players.values.filter { it.clubId.isEmpty() && !it.retired && !it.academy }
            .sortedByDescending { it.overall() }.take(150).map { it.id }.toMutableList()
        st.phase = "LIVE"
        st.log.add("Auction pool: ${st.lots.size} live lots (remaining players go to the accelerated round).")
        openLot(save, st)
    }

    fun cur(save: CareerSave, st: AuctionState): GPlayer? = st.lots.getOrNull(st.index)?.let { save.players[it] }

    private fun purseAvail(save: CareerSave, c: Club) = Career.purse(save, c) - 0.2 * save.economy * max(0, 16 - c.squad.size - 1)

    private fun eligible(save: CareerSave, c: Club, p: GPlayer, amount: Double): Boolean =
        c.squad.size < 25 && (!TeamSelector.isOverseas(p, c) || Career.overseasCount(save, c) < 8) && purseAvail(save, c) >= amount

    private fun valuation(save: CareerSave, c: Club, p: GPlayer, rnd: Random): Double {
        var v = Money.value(p.overall(), p.age(save.year), save.economy) * (0.75 + rnd.nextDouble() * 0.55)
        v *= listOf(0.85, 1.0, 1.15)[save.difficulty.coerceIn(0, 2)]
        val ps = c.squad.mapNotNull { save.players[it] }
        val wk = ps.count { it.role == "WK" }; val bowl = ps.count { it.role == "BOWL" || it.role == "AR" }; val bat = ps.count { it.role == "BAT" }
        if ((p.role == "WK" && wk < 2) || ((p.role == "BOWL" || p.role == "AR") && bowl < 7) || (p.role == "BAT" && bat < 5)) v *= 1.35
        if (c.squad.size >= 22) v *= 0.6
        if (c.squad.size < 15) v *= 1.2
        return v
    }

    fun nextAmount(st: AuctionState): Double =
        if (st.curBidder.isEmpty()) st.curBid else ((st.curBid + Money.increment(st.curBid)) * 100).roundToInt() / 100.0

    fun openLot(save: CareerSave, st: AuctionState, rnd: Random = Random.Default) {
        while (st.index < st.lots.size && save.players[st.lots[st.index]]?.clubId?.isNotEmpty() == true) st.index++
        if (st.index >= st.lots.size) { finish(save, st); return }
        val p = cur(save, st)!!
        st.curBid = Money.basePrice(p.overall(), save.economy); st.curBidder = ""; st.countdown = 0; st.userPassed = false; st.lotBidders.clear()
        st.aiVal = save.clubs.values.filter { it.kind == Modes.marketKind(save) && !it.isUser && eligible(save, it, p, st.curBid) }
            .associate { it.id to valuation(save, it, p, rnd) }.toMutableMap()
        st.log.add("LOT ${st.index + 1}/${st.lots.size}: ${p.name} (${Consts.ROLE_NAMES[p.role]}, ${p.country}, OVR ${p.overall()}) — base ${Money.fmt(st.curBid)}")
        trimLog(st)
    }

    fun userBid(save: CareerSave, st: AuctionState): String? {
        val p = cur(save, st) ?: return "No player on the block."
        val u = team(save, st)
        if (st.curBidder == u.id) return "You are already the highest bidder."
        val amt = nextAmount(st)
        Career.canJoin(save, u, p, amt)?.let { return it }
        val reserve = 0.2 * save.economy * max(0, 15 - u.squad.size - 1)
        if (Career.purse(save, u) - amt < reserve)
            return "You must keep ${Money.fmt(reserve)} of cap room to complete a 15-man squad (max bid now ${Money.fmt(Career.purse(save, u) - reserve)})."
        st.curBid = amt; st.curBidder = u.id; st.countdown = 0; st.userPassed = false; noteBid(st, u.id)
        st.log.add("YOU bid ${Money.fmt(amt)}"); trimLog(st)
        return null
    }

    /** One step of live bidding. Returns true when the lot was hammered. */
    fun tick(save: CareerSave, st: AuctionState, rnd: Random = Random.Default): Boolean {
        if (st.phase != "LIVE" || st.done) return false
        val p = cur(save, st) ?: return false
        if (aiStep(save, st, p, rnd, 0.85)) return false
        st.countdown++
        when (st.countdown) {
            1 -> st.log.add("Going once at ${Money.fmt(st.curBid)}…")
            2 -> st.log.add("Going twice…")
            else -> { hammer(save, st); return true }
        }
        trimLog(st)
        return false
    }

    private fun aiStep(save: CareerSave, st: AuctionState, p: GPlayer, rnd: Random, chance: Double): Boolean {
        val amt = nextAmount(st)
        val cands = st.aiVal.filter { (id, v) -> id != st.curBidder && v >= amt && save.clubs[id]?.let { eligible(save, it, p, amt) } == true }
        if (cands.isEmpty() || rnd.nextDouble() >= chance) return false
        val total = cands.values.sum()
        var r = rnd.nextDouble() * total
        var chosen = cands.keys.first()
        for ((k, v) in cands) { r -= v; if (r <= 0) { chosen = k; break } }
        st.curBid = amt; st.curBidder = chosen; st.countdown = 0; noteBid(st, chosen)
        st.log.add("${save.clubs[chosen]?.short} bid ${Money.fmt(amt)}")
        trimLog(st)
        return true
    }

    /** User passes / sims the lot: AI clubs fight it out instantly. */
    fun resolveLot(save: CareerSave, st: AuctionState, rnd: Random = Random.Default) {
        val p = cur(save, st) ?: return
        st.userPassed = true
        var guard = 0
        while (guard++ < 200 && aiStep(save, st, p, rnd, 1.0)) { }
        hammer(save, st)
    }

    /** Squad size the "SIM ALL" auto-bidder aims for on the user's behalf. */
    const val AUTO_TARGET = 18

    /**
     * SIM ALL: every remaining lot is resolved instantly. With [bidForUser] the user's club joins the
     * bidding like an AI franchise (same valuation, same purse reserve) until it has [AUTO_TARGET] players,
     * so a simmed auction never leaves the user with an unplayable squad.
     */
    fun autoComplete(save: CareerSave, st: AuctionState, rnd: Random = Random.Default, bidForUser: Boolean = true) {
        var guard = 0
        val u = team(save, st)
        while (!st.done && guard++ < 1000) {
            val p = cur(save, st)
            if (bidForUser && p != null && u.squad.size < AUTO_TARGET && st.curBidder != u.id && eligible(save, u, p, nextAmount(st)) &&
                Career.canJoin(save, u, p, nextAmount(st)) == null) {
                // a little more eager the emptier the squad is
                // ...but budgeted: keep enough purse to fill every remaining slot at the minimum price, and don't blow
                // more than ~2.5x the average per-slot budget on one player
                val left = (AUTO_TARGET - u.squad.size).coerceAtLeast(1)
                val minP = Money.basePrice(0, save.economy)
                val purse = Career.purse(save, u)
                val cap = minOf(purse - (left - 1) * minP, purse / left * 2.5)
                st.aiVal[u.id] = minOf(valuation(save, u, p, rnd) * (if (u.squad.size < 12) 1.15 else 1.0), cap)
            } else st.aiVal.remove(u.id)
            resolveLot(save, st, rnd)
        }
        if (bidForUser) acceleratedRound(save, st, u)
    }

    /**
     * Accelerated round after SIM ALL: if the user's club was outbid too often, it picks up the best remaining
     * unsold players (then other free agents) at their base price until it has [AUTO_TARGET], within cap and overseas limits.
     */
    private fun acceleratedRound(save: CareerSave, st: AuctionState, u: Club) {
        if (u.squad.size >= AUTO_TARGET) return
        val unsold = st.unsold.toHashSet()
        val pool = save.players.values.filter { it.clubId.isEmpty() && !it.retired && !it.academy }
            .sortedWith(compareByDescending<GPlayer> { it.id in unsold }.thenByDescending { it.overall() })
        var n = 0
        for (p in pool) {
            if (u.squad.size >= AUTO_TARGET) break
            val price = Money.basePrice(p.overall(), save.economy)
            if (Career.canJoin(save, u, p, price) != null) continue
            p.clubId = u.id; u.squad.add(p.id); p.salary = price; p.contractYears = if (st.type == "MEGA") 3 else 2
            p.history.add("Season ${save.season}: Picked up by ${u.name} in the accelerated round for ${Money.fmt(price)}")
            st.sold.add(AuctionSale(p.id, u.id, price)); st.unsold.remove(p.id); n++
        }
        if (n > 0) {
            if (u.squad.size >= 15) st.log.removeAll { it.startsWith("⚠ Your squad has") }
            st.log.add("⚡ Accelerated round: ${u.name} picked up $n player(s) at base price — squad now ${u.squad.size}.")
            save.addNews("Auction: accelerated round added $n player(s) at base price")
            trimLog(st)
        }
        TeamSelector.autoPick(u, save.players)
    }

    private fun noteBid(st: AuctionState, club: String) {
        st.bidSeq++
        st.lotBidders.remove(club); st.lotBidders.add(club)
        while (st.lotBidders.size > 12) st.lotBidders.removeAt(0)
    }

    private fun hammer(save: CareerSave, st: AuctionState) {
        val p = cur(save, st) ?: return
        st.lastSeq++; st.lastPid = p.id; st.lastClub = st.curBidder; st.lastPrice = st.curBid
        if (st.curBidder.isNotEmpty()) {
            val c = save.clubs[st.curBidder]!!
            p.clubId = c.id; c.squad.add(p.id); p.salary = st.curBid; p.contractYears = if (st.type == "MEGA") 3 else 2
            p.history.add("Season ${save.season}: Bought by ${c.name} at ${st.type.lowercase()} auction for ${Money.fmt(st.curBid)}")
            st.sold.add(AuctionSale(p.id, c.id, st.curBid))
            st.log.add("🔨 SOLD! ${p.name} to ${c.name} for ${Money.fmt(st.curBid)}")
            if (c.isUser) { save.addNews("Auction: signed ${p.name} for ${Money.fmt(st.curBid)}"); Social.onSigning(save, p, "AUCTION", st.curBid) }
            else if (p.overall() >= 76 || (Stumps.account(save, p.id)?.followers ?: 0L) >= 3_000_000L) Yorker.auctionSale(save, p, c, st.curBid)
        } else {
            st.unsold.add(p.id)
            st.log.add("UNSOLD: ${p.name}")
        }
        st.index++
        trimLog(st)
        openLot(save, st)
    }

    private fun finish(save: CareerSave, st: AuctionState) {
        if (st.done) return
        st.done = true; st.phase = "DONE"
        for (c in save.clubs.values.filter { it.kind == Modes.marketKind(save) && !it.isUser }) {
            var g = 0
            while (c.squad.size < 16 && g++ < 20) Career.signBestFreeAgent(save, c)
            TeamSelector.autoPick(c, save.players)
        }
        val u = team(save, st)
        val mine = st.sold.filter { it.club == u.id }
        st.log.add("AUCTION COMPLETE. You bought ${mine.size} player(s) for ${Money.fmt(mine.sumOf { it.price })}. Unsold players are now free agents.")
        save.auctionDoneThisSeason = true
        if (u.squad.size < 15) st.log.add("⚠ Your squad has ${u.squad.size} players — sign free agents in Transfers to reach 15.")
        TeamSelector.autoPick(u, save.players)
    }

    private fun trimLog(st: AuctionState) { while (st.log.size > 80) st.log.removeAt(0) }
}
