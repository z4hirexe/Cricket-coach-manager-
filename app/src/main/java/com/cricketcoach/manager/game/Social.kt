package com.cricketcoach.manager.game

import kotlin.math.max
import kotlin.random.Random

/**
 * Stumpstagram media / fan accounts (works fully offline); player accounts and player posts live in [Stumps]. Posts are generated automatically from real match events:
 * centuries, five-wicket hauls, wins, records, transfers, auction signings and titles. All accounts are fictional.
 */
object Social {
    data class Account(val name: String, val handle: String, val verified: Boolean)
    val OFFICIAL = Account("Cricket Manager Pro News", "@CMP_News", true)
    val STATS = Account("Stats Corner", "@StatsCorner", true)
    val INSIDER = Account("Dressing Room Insider", "@DR_Insider", true)
    private val FANS = listOf("cover_drive_99", "yorker_queen", "stumped_again", "spin_doctor_21", "the_third_man", "silly_point_7",
        "longon_larry", "doosra_daily", "nightwatchman", "reverse_sweep_raj", "pitch_report", "duck_or_glory", "chinaman_chintu",
        "gully_cricket_guru", "boundary_rope", "hat_trick_hero", "maiden_over_mia", "powerplay_pandit", "inswing_iyer", "stands_se_live",
        "deep_square_leg", "googly_gang", "free_hit_fanatic", "super_over_sid", "drs_review_wala", "chai_and_cricket", "fine_leg_fiona", "slip_cordon_club")

    private const val MAX_POSTS = 200

    /** Poster card details for a post (all optional). */
    class Poster(val pid: String = "", val club: String = "", val opp: String = "", val headline: String = "", val stat: String = "", val lines: List<String> = emptyList())

    fun post(save: CareerSave, a: Account, text: String, kind: String, likes: Int, rnd: Random, comments: Int = 3, poster: Poster? = null): SocialPost {
        val p = SocialPost(id = save.nextId++, author = a.name, handle = a.handle, verified = a.verified, kind = kind, text = text,
            likes = max(1, likes), reposts = max(0, likes / (8 + rnd.nextInt(8))), season = save.season, day = save.day,
            tags = Regex("#(\\w+)").findAll(text).map { it.groupValues[1] }.toMutableList())
        if (poster != null) {
            p.pid = poster.pid; p.club = poster.club; p.opp = poster.opp; p.headline = poster.headline; p.stat = poster.stat; p.lines.addAll(poster.lines.take(4))
        }
        val name = save.players[p.pid]?.name ?: ""
        val n = if (poster != null) comments + 2 + rnd.nextInt(3) else comments
        val used = HashSet<String>()
        repeat(n) { var c = fanComment(kind, rnd, name); var k = 0; while (!used.add(c.text) && k++ < 6) c = fanComment(kind, rnd, name); p.comments.add(c) }
        save.posts.add(0, p)
        while (save.posts.size > MAX_POSTS) save.posts.removeAt(save.posts.size - 1)
        return p
    }

    private fun fanComment(kind: String, rnd: Random, name: String = ""): SocialComment {
        val first = name.substringBefore(' ').ifBlank { name }
        val named = if (name.isBlank()) emptyList() else when (kind) {
            "CENTURY", "RECORD", "FORM" -> listOf("$name is in the form of his life! 👑🔥", "World-class consistency from $first. Unbelievable run of scores! 🏏✨",
                "Exceptional conversion rate from $first. Anchoring the side with authority. 📈", "Put $first in the national XI and throw away the key.", "$first bhai, kya timing hai! 😍")
            "FIVEFOR" -> listOf("$name was unplayable today 🎯", "$first with the ball in hand = wickets. Simple.", "Give $first the new ball every single game!", "Batters had no answer to $first. 🔥")
            "AUCTION", "TRANSFER" -> listOf("$name is a huge signing for us! 🙌", "Paisa vasool if $first stays fit 🤔", "Welcome $first! Can't wait to see you in our colours.", "$first at that price? Steal of the season.")
            "LOSS" -> listOf("Bench star $first should play ahead of the out-of-form guys. 😤", "Even $first couldn't save that one...", "Questions for the coach after this. 🤡")
            "WIN", "TITLE" -> listOf("$first was the difference today! 💪", "Player of the match vibes from $first 🏆", "$first + this coach = magic ✨")
            else -> emptyList()
        }
        if (named.isNotEmpty() && rnd.nextDouble() < 0.5) { val h = FANS.random(rnd); return SocialComment(h.replace('_', ' ').replaceFirstChar { it.uppercase() }, "@$h", named.random(rnd), rnd.nextInt(0, 600)) }
        val pool = when (kind) {
            "WIN", "TITLE" -> listOf("What a performance! 🔥", "Kya team hai yaar, champions vibe!", "Coach deserves the credit for this one 👏", "Never doubted them. Not once.", "Party tonight! 🎉", "Ye toh bas shuruaat hai!", "Tactically brilliant from the coach.")
            "LOSS" -> listOf("Need changes in the XI, coach.", "Bowling at the death needs work 😤", "Bad day, we bounce back.", "Middle order ne phir disappoint kiya...", "Still believe in this squad.", "Drop the out-of-form guys please.")
            "CENTURY" -> listOf("Class is permanent 👑", "Shot after shot, pure timing!", "Ek number innings bhai!", "Pick him for the national team NOW.", "Masterclass. Frame this one.", "Hundred aur woh bhi style mein 😍")
            "FORM" -> listOf("Purple patch! Don't change a thing 🔥", "Form is temporary, class is permanent 👑", "Keep him at the top of the order!", "Consistency king 📈")
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
        val key = "${comp.id}:${fx.id}"
        if (save.posts.any { it.matchKey == key }) return    // already processed (re-entry / reload)
        val firstNew = save.nextId
        val f = save.followers
        if (mine) {
            val opp = save.clubs[if (fx.home == u) fx.away else fx.home]
            val res = fx.res
            save.pendingXp += 10; save.pendingCoins += 5
            // the user side's standout performer + the last 4 results, for the poster card
            val star = topPerformer(sim, u)
            val form = recentResults(save, u, 4)
            val poster = { head: String -> Poster(star ?: "", u, opp?.id ?: "", head, "${res?.hScore ?: ""} v ${res?.aScore ?: ""}".trim(), form) }
            when {
                sim.winner == u -> {
                    save.pendingCoins += 25; save.pendingXp += 15
                    save.followers += max(50, (f * (0.004 + rnd.nextDouble() * 0.01)).toInt())
                    post(save, fanOf(uc), "VICTORY! ${uc.name} beat ${opp?.name} (${fx.stage}, ${comp.name}). ${res?.text ?: ""} #${uc.short} #${tag(comp)}", "WIN", (f * 0.02).toInt() + rnd.nextInt(500), rnd, 4, poster("VICTORY"))
                }
                sim.winner == null -> post(save, OFFICIAL, "${comp.name}: ${uc.name} v ${opp?.name} ends without a winner. ${res?.text ?: ""} #${tag(comp)}", "NEWS", (f * 0.006).toInt() + rnd.nextInt(200), rnd, 2)
                else -> {
                    save.followers = max(1000, save.followers - (f * 0.002).toInt())
                    post(save, INSIDER, "Tough day for ${uc.name}: beaten by ${opp?.name}. ${res?.text ?: ""} Questions for the coach after this one. #${uc.short}", "LOSS", (f * 0.008).toInt() + rnd.nextInt(300), rnd, 4, poster("DEFEAT"))
                }
            }
            // "good form" post: a user player who keeps delivering (high form after this game), now and then
            val fp = star?.let { save.players[it] }
            if (fp != null && fp.form >= 80 && form.count { it.contains("WON") } >= 2 && rnd.nextDouble() < 0.35) {
                post(save, Account("League Insider", "@league_insider", true), "LEADING THE CHARGE: ${fp.name} has been in sensational form recently, leading ${uc.name} by example. 👑🔥 #InForm #${uc.short}",
                    "FORM", 1200 + fp.form * 30 + rnd.nextInt(2000), rnd, 4, Poster(fp.id, u, "", "GOOD FORM", "FORM ${fp.form}", form))
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
                val oppT = sim.club(inn.bowlTeam)
                if (!rec && Stumps.account(save, p.id) != null) continue   // the player posts it himself (Stumps.onMatch) - no duplicate fan card
                post(save, if (rec) STATS else fanOf(team), "${if (rec) "RECORD-BREAKER! " else "CENTURY! "}${p.name} ${c.runs}${if (!c.out) "*" else ""} off ${c.balls} balls for ${team.name} (${comp.name}).${if (p.fictional) " [fictional player]" else ""} #${team.short} #${if (rec) "Record" else "Century"}",
                    if (rec) "RECORD" else "CENTURY", 800 + c.runs * 40 + rnd.nextInt(1500), rnd, 4,
                    Poster(p.id, team.id, oppT.id, if (rec) "RECORD" else "CENTURY", "${c.runs}${if (!c.out) "*" else ""}",
                        listOf("BALLS|${c.balls}", "4s / 6s|${c.fours} / ${c.sixes}", "STRIKE RATE|${"%.1f".format(if (c.balls > 0) c.runs * 100.0 / c.balls else 0.0)}", "VS|${oppT.short}")))
            }
            for (b in inn.bw.values) {
                if (b.wk < 5) continue
                val p = save.players[b.pid] ?: continue
                val forUser = b.pid in uc.squad
                if (!mine && !forUser) continue
                if (forUser) { save.pendingCoins += 20; save.followers += 300 + b.wk * 100 }
                val team = sim.club(inn.bowlTeam)
                val oppT = sim.club(inn.batTeam)
                if (b.wk < 8 && Stumps.account(save, p.id) != null) continue
                post(save, fanOf(team), "FIVE-FOR! ${p.name} takes ${b.wk}/${b.runs} for ${team.name} (${comp.name}).${if (p.fictional) " [fictional player]" else ""} #${team.short} #FiveFor",
                    if (b.wk >= 8) "RECORD" else "FIVEFOR", 900 + b.wk * 300 + rnd.nextInt(1200), rnd, 4,
                    Poster(p.id, team.id, oppT.id, "FIVE-FOR", "${b.wk}/${b.runs}",
                        listOf("OVERS|${b.oversText}", "MAIDENS|${b.maidens}", "ECONOMY|${"%.2f".format(b.econ)}", "VS|${oppT.short}")))
            }
            if (mine) {
                val big = when (comp.format) { "T20" -> inn.runs >= 240; "ODI" -> inn.runs >= 400; else -> inn.runs >= 650 }
                if (big) post(save, STATS, "RECORD TOTAL: ${sim.club(inn.batTeam).name} pile up ${inn.runs}/${inn.wk} in the ${comp.name}! #Record", "RECORD", 3000 + rnd.nextInt(4000), rnd, 3,
                    Poster(topPerformer(sim, inn.batTeam) ?: "", inn.batTeam, inn.bowlTeam, "RECORD TOTAL", "${inn.runs}/${inn.wk}", listOf("OVERS|${inn.balls / 6}.${inn.balls % 6}", "VS|${sim.club(inn.bowlTeam).short}")))
            }
        }
        Stumps.onMatch(save, comp, fx, sim, rnd)
        for (p in save.posts) { if (p.id < firstNew) break; p.matchKey = key }
    }

    fun onTitle(save: CareerSave, comp: Competition, rnd: Random = Random.Default) {
        val champ = comp.champion?.let { save.clubs[it] } ?: return
        if (champ.id == save.userClubId) {
            save.pendingCoins += 250; save.pendingXp += 200
            save.followers += max(5000, (save.followers * 0.08).toInt())
            Stumps.onTitle(save, comp, champ, rnd)
            post(save, OFFICIAL, "🏆 CHAMPIONS! ${champ.name} win the ${comp.name}! Coach ${save.manager} lifts the trophy. #${champ.short} #Champions #${tag(comp)}", "TITLE", (save.followers * 0.05).toInt() + 5000, rnd, 6,
                Poster(champ.captain, champ.id, comp.runnerUp ?: "", "CHAMPIONS", comp.name.substringBefore(" (").uppercase().take(26), recentResults(save, champ.id, 4)))
        } else if (comp.userTeam == save.userClubId || comp.kind != "CLUB" || comp.division == 1) {
            post(save, OFFICIAL, "${champ.name} are the ${comp.name} champions! #${tag(comp)}", "NEWS", 1500 + rnd.nextInt(3000), rnd, 2)
        }
    }

    fun onSigning(save: CareerSave, p: GPlayer, how: String, fee: Double, rnd: Random = Random.Default) {
        val u = save.userClub()
        save.followers += 100 + max(0, p.overall() - 60) * 40
        val kind = if (how == "AUCTION") "AUCTION" else "TRANSFER"
        post(save, INSIDER, (if (kind == "AUCTION") "SOLD! ${u.name} win the bidding for ${p.name} at ${Money.fmt(fee)}." else "DONE DEAL: ${p.name} signs for ${u.name}${if (fee > 0) " (${Money.fmt(fee)})" else ""}.") +
            (if (p.fictional) " [fictional player]" else "") + " #${u.short} #${if (kind == "AUCTION") "Auction" else "Transfer"}", kind, 400 + p.overall() * 25 + rnd.nextInt(600), rnd, 3,
            Poster(p.id, u.id, "", if (kind == "AUCTION") "SOLD!" else "SIGNED", if (fee > 0) Money.fmt(fee) else "FREE",
                listOf("ROLE|${Consts.ROLE_NAMES[p.role] ?: p.role}", "OVERALL|${p.overall()}", "AGE|${p.age(save.year)}", "FROM|${p.country}")))
        Stumps.onSigning(save, p, u.name, kind, fee, rnd)
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

    /** Best performer for [team] in this match: top run-scorer, or the best bowler if he made more impact. */
    fun topPerformer(sim: MatchSim, team: String): String? {
        val bat = sim.innings.filter { it.batTeam == team }.flatMap { it.bc }.filter { it.batted }.maxByOrNull { it.runs }
        val bowl = sim.innings.filter { it.bowlTeam == team }.flatMap { it.bw.values }.maxByOrNull { it.wk * 100 - it.runs }
        return if (bowl != null && (bat == null || bowl.wk * 25 > bat.runs)) bowl.pid else bat?.pid
    }

    /** Last [n] results of [team] (newest first) as "VS OPP|WON BY 9 WKTS" rows. */
    fun recentResults(save: CareerSave, team: String, n: Int): List<String> =
        save.league.flatMap { c -> c.fixtures.filter { it.played && it.res != null && (it.home == team || it.away == team) } }
            .sortedByDescending { it.day }.take(n).map { f ->
                val r = f.res!!; val opp = save.clubs[if (f.home == team) f.away else f.home]?.short ?: "?"
                val margin = r.text.substringAfter(" won ", "").substringBefore(" (").uppercase().replace("AN INNINGS AND", "INNS &").replace("WICKETS", "WKTS").replace("WICKET", "WKT").trim()
                val res = when { r.noResult -> "NO RESULT"; r.draw || r.winner == null -> "DRAWN"; r.winner == team -> "WON $margin".trim(); else -> "LOST" }
                "VS $opp|$res"
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
