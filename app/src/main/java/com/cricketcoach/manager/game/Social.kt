package com.cricketcoach.manager.game

import kotlin.math.max
import kotlin.random.Random

/**
 * In-game social network (works fully offline). Posts are generated automatically from real match events:
 * centuries, five-wicket hauls, wins, records, transfers, auction signings and titles. All accounts are fictional.
 */
object Social {
    data class Account(val name: String, val handle: String, val verified: Boolean)
    val OFFICIAL = Account("Cricket Manager Pro News", "@CMP_News", true)
    val STATS = Account("Stats Corner", "@StatsCorner", true)
    val INSIDER = Account("Dressing Room Insider", "@DR_Insider", true)
    private val FANS = listOf("cover_drive_99", "yorker_queen", "stumped_again", "spin_doctor_21", "the_third_man", "silly_point_7",
        "longon_larry", "doosra_daily", "nightwatchman", "reverse_sweep_raj", "pitch_report", "duck_or_glory", "chinaman_chintu",
        "gully_cricket_guru", "boundary_rope", "hat_trick_hero", "maiden_over_mia", "powerplay_pandit", "inswing_iyer", "stands_se_live")

    private const val MAX_POSTS = 200

    fun post(save: CareerSave, a: Account, text: String, kind: String, likes: Int, rnd: Random, comments: Int = 3): SocialPost {
        val p = SocialPost(id = save.nextId++, author = a.name, handle = a.handle, verified = a.verified, kind = kind, text = text,
            likes = max(1, likes), reposts = max(0, likes / (8 + rnd.nextInt(8))), season = save.season, day = save.day,
            tags = Regex("#(\\w+)").findAll(text).map { it.groupValues[1] }.toMutableList())
        repeat(comments) { p.comments.add(fanComment(kind, rnd)) }
        save.posts.add(0, p)
        while (save.posts.size > MAX_POSTS) save.posts.removeAt(save.posts.size - 1)
        return p
    }

    private fun fanComment(kind: String, rnd: Random): SocialComment {
        val pool = when (kind) {
            "WIN", "TITLE" -> listOf("What a performance! 🔥", "Kya team hai yaar, champions vibe!", "Coach deserves the credit for this one 👏", "Never doubted them. Not once.", "Party tonight! 🎉", "Ye toh bas shuruaat hai!", "Tactically brilliant from the coach.")
            "LOSS" -> listOf("Need changes in the XI, coach.", "Bowling at the death needs work 😤", "Bad day, we bounce back.", "Middle order ne phir disappoint kiya...", "Still believe in this squad.", "Drop the out-of-form guys please.")
            "CENTURY" -> listOf("Class is permanent 👑", "Shot after shot, pure timing!", "Ek number innings bhai!", "Pick him for the national team NOW.", "Masterclass. Frame this one.", "Hundred aur woh bhi style mein 😍")
            "FIVEFOR" -> listOf("Unplayable spell! 🎯", "Fire with the ball!", "Paanch wicket, kya baat hai!", "Batters had no clue.", "Give him the new ball every game.")
            "RECORD" -> listOf("History made! 📜", "Record books rewritten.", "Unreal numbers.", "Ye record kabhi nahi tootega!")
            "TRANSFER", "AUCTION" -> listOf("Great signing! 🙌", "Paisa vasool or overpaid? 🤔", "Welcome to the family!", "This changes everything.", "Smart business by the management.", "Hmm, not convinced yet.")
            else -> listOf("Let's go! 💪", "Interesting...", "Can't wait for the next match!", "Coach knows best.", "Full support from the stands!", "Come on boys!")
        }
        val h = FANS.random(rnd)
        return SocialComment(h.replace('_', ' ').replaceFirstChar { it.uppercase() }, "@$h", pool.random(rnd), rnd.nextInt(0, 400))
    }

    private fun fanOf(c: Club) = Account("${c.name} Army", "@${c.short.filter { it.isLetter() }}_Army", false)

    /** Auto-posts for the user's matches and standout individual performances. Also grants coins/xp/followers. */
    fun onMatch(save: CareerSave, comp: Competition, fx: Fixture, sim: MatchSim, rnd: Random = Random.Default) {
        val u = save.userClubId
        val mine = fx.home == u || fx.away == u
        val uc = save.clubs[u] ?: return
        val f = save.followers
        if (mine) {
            val opp = save.clubs[if (fx.home == u) fx.away else fx.home]
            val res = fx.res
            save.pendingXp += 10; save.pendingCoins += 5
            when {
                sim.winner == u -> {
                    save.pendingCoins += 25; save.pendingXp += 15
                    save.followers += max(50, (f * (0.004 + rnd.nextDouble() * 0.01)).toInt())
                    post(save, fanOf(uc), "VICTORY! ${uc.name} beat ${opp?.name} (${fx.stage}, ${comp.name}). ${res?.text ?: ""} #${uc.short} #${tag(comp)}", "WIN", (f * 0.02).toInt() + rnd.nextInt(500), rnd, 4)
                }
                sim.winner == null -> post(save, OFFICIAL, "${comp.name}: ${uc.name} v ${opp?.name} ends without a winner. ${res?.text ?: ""} #${tag(comp)}", "NEWS", (f * 0.006).toInt() + rnd.nextInt(200), rnd, 2)
                else -> {
                    save.followers = max(1000, save.followers - (f * 0.002).toInt())
                    post(save, INSIDER, "Tough day for ${uc.name}: beaten by ${opp?.name}. ${res?.text ?: ""} Questions for the coach after this one. #${uc.short}", "LOSS", (f * 0.008).toInt() + rnd.nextInt(300), rnd, 4)
                }
            }
        }
        // centuries & five-fors: any player in the user's match, or big hauls anywhere by user-squad players
        for (inn in sim.innings) {
            for (c in inn.bc) {
                if (!c.batted || c.runs < 100) continue
                val p = save.players[c.pid] ?: continue
                val forUser = c.pid in uc.squad
                if (!mine && !forUser) continue
                if (forUser) { save.pendingCoins += 20; save.followers += 200 + c.runs * 5 }
                val team = sim.club(inn.batTeam)
                val rec = when (comp.format) { "T20" -> c.runs >= 130; "ODI" -> c.runs >= 180; else -> c.runs >= 250 }
                post(save, if (rec) STATS else fanOf(team), "${if (rec) "RECORD-BREAKER! " else "CENTURY! "}${p.name} ${c.runs}${if (!c.out) "*" else ""} off ${c.balls} balls for ${team.name} (${comp.name}).${if (p.fictional) " [fictional player]" else ""} #${team.short} #${if (rec) "Record" else "Century"}",
                    if (rec) "RECORD" else "CENTURY", 800 + c.runs * 40 + rnd.nextInt(1500), rnd, 4)
            }
            for (b in inn.bw.values) {
                if (b.wk < 5) continue
                val p = save.players[b.pid] ?: continue
                val forUser = b.pid in uc.squad
                if (!mine && !forUser) continue
                if (forUser) { save.pendingCoins += 20; save.followers += 300 + b.wk * 100 }
                val team = sim.club(inn.bowlTeam)
                post(save, fanOf(team), "FIVE-FOR! ${p.name} takes ${b.wk}/${b.runs} for ${team.name} (${comp.name}).${if (p.fictional) " [fictional player]" else ""} #${team.short} #FiveFor",
                    if (b.wk >= 8) "RECORD" else "FIVEFOR", 900 + b.wk * 300 + rnd.nextInt(1200), rnd, 4)
            }
            if (mine) {
                val big = when (comp.format) { "T20" -> inn.runs >= 240; "ODI" -> inn.runs >= 400; else -> inn.runs >= 650 }
                if (big) post(save, STATS, "RECORD TOTAL: ${sim.club(inn.batTeam).name} pile up ${inn.runs}/${inn.wk} in the ${comp.name}! #Record", "RECORD", 3000 + rnd.nextInt(4000), rnd, 3)
            }
        }
    }

    fun onTitle(save: CareerSave, comp: Competition, rnd: Random = Random.Default) {
        val champ = comp.champion?.let { save.clubs[it] } ?: return
        if (champ.id == save.userClubId) {
            save.pendingCoins += 250; save.pendingXp += 200
            save.followers += max(5000, (save.followers * 0.08).toInt())
            post(save, OFFICIAL, "🏆 CHAMPIONS! ${champ.name} win the ${comp.name}! Coach ${save.manager} lifts the trophy. #${champ.short} #Champions #${tag(comp)}", "TITLE", (save.followers * 0.05).toInt() + 5000, rnd, 6)
        } else if (comp.userTeam == save.userClubId || comp.kind != "CLUB" || comp.division == 1) {
            post(save, OFFICIAL, "${champ.name} are the ${comp.name} champions! #${tag(comp)}", "NEWS", 1500 + rnd.nextInt(3000), rnd, 2)
        }
    }

    fun onSigning(save: CareerSave, p: GPlayer, how: String, fee: Double, rnd: Random = Random.Default) {
        val u = save.userClub()
        save.followers += 100 + max(0, p.overall() - 60) * 40
        val kind = if (how == "AUCTION") "AUCTION" else "TRANSFER"
        post(save, INSIDER, (if (kind == "AUCTION") "SOLD! ${u.name} win the bidding for ${p.name} at ${Money.fmt(fee)}." else "DONE DEAL: ${p.name} signs for ${u.name}${if (fee > 0) " (${Money.fmt(fee)})" else ""}.") +
            (if (p.fictional) " [fictional player]" else "") + " #${u.short} #${if (kind == "AUCTION") "Auction" else "Transfer"}", kind, 400 + p.overall() * 25 + rnd.nextInt(600), rnd, 3)
    }

    /** User's own post from the compose box. */
    fun userPost(save: CareerSave, text: String): SocialPost {
        val u = save.userClub()
        val p = post(save, Account("${save.manager} (Coach)", "@coach_${save.manager.filter { it.isLetterOrDigit() }.lowercase().take(12)}", false), text.take(280), "POST",
            max(5, save.followers / 200), Random.Default, 2)
        p.mine = true
        save.followers += 10
        if (p.tags.isEmpty()) p.tags.add(u.short)
        return p
    }

    fun toggleLike(p: SocialPost) { p.liked = !p.liked; p.likes += if (p.liked) 1 else -1 }

    fun trending(save: CareerSave): List<Pair<String, Int>> =
        save.posts.take(80).flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key to it.value }

    private fun tag(comp: Competition) = when (comp.key) {
        "RANJI" -> "RanjiTrophy"; "SMAT" -> "SMAT"; "VHT" -> "VijayHazare"; "DULEEP" -> "DuleepTrophy"; "IRANI" -> "IraniCup"
        "IPL" -> "IPL"; "ODI_WC" -> "CWC"; "T20_WC" -> "T20WorldCup"; "CT" -> "ChampionsTrophy"
        else -> comp.name.filter { it.isLetterOrDigit() }.take(18)
    }
}
